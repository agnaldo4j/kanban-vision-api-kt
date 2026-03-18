---
name: evolutionary-change
description: Planeje e execute mudanças evolutivas no projeto sem causar crises estruturais, degradação de qualidade ou desperdício de contexto de LLMs. Use este skill ao decidir como abordar gaps técnicos, refatorações, upgrades de dependências, mudanças de arquitetura ou qualquer intervenção no sistema que carregue risco.
argument-hint: "[gap or change description, e.g.: GAP-J pagination]"
allowed-tools: Read, Grep, Glob
---

# Evolutionary Change

## 1. Por que mudança evolutiva?

A premissa central: **humanos (e sistemas) resistem a mudanças estruturais dramáticas**, não a mudanças normativas incrementais.

| Tipo de mudança | Definição | Impacto no sistema | Resistência |
|---|---|---|---|
| **Normativa** | Muda *como* algo é feito (ferramenta, método, prática) sem afetar relações, status ou identidade | Baixo — o sistema ainda "faz a mesma coisa" | Mínima |
| **Estrutural** | Muda *quem faz o quê*, relações, hierarquias, identidades | Alto — quebra contratos implícitos | Alta — gera crise psicológica |

> "People do not resist change, they resist **being** changed." — Peter Senge, The Fifth Discipline

No contexto de software, "resistência" se manifesta como:
- Testes quebrando em cascata após refatoração massiva
- Build time aumentando por migração grande de dependências
- Regressões silenciosas que só aparecem em produção
- PRs enormes que ninguém consegue revisar com atenção
- LLMs perdendo contexto e gerando código inconsistente em sessões longas

---

## 2. Os 4 estados de mudança social (mapeados para software)

Adaptado de Roxane de la Sablonnière, *Frontiers in Social Psychology* (2017):

| Estado | Definição original | Equivalente no projeto |
|---|---|---|
| **Stability** | Condições dentro de limites conhecidos; o sistema está adaptado | Pipeline verde, cobertura 95%, zero violações Detekt — sistema saudável |
| **Inertia** | Equilíbrio aparente escondendo um problema crescente | Dívida técnica acumulando silenciosamente; ex.: `SimulationEngine` como `object` sem interface — funciona, mas bloqueia evolução |
| **Incremental** | Pequenas mudanças normativas que evoluem o sistema sem crise | PRs focados de 1-3 arquivos; um gap por vez; refatoração Strangler Fig |
| **Dramatic** | Mudança estrutural que invoca crise | Reescritas completas; migração de framework em um único PR; reorganização de todos os pacotes de uma vez |

**Diagnóstico de inertia no código**: quando um módulo parece estável mas impede mudanças em outros módulos, é candidato a refatoração evolutiva — não a reescrita.

---

## 3. O Efeito J-Curve

Toda mudança passa por uma queda de capacidade antes de atingir o novo patamar. Este é o **J-Curve**:

```
Capacidade
    │
  N │.......                    ← patamar desejado
  o │      ↑ subida (meses)
  v │
  o │
  P │ atual ─────┐              ← patamar atual
  a │            │ queda inicial
  t │            └──────.....   ← fundo da curva
  a │
    └────────────────────────── Tempo
                 ↑        ↑
              Safety   Patience
```

- **Safety**: quão fundo pode cair antes de alguém ser punido (build quebra? cobertura cai abaixo de 95%? CI vermelho?)
- **Patience**: quanto tempo até a liderança cancelar a iniciativa

Para **LLMs**, o J-curve existe em contexto: uma refatoração grande esgota o contexto da sessão, causando inconsistências no código gerado. A solução é a mesma: muitos J-curves pequenos em vez de um grande.

### Tolerância executiva para este projeto

| Medida | Limite de Safety | Notas |
|---|---|---|
| Cobertura JaCoCo | ≥ 95% (gate automático) | CI bloqueia merge se cair |
| Detekt violations | 0 com `warningsAsErrors=true` | CI bloqueia merge |
| Build verde | Obrigatório em todo PR | `./gradlew testAll` |
| PR size | ≤ 400 linhas alteradas por PR | Heurística — acima disso revisar atentamente |
| Contexto LLM por sessão | 1 gap por sessão | Evita inconsistências cross-gap |

---

## 4. Mudança normativa vs estrutural no código

### Normativa (preferida) — não invoca crise
```
✓ Extrair interface de uma classe concreta
✓ Mover método para classe mais coesa
✓ Renomear variável/função para expressar melhor a intenção
✓ Adicionar teste unitário a código existente
✓ Atualizar uma dependência por vez
✓ Adicionar um campo opcional em DTO
✓ Extrair constante mágica para named constant
✓ Adicionar endpoint novo sem alterar os existentes
✓ Adicionar coluna nullable em migração Flyway
```

