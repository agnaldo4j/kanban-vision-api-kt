---
status: proposed
date: 2026-07-17
decision-makers: "@agnaldo4j"
---

# ADR-0038 — Extração de módulos por bounded context (domain compartilhado → domain-common + domain-kanban + domain-simulation)

> Os dois bounded contexts compartilham um único módulo `domain` (Shared Kernel). Esta ADR fixa a
> topologia-alvo — três módulos de domínio com a relação `simulation → kanban → common` — e a
> estratégia de migração faseada, mantendo o monólito modular e nomeando a independência
> grau-microserviço como direção futura.

## Context and Problem Statement

`Microservices / Modular Monolith` é a única dimensão do scorecard abaixo de 9 (8.8 —
`docs/quality/scorecard-2026-07.md`). A causa é estrutural: Kanban Management (KM) e Simulation
convivem num só módulo `domain`, que o `docs/context-map.md` classifica como Shared Kernel de todos os
módulos. Os sintomas concretos:

- **O `Scenario` (raiz de agregado do Simulation BC — `context-map.md:65`) mora no pacote KM
  `model/organization/` e carrega um `Board` concreto** (`Scenario.kt:13`). O `ContextBoundaryTest`
  precisa classificar todo o pacote `organization` como KM para esse import passar — a fronteira real
  fica escondida em vez de explícita.
- **`DomainError` e `DomainEvent` são hierarquias `sealed` com variantes de ambos os contextos** num só
  arquivo cada.
- **`Domain<ID>`, `Audit` e os cinco IDs de `Refs.kt`** são base compartilhada sem dono.
- O schema é **unificado, com FKs cross-context**.

A fronteira entre contextos é hoje apenas convenção de pacote dentro de um módulo, verificada por
Konsist. A pergunta: **como transformar essa convenção em fronteira de módulo Gradle real, por
contexto, sem inverter a regra de dependência nem reescrever o motor de simulação?**

## Decision Drivers

- Elevar a dimensão Modular Monolith: fronteiras entre contextos precisam ser de módulo, não de pacote.
- Preservar a relação `KM --Customer-Supplier--> Simulation` já declarada no context-map (dependência
  unidirecional `simulation → kanban`), sem reescrever `SimulationEngine`, que opera sobre `Board/Card/Step/Worker`.
- Nunca inverter a regra de dependência (`http_api → usecases → domain*`).
- Manter `DomainError` utilizável como contrato de erro que o `StatusPages`/mapper HTTP consome.
- Mudança de baixo risco e evolutiva (J-Curve; PRs ≤ 400 linhas; 1 gap por sessão).
- Deixar pista para uma futura extração de serviço, sem pagar o custo dela agora.

## Considered Options

1. Manter `:domain` único e apenas apertar as regras de pacote do Konsist.
2. Dois módulos — `domain-simulation → domain-kanban`, com o `domain-kanban` acumulando os tipos-base (kernel de fato).
3. **Três módulos — `domain-common` + `domain-kanban` + `domain-simulation`**, com `simulation → kanban → common`.
4. Independência grau-microserviço — Simulation deixa de compartilhar o `Board`, ganha snapshot próprio via published-language/ACL, FKs cross-context removidas, schema por contexto.

## Decision Outcome

**Escolhida: Opção 3.** Ela entrega a fronteira de módulo que o scorecard cobra, mantém intacta a
relação customer-supplier existente (nenhuma reescrita do motor) e dá um lar limpo aos tipos-base sem
que nenhum contexto vire kernel de fato. A Opção 4 é a evolução natural, mas seu custo (reescrever o
`SimulationEngine` sobre um modelo próprio, migrar o schema) não se justifica agora — fica **nomeada
como direção futura**, coerente com os seams ACL/OHS que o próprio context-map já reserva.

**Topologia-alvo** (substitui o `:domain` atual; a regra de dependência nunca inverte):

```
domain-common       ← Domain<ID>, Audit, DomainError (interface) + CommonError (grupo genérico)
   ▲     ▲
domain-kanban ───────┘  ← model/kanban/* + organization KM (Organization/Tribe/Squad/PolicySet);
   ▲                        IDs BoardId/StepId/CardId; KanbanError sealed
domain-simulation ───┘  ← model/simulation/* + simulation/* + Scenario/ScenarioRules (movidos p/ cá);
  (→ kanban, → common)      DomainEvent; IDs SimulationId/ScenarioId; SimulationError sealed
```

**Mecânica das decisões que dependem de detalhe:**

