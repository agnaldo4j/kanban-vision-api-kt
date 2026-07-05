---
name: adr
description: >
  Crie, revise e mantenha Architecture Decision Records (ADRs) neste projeto.
  Use este skill ao propor qualquer mudança significativa de arquitetura, tecnologia,
  padrão ou design. Toda mudança relevante deve ter uma ADR aprovada antes do código
  ser escrito. A ADR garante explicitamente DOD, qualidade de código, manutenibilidade
  e aderência à arquitetura hexagonal do projeto.
argument-hint: "[ADR topic or gap description, e.g.: GAP-J pagination]"
allowed-tools: Read, Grep, Glob, Write, Edit, Bash
---

# Architecture Decision Records (ADR)

> **Princípio central**: Decisões arquiteturais são irreversíveis no curto prazo.
> Documentar antes de implementar é mais barato do que refatorar depois de descobrir
> que a decisão estava errada. Uma ADR bem escrita é o melhor artefato de onboarding
> que um time pode ter.

---

## 1. Quando Escrever uma ADR

### Obrigatório — sem ADR, sem PR

| Categoria | Exemplos concretos |
|---|---|
| Nova tecnologia ou biblioteca | Adicionar Arrow-kt, Exposed, Redis, Kafka |
| Novo padrão arquitetural | Introduzir CQRS com event sourcing, saga pattern |
| Mudança de módulo Gradle | Criar novo subprojeto, remover módulo existente |
| Quebra de API pública | Renomear endpoint, mudar payload, remover campo |
| Decisão de persistência significativa | Novo esquema de tabelas, migração de banco, estratégia de serialização |
| Novo bounded context | Adicionar `analytics/`, `notifications/`, `billing/` |
| Escolha entre alternativas com trade-offs reais | JDBC vs. Exposed, Koin vs. Dagger, REST vs. gRPC |
| Mudança na estrutura de módulos ou camadas | Mover ports, reorganizar pacotes |

### Recomendado

| Categoria | Exemplos concretos |
|---|---|
| Refatorações internas de impacto médio | Extrair serviço de domínio, consolidar value objects |
| Novas rotas em domínio já existente | `ScenarioAnalyticsRoutes`, `TenantMetricsRoutes` |
| Melhorias de observabilidade | Novo middleware de MDC, estratégia de logging estruturado |

### Não necessário

| Categoria | Exemplos concretos |
|---|---|
| Bug fix sem mudança de comportamento | Corrigir validação, fix de NPE |
| Atualização de versão patch | `ktor 3.5.1 → 3.5.2` |
| Documentação pura | Atualizar README, corrigir docstring |
| Refatoração trivial | Renomear variável local, extrair constante |

---

## 2. Convenção de Nomenclatura

```
adr/ADR-NNNN-slug-descritivo.md
```

- `NNNN` — número sequencial com zero-padding (0001, 0002, …)
- `slug-descritivo` — kebab-case, 3–6 palavras que identificam a decisão
- Nunca reutilize um número, mesmo se a ADR for rejeitada

### Rastreabilidade ADR ↔ Branch ↔ Commit ↔ PR

| Artefato | Convenção | Exemplo |
|---|---|---|
| Arquivo ADR | `adr/ADR-NNNN-slug.md` | `adr/ADR-0002-scenario-analytics-routes.md` |
| Branch | `feat/adr-NNNN-slug` | `feat/adr-0002-scenario-analytics-routes` |
| Commit inicial | `docs(adr): ADR-NNNN — título curto` | `docs(adr): ADR-0002 — adicionar rotas de analytics de cenário` |
| PR title | `feat(adr-NNNN): título curto` | `feat(adr-0002): scenario analytics routes` |
| Campo PR na ADR | URL do PR no GitHub | `https://github.com/org/repo/pull/52` |

---

## 3. Template MADR 4.0 (obrigatório para ADRs novas — ADR-0023)