### Estrutural (requer planejamento de ADR) — invoca crise
```
✗ Reorganizar todos os pacotes de uma vez
✗ Trocar framework HTTP (Ktor → Spring Boot)
✗ Trocar ORM ou estratégia de persistência completamente
✗ Mudar modelo de erro (Either → exceções)
✗ Remover campo existente em API pública
✗ Alterar contrato de repositório usado por múltiplos use cases
✗ Converter módulo Gradle em múltiplos módulos de uma vez
```

**Regra prática**: se o PR tocará mais de 3 arquivos de camadas diferentes simultaneamente, avalie se pode ser dividido.

---

## 5. Estratégia de execução para os gaps do ADR-0004

Os 20 gaps do ADR-0004 são classificados por impacto evolutivo. Esta classificação é
a fonte canônica — a coluna `Tipo` das tabelas do ADR-0004 é derivada daqui.

### Normativas `[N]` — execute diretamente, 1 por sessão LLM
```
GAP-B  — Health check:         adiciona endpoints, não altera existentes
GAP-C  — Graceful shutdown:    adiciona shutdown hook em Main.kt
GAP-F  — Logs JSON:            adiciona appender condicional, não remove texto
GAP-W  — Governança de gaps:   enriquece ADR + cria docs/, zero código alterado
GAP-P  — SimulationEngine iface: extrai interface, substitui referências
GAP-Q  — Log PersistenceError: adiciona log.error() antes do map
GAP-N  — OpenAPI exemplos:     enriquece specs, não altera comportamento
GAP-T  — Context Map:          novo arquivo docs/, zero código alterado
```

### Médio impacto `[M]` — 1 sessão de design + 1 PR focado por gap
```
GAP-D  — Métricas:       novo plugin + DomainMetrics (não altera rotas)
GAP-E  — Rate Limiting:  middleware adicionado antes do routing
GAP-G  — Containerização: novos arquivos (Dockerfile, k8s/), zero alteração de código
GAP-U  — Alertas:        novos arquivos Grafana/Prometheus, depende de GAP-D
GAP-S  — Boundary Gradle: regra Detekt custom, sem alterar código de produção
GAP-I  — Board aggregate: mover validações dos use cases para Board.kt
GAP-J  — Paginação:      nova interface Query + novos parâmetros opcionais
GAP-L  — PITest:         novo Gradle plugin, sem alterar código de produção
```

### Estruturais `[E]` — requerem ADR dedicada aprovada antes de qualquer código
```
GAP-A  → ADR-0005: Autenticação — altera todas as rotas + adiciona camada de segurança
GAP-V  → ADR-0006: CI/CD pipeline — modifica ci.yml + secrets de infra compartilhada
GAP-O  → ADR-0007: OTel Agent — depende de GAP-G; configura agente externo no JVM
GAP-H  → ADR-0008: Domain Events — novo conceito transversal no domínio; depende GAP-P
GAP-K  → ADR-0009: Contract tests — nova estratégia + broker Pact; depende GAP-G
GAP-R  → ADR-0010: API Build Module — divisão de módulo Gradle; só se build time > 2min
GAP-M  → ADR-0011: Schema boundaries — migração Flyway + mudança de queries existentes
```

---

## 6. Protocolo de execução evolutiva por sessão LLM

A principal causa de degradação de qualidade em sessões longas de LLM é o **esgotamento de contexto**, que leva a:
- Código inconsistente com padrões já estabelecidos
- Violações Detekt que o LLM não percebe mais
- Testes esquecidos ou escritos incorretamente
- Regressões em código não modificado

### Protocolo: 1 gap por sessão

```
1. ANTES de iniciar: leia CLAUDE.md + ADR-0004 + os 2-3 arquivos alvo
2. EXECUTE apenas o gap planejado (normativo de preferência)
3. VALIDE localmente: ./gradlew testAll
4. CRIE PR focado (título: "feat(gap-X): descrição do gap")
5. ENCERRE a sessão após o PR estar pronto para revisão
6. PRÓXIMA sessão: recarregue o contexto — não assuma memória entre sessões
```

### Sinais de que o contexto da sessão está esgotado

- O LLM começa a criar helpers desnecessários
- O LLM esquece padrões estabelecidos (ex.: usa `Either` diferente do padrão do projeto)
- O PR está tocando mais de 5 arquivos para um único gap
- O LLM sugere "enquanto estamos aqui, vamos também refatorar X"