- **`DomainError`** — Kotlin não permite subtipos `sealed` cross-módulo, então `DomainError` vira uma
  **interface** em `domain-common`, com um grupo genérico `sealed interface CommonError : DomainError`
  (ValidationError, PersistenceError, ServiceUnavailable, Forbidden) no common, `sealed interface
  KanbanError : DomainError` (Board/Card/Step/OrganizationNotFound) no kanban e `sealed interface
  SimulationError : DomainError` (SimulationNotFound, InvalidDecision, DayAlreadyExecuted) no simulation.
  As variantes já usam `String` (não IDs tipados), então não há dependência de direção errada. Cada
  grupo por contexto permanece `sealed` e exaustivo; o `when` de topo perde exaustividade e o mapper HTTP
  passa a ser total com `else` **fail-closed** (500 genérico — `security.md`).
- **`DomainEvent`** — as seis variantes são 100% semântica de Simulation; move inteiro para
  `domain-simulation`, permanece `sealed` e exaustivo. Sem split.
- **IDs (`Refs.kt`)** — divididos **por contexto** (BoardId/StepId/CardId → kanban;
  SimulationId/ScenarioId → simulation). Como erros/eventos usam `String`, o `domain-common` não precisa
  de nenhum ID concreto (`Domain<ID>` é genérico) — cada contexto é dono dos seus identificadores.
- **Schema/FKs** — permanecem unificados (é monólito). Separação de schema e remoção de FKs
  cross-context pertencem à Opção 4 (futura), não a este baseline.

**Migração evolutiva** (o plano de execução vive no board #6, não aqui): (1) destrançar dentro do
módulo único — mover `Scenario`/`ScenarioRules` para pacotes de Simulation, dividir `DomainError`,
mover `DomainEvent`, dividir `Refs.kt`, isolar a base — mantendo CI verde a cada passo; (2) extrair os
três módulos Gradle e religar `usecases`/`sql_persistence`/`http_api`, aposentando o `:domain`.

### Confirmation

Fitness functions Konsist no módulo `architecture/` (ADR-0026), verdes no `testAll` a cada fase:
`ContextBoundaryTest` reclassifica `Scenario`/`ScenarioRules` para o lado Simulation e, após a extração,
passa a verificar a fronteira como dependência de módulo Gradle; `HexagonalArchitectureTest` codifica o
novo grafo `simulation → kanban → common` (e a não-inversão); `DomainPurityTest` passa a valer para os
três módulos; `PackageCycleTest` e `ContractPackageTest` seguem valendo. A não-inversão da regra de
dependência é garantida pelo escopo `implementation` do Gradle (ADR-0033 — sem JPMS).

## Consequences

- Bom: a fronteira entre contextos vira física (módulo Gradle), não convenção; cada contexto evolui e
  compila isolado; o `Scenario` deixa de estar escondido no pacote `organization`; abre pista para a
  Opção 4 (extração de serviço) sem comprometer-se com ela agora.
- Ruim: três módulos de domínio = mais cerimônia de Gradle/convention-plugin; `DomainError` perde a
  exaustividade de topo (mitigado pelo mapper total fail-closed, que o `security.md` já exige); a
  migração é multi-PR (mitigado por destrançar em pacotes antes de mover para módulos, com `testAll`
  verde a cada passo — J-Curve controlada).

## Pros and Cons of the Options

### Opção 1 — `:domain` único, só regras de pacote
- Bom: custo zero de build; nada quebra.
- Ruim: não muda a dimensão do scorecard — a fronteira continua sendo convenção, e o `Scenario`
  escondido no pacote KM permanece.

### Opção 2 — dois módulos, `kanban` como kernel
- Bom: grafo mais simples (um módulo a menos).
- Ruim: o `domain-kanban` acumula os tipos-base compartilhados e vira kernel de fato — a fronteira fica
  turva e o Simulation depende do KM até para `Domain`/`Audit`/erros genéricos.

### Opção 3 — três módulos com `domain-common` (escolhida)
- Bom: tipos-base num kernel neutro; `simulation → kanban → common` explícito; nenhum contexto sobrecarregado.
- Ruim: um módulo a mais; exige o split das hierarquias `sealed`.

### Opção 4 — independência grau-microserviço (futura)
- Bom: contextos verdadeiramente autônomos; pronto para extração de serviço.
- Ruim: reescreve o `SimulationEngine` sobre um modelo próprio, remove FKs, migra schema — custo alto,
  sem demanda atual.

## More Information

- Branch: `docs/adr-0038-context-module-extraction` · PR: https://github.com/agnaldo4j/kanban-vision-api-kt/pull/293
- Board #6: **GAP-BQ** — o plano de implementação faseada (os gaps que nascem após o aceite) vive lá, não nesta ADR.
- Referências: ADR-0021 (context map), ADR-0026 (fitness functions Konsist), ADR-0033 (limites entre
  módulos sem JPMS), ADR-0034 (IDs value-class); `docs/context-map.md`; skills
  `/microservices-modular-monolith`, `/clean-architecture`, `/evolutionary-change`.