Copie este template ao criar uma nova ADR. Formato: [MADR 4.0](https://adr.github.io/madr/).
Todos os campos marcados com `*` são obrigatórios.

```markdown
---
status: proposed            # proposed | accepted | rejected | deprecated | superseded by ADR-XXXX
date: YYYY-MM-DD
decision-makers: "@handle"
# supersedes: ADR-XXXX      # somente se substituir outra ADR (adicionar a linha de status na antiga)
---

# ADR-NNNN — Título Descritivo da Decisão *

## Context and Problem Statement *

> O problema ou necessidade que exige uma decisão explícita agora: qual parte do sistema é
> afetada, quais requisitos/restrições existem. Termine com a pergunta a ser decidida.

## Decision Drivers

- Força 1 — ex.: manter o domínio livre de dependências de framework
- Força 2 — ex.: não quebrar a API v1 (additive-only)

## Considered Options *

1. Opção A — descrição em uma frase
2. Opção B — descrição em uma frase

## Decision Outcome *

**Escolhida: Opção X**, porque [justificativa objetiva ancorada nos drivers, 2–4 frases].

### Confirmation

> Como a conformidade com esta decisão será verificada — de preferência um gate/fitness
> function de CI (Detekt rule, teste Konsist, quality gate, assertion na spec). Se não for
> automatizável, descreva a verificação em code review.

## Consequences *

- Bom: consequência positiva 1
- Ruim: trade-off 1 (e como será mitigado)

## Pros and Cons of the Options

### Opção A
- Bom: …
- Ruim: …

### Opção B
- Bom: …
- Ruim: …

## More Information

- Branch: `feat/adr-NNNN-slug` · PR: (link após abrir)
- Item no board #6: (link) — o plano de implementação vive lá, não aqui
- Referências: skills, ADRs relacionadas, literatura
```

**O que NÃO vai na ADR (ADR-0023):** plano de implementação com checkboxes, checklists de
DoD/SOLID/arquitetura, scores e status de progresso. O plano vive no item do board #6 e no PR;
a qualidade é verificada pelos gates do CI e pelos skills `definition-of-done` /
`kotlin-quality-pipeline` no PR de implementação — a seção *Confirmation* apenas aponta o gate.

---

## 4. Workflow ADR-First

### Fluxo em 8 passos

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  1. Proposta │────▶│  2. Escrita  │────▶│  3. Revisão  │────▶│  4. Aprovação│
│  (issue ou  │     │  (branch     │     │  (PR draft, │     │  (Status:   │
│   discussão)│     │   feat/adr-) │     │   feedback) │     │   Aceita)   │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
                                                                      │
                                                                      ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  8. Merge   │◀────│ 7. Verif.   │◀────│  6. PR      │◀────│ 5. Impl.    │
│  (main)     │     │    DOD      │     │  (review,   │     │  (código na │
│             │     │             │     │   CI verde) │     │   branch)   │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
```

### Ciclo de vida do Status

| Status (canônico MADR) | Significado | Quem define |
|---|---|---|
| **proposed** | ADR escrita, aguardando revisão | Autor |
| **accepted** | Decisão aprovada, implementação autorizada | Revisor após aprovação |
| **superseded by ADR-XXXX** | Substituída por ADR mais recente (com link) | Autor da nova ADR |
| **rejected** | Avaliada e descartada (registrar motivo) | Revisor |
| **deprecated** | Era válida, mas o contexto mudou (sem substituto imediato) | Time |

> ADRs legadas (≤ 0022) mantêm os termos em português (`Aceita`, `Supersedida...`) — não converter.
> Para automação/grep, os termos canônicos acima valem para toda ADR nova.

**Regra**: o status da ADR deve ser atualizado no mesmo PR que concluir a implementação.
Uma ADR com status `Proposta` após o merge indica processo incompleto.

**Imutabilidade (ADR-0023)**: uma ADR `accepted` nunca tem seu conteúdo editado. Mudança de
decisão = nova ADR que a supersede; a antiga recebe apenas a linha de status
`superseded by ADR-XXXX`. Progresso, backlog, scores e ordem de execução NUNCA vivem em
ADRs — planejamento fica no board #6 e medição em `docs/quality/` + CI. ADRs novas usam o
template **MADR 4.0** (front-matter `status/date/decision-makers`; Context and Problem
Statement; Decision Drivers; Considered Options; Decision Outcome; **Confirmation** — o
gate/fitness function que verifica a decisão; Consequences). Ver
`adr/ADR-0023-politica-adrs-imutabilidade-madr.md`.

---

## 5. Como Ligar ADR ao PR

### Commit inicial da ADR

```
docs(adr): ADR-NNNN — [título curto da decisão]

Documenta a decisão de [frase resumo].
Contexto: [1-2 frases sobre o problema].
Opções avaliadas: A (escolhida), B, C.
```

### Corpo do PR

```markdown
## ADR

Decisão documentada em: `adr/ADR-NNNN-slug.md`

## Contexto

[1-3 frases sobre o problema que motivou esta ADR]

## O que muda

- [Mudança 1]
- [Mudança 2]

## Checklist ADR

- [ ] ADR com status `Aceita` antes do primeiro commit de código
- [ ] Plano de implementação completo na ADR
- [ ] Garantias de Qualidade preenchidas na ADR
- [ ] `./gradlew testAll` verde
- [ ] Campo `PR` na ADR preenchido com a URL deste PR

## Test plan

- [ ] Testes unitários: [descreva os cenários]
- [ ] Testes de integração: [descreva os boundaries testados]
- [ ] CI pipeline verde

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

### Instrução final

Após abrir o PR, volte ao arquivo `adr/ADR-NNNN-slug.md` e preencha o campo `PR`
na tabela de cabeçalho com a URL completa do PR. Isso fecha o ciclo de rastreabilidade.

---

## 6. Exemplos: ADR Boa vs. Ruim

O exemplo usa `ScenarioAnalyticsRoutes` — um arquivo real deste projeto.

### Contexto ruim

```markdown
## Contexto

Precisamos adicionar analytics para cenários.
```

**Por que é ruim**: não explica o problema, não menciona restrições, não justifica
por que uma decisão explícita é necessária agora.

### Contexto bom

```markdown
## Contexto e Motivação

O módulo `http_api` expõe rotas de CRUD para cenários (`ScenarioRoutes`), mas não
há endpoints que retornem métricas agregadas de simulação — throughput diário,
distribuição de lead time, WIP médio por coluna.

Clientes da API precisam dessas métricas para construir dashboards. A lógica de
agregação envolve múltiplos dias de `DailySnapshot` e potencialmente queries pesadas.
Precisamos decidir se a agregação ocorre na camada de aplicação (usecase) ou diretamente
em SQL, pois essa escolha impacta testabilidade, performance e aderência à Dependency Rule.

Esta ADR é necessária agora porque existe PR em progresso que adiciona SQL de agregação
diretamente na rota, violando a separação de responsabilidades.
```

**Por que é boa**: descreve o sistema atual, o problema real, o impacto da decisão
e por que ela é urgente.

---

### Análise de opções ruim

```markdown
## Opções

- Opção A: usar usecase
- Opção B: usar SQL direto

Escolhemos A porque é melhor.
```

**Por que é ruim**: sem prós/contras, sem critérios, justificativa vazia.

### Análise de opções boa

```markdown
## Opções Consideradas

- **Opção A**: Agregação no `GetScenarioAnalyticsUseCase` — lógica em Kotlin sobre objetos de domínio
- **Opção B**: Agregação em SQL — query complexa em `JdbcScenarioAnalyticsRepository`
- **Opção C**: View materializada no PostgreSQL — pré-computada por trigger

### Opção A — Agregação no UseCase

**Prós:**
- Lógica testável com MockK puro, sem banco real
- Mantém `domain/` e `usecases/` sem SQL
- Fácil de entender e modificar

**Contras:**
- Carrega todos os `DailySnapshot` em memória para processar
- Performance degradada para cenários com muitos dias (> 365)

### Opção B — Agregação em SQL

**Prós:**
- Performance superior para grandes volumes de dados
- Banco faz o que faz bem (agregações)

**Contras:**
- Lógica de negócio em SQL → difícil de testar sem banco real
- Viola a intenção da Dependency Rule: usecase passa a depender de SQL implicitamente
- Mudança de banco exige reescrever a lógica de agregação

### Opção C — View Materializada

**Prós:**
- Consulta O(1) após atualização

**Contras:**
- Complexidade operacional: trigger de atualização, invalidação de cache
- Over-engineering para o volume atual de dados
- Não justificado pelas métricas de uso presentes

## Decisão

**Escolhemos Opção A** (Agregação no UseCase) porque mantém a Dependency Rule intacta,
garante testabilidade sem banco e o volume atual de dados (simulações de até 365 dias)
não justifica a complexidade das alternativas. Se o volume crescer, uma ADR de migração
para Opção B será emitida com benchmark comparativo.
```

---

## 7. Antipadrões

| Antipadrão | Problema | Correção |
|---|---|---|
| **ADR post-fato** | Código já está em produção quando a ADR é escrita | ADR-first: nenhum código antes da ADR aprovada |
| **ADR como changelog** | Descreve o que foi feito, não por que foi decidido | Foco no problema e nas alternativas rejeitadas |
| **Uma opção avaliada** | Sem alternativas, não é uma decisão — é uma especificação | Mínimo 2 opções com prós/contras reais |
| **Justificativa vaga** | "Escolhemos X porque é melhor/mais simples/recomendado" | Justificativa baseada em forças concretas e trade-offs |
| **Plano dentro da ADR** | Checkboxes de implementação embutidos tornam a ADR mutável | Plano vive no item do board #6 e no PR; ADR aponta o gate na seção *Confirmation* |
| **Confirmation vazia** | Decisão sem forma de verificar conformidade | Apontar gate/fitness function de CI, ou descrever a verificação de review |
| **Status nunca atualizado** | ADR permanece como `proposed` após o merge | Atualizar status para `accepted` no PR de implementação |
| **Sem número sequencial** | `adr/analytics.md`, `adr/nova-rota.md` | Sempre `ADR-NNNN-slug.md` — número é imutável |
| **ADR supersedida sem referência** | ADR antiga não menciona a nova | Linha `superseded by ADR-NNNN` na antiga; `supersedes: ADR-NNNN` no front-matter da nova |
| **Múltiplas decisões em uma ADR** | "ADR sobre analytics E autenticação E cache" | Uma decisão por ADR — se estão acopladas, documente explicitamente |
| **ADR como backlog/status report** | Checkboxes de progresso, scores, ordem de execução editados a cada entrega (caso ADR-0004) | Planejamento no board #6; medição em `docs/quality/` + CI; ADR imutável referencia por link |
| **ADR aceita editada** | Conteúdo alterado após aceite — histórico do porquê se perde | Nova ADR que supersede; a antiga só ganha a linha de status |

---

## 8. Checklist Rápido Pré-PR

Use antes de marcar o PR como pronto para revisão.

### ADR

- [ ] Arquivo nomeado corretamente: `adr/ADR-NNNN-slug-descritivo.md`
- [ ] Status atualizado para `Aceita`
- [ ] Mínimo 2 opções com prós/contras completos
- [ ] Justificativa da decisão baseada nas forças declaradas
- [ ] Plano de implementação com todas as tarefas marcadas como concluídas

### Rastreabilidade

- [ ] Campo `Branch` preenchido na ADR
- [ ] Campo `PR` preenchido com URL após abertura do PR
- [ ] Commit inicial da ADR segue a convenção `docs(adr): ADR-NNNN — título`
- [ ] Se supersede outra ADR, ambas referenciam-se mutuamente

### Garantias

- [ ] `./gradlew testAll` verde (Detekt + KtLint + testes + JaCoCo ≥ 97%)
- [ ] Todos os itens do DOD confirmados ou marcados N/A com justificativa
- [ ] Dependency Rule verificada: nenhum import de framework em `domain/` ou `usecases/`
- [ ] Diagramas C4 atualizados se um novo módulo, rota ou caso de uso foi adicionado