**Ação**: encerre a sessão. Abra nova sessão com contexto limpo.

---

## 7. Anti-padrões de mudança dramática (evitar)

### Big Bang Refactor
```kotlin
// EVITAR: PR com 800 linhas alterando toda a camada de persistência
// de uma vez porque "enquanto estamos aqui"
```
→ Use Strangler Fig: extrai uma entidade por vez (`Board`, depois `Card`, etc.)

### Upgrade em lote
```
// EVITAR: bump de Ktor 3.1.2 → 4.x + Koin 4.1.1 → 5.x + Kotlin 2.x no mesmo PR
```
→ Uma dependência por PR; rode `./gradlew testAll` após cada uma.

### Gap stacking
```
// EVITAR: "GAP-F + GAP-D + GAP-B numa mesma sessão porque são pequenos"
```
→ Cada gap tem suas próprias dependências e efeitos colaterais. Sessões compostas degradam qualidade e esgotam contexto.

### Designed process (proibido)
```
// EVITAR: "vamos redesenhar toda a estrutura de pacotes para seguir
// a convenção X que li no blog Y"
```
→ O sistema já tem uma estrutura que funciona. Evolua a partir do que existe.

### Reverse J-curve
```
// EVITAR: implementar GAP-O (OTel traces) antes do GAP-G (Docker),
// forçando configuração manual sem container
```
→ Respeite a ordem de dependências: `GAP-G → GAP-O`, `GAP-D → GAP-U`, `GAP-P → GAP-H`.

---

## 8. Evolutionary Relics no código

Artefatos evolutivos deixados para trás que já não servem propósito mas também não causam dano. Não remova sem investigar:

| Síntoma | Investigação antes de remover |
|---|---|
| Método `unused` sinalizdo pelo IDE | Pode ser chamado via reflection (Ktor serialization, Koin) |
| `@Suppress` sem explicação | Pode ser supressão válida de falso positivo Detekt |
| Coluna de DB sem uso aparente | Verifique Flyway history — pode ser usada em query complexa |
| Classe `sealed` com um único subtype | Pode estar aguardando novos cases de domínio |
| `TODO` comment antigo | Pode ser inertia disfarçada — registre como gap ou remova conscientemente |

---

## 9. Checklist de mudança evolutiva

Antes de iniciar qualquer mudança, responda:

```
[ ] Esta mudança é normativa ou estrutural?
    → Se estrutural: existe ADR aprovada para ela?

[ ] Qual é o J-curve esperado?
    → Safety: o CI permanece verde durante a transição?
    → Patience: o PR será revisado em < 48h?

[ ] O escopo está delimitado a 1 gap?
    → Se tocar mais de 3 arquivos de camadas distintas: dividir em PRs menores

[ ] A ordem de dependências foi respeitada?
    → Ver Ciclos de execução do ADR-0004

[ ] O contexto da sessão LLM está fresco?
    → CLAUDE.md e arquivos alvo foram relidos no início da sessão?

[ ] Os testes cobrem o comportamento alterado?
    → ./gradlew testAll verde antes de abrir o PR

[ ] O PR title referencia o gap?
    → "feat(gap-B): add /health/live and /health/ready endpoints"
```

---

## 10. Referências

- Anderson, David. *Kanban: Successful Evolutionary Change for Your Technology Business*
- de la Sablonnière, Roxane. "Toward a Psychology of Social Change: A Typology of Social Change." *Frontiers in Social Psychology*, 2017. https://doi.org/10.3389/fpsyg.2017.00397
- Steele, C.M., Spencer, S.J., Aaronson, J. "Contending with Group Image: The psychology of stereotype and social identity threat." *Advances in Experimental Social Psychology*, 2002.
- Senge, Peter. *The Fifth Discipline: The Art & Practice of the Learning Organization*
- Taleb, Nassim Nicholas. *Antifragile: Things That Gain from Disorder*
- Fowler, Martin. *Strangler Fig Application*. https://martinfowler.com/bliki/StranglerFigApplication.html
- PMI. *Organizational Change Management*. 2014. https://www.pmi.org/learning/thought-leadership/pulse/organizational-change-management
- Skill: [adr](.claude/skills/adr/SKILL.md)
- Skill: [refactoring](.claude/skills/refactoring/SKILL.md)
- Skill: [definition-of-done](.claude/skills/definition-of-done/SKILL.md)
- ADR-0004 — Avaliação de Qualidade: Gaps e Prioridades