# Context Map

> Referências: *Kanban from the Inside* (Burrows) · *The Principles of Product Development Flow* (Reinertsen) · [ADR-0004](../adr/ADR-0004-avaliacao-qualidade-gaps-priorizados.md)

---

## Visão Geral

```
┌──────────────────────────────────────────────────────────────────────┐
│                          SHARED KERNEL                               │
│  domain/ — Board, Step, Card, Organization, ServiceClass,            │
│            DomainError, Audit, *Ref, SimulationDay                   │
└───────────┬────────────────────────────┬─────────────────────────────┘
            │                            │
            ▼                            ▼
┌───────────────────────┐    ┌───────────────────────────┐
│   Kanban Management   │    │     Simulation Engine      │
│                       │◄───│                           │
│  Board, Step, Card    │    │  Scenario, SimulationEngine│
│  Organization, Tribe  │    │  Decision, PolicySet       │
│  Squad, Worker        │    │  DailySnapshot             │
└───────────────────────┘    └─────────────┬─────────────┘
                                           │
                                     ACL (planejado)
                                           │
                                           ▼
                             ┌─────────────────────────┐
                             │        Analytics         │
                             │                         │
                             │  FlowMetrics, CFD        │
                             │  GET /simulations        │
                             │  GET /simulations/{id}/  │
                             │    days · cfd            │
                             └─────────────┬───────────┘
                                           │
                              Customer-Supplier (futuro)
                                           │
                                           ▼
                             ┌─────────────────────────┐
                             │       Forecasting        │
                             │    (candidato futuro)    │
                             │  Lead Time Distribution  │
                             │  Throughput Prediction   │
                             │  Monte Carlo Simulation  │
                             └─────────────────────────┘

┌──────────────────────────────────────────────────────┐
│             Policy Engine (candidato futuro)         │
│    Open Host Service → consumido por Simulation      │
│  Escalation, classes de serviço, automação de        │
│  decisões, políticas explícitas (Burrows §4)         │
└──────────────────────────────────────────────────────┘
```

---

## Bounded Contexts Estabelecidos

### 1. Kanban Management

**Pacote:** `domain/src/main/kotlin/com/kanbanvision/domain/model/`

**Aggregate Roots:** `Board`, `Organization`

| Entidade/VO | Tipo | Responsabilidade |
|---|---|---|
| `Board` | Aggregate Root | Estrutura do board; garante nomes de steps únicos |
| `Step` | Entity | Posição no fluxo; workers com ability obrigatória |
| `Card` | Entity | Unidade de trabalho; state machine (TODO→IN_PROGRESS→DONE) |
| `Organization` | Aggregate Root | Hierarquia de times (Tribe → Squad → Worker) |
| `Worker` | Entity | Capacidade diária por ability (determinística com seed) |
| `ServiceClass` | Enum | STANDARD · EXPEDITE · FIXED_DATE · INTANGIBLE |
| `AbilityName` | Enum | PRODUCT_MANAGER · DEVELOPER · TESTER · DEPLOYER |

**Invariantes de domínio:**
- `Board.addStep()` — nome único por board
- `Step.assignWorker()` — worker deve ter a ability requerida pelo step
- `Worker` TESTER implica DEPLOYER

**Linguagem Ubíqua:** Board, Step, Card, Service Class, WIP, Aging, Effort, Ability, Seniority

---

### 2. Simulation Engine

**Pacotes:** `domain/src/main/kotlin/com/kanbanvision/domain/simulation/` + `domain/model/Simulation.kt`, `Scenario.kt`, `ScenarioRules.kt`

**Aggregate Roots:** `Simulation`, `Scenario`

| Entidade/VO | Tipo | Responsabilidade |
|---|---|---|
| `Simulation` | Aggregate Root | Ciclo de vida da simulação (DRAFT→RUNNING→PAUSED→FINISHED) |
| `Scenario` | Aggregate Root | Estado imutável do board + regras + histórico de execução |
| `ScenarioRules` | Value Object | WIP limit, team size, seed determinístico |
| `SimulationEngine` | Domain Service | Execução pura e determinística de um dia |
| `Decision` | Entity | Comando aplicado a um dia (MOVE, BLOCK, UNBLOCK, ADD) |
| `DailySnapshot` | Entity | Estado capturado ao final de cada dia executado |
| `FlowMetrics` | Value Object | throughput, wipCount, blockedCount, avgAgingDays |
| `Movement` | Entity | Rastreamento de cada movimentação de card no dia |

**Linguagem Ubíqua:** Simulation Day, Scenario, Decision, Snapshot, WIP Limit, Seed, Throughput, Lead Time

---

### 3. Analytics

**Pacotes:** Usecases — `usecases/simulation/GetSimulationDays*`, `GetSimulationCfd*`, `ListSimulations*`

**Rotas HTTP:**

| Endpoint | Descrição |
|---|---|
| `GET /simulations` | Lista paginada de simulações (ordenada por id ASC) |
| `GET /simulations/{id}/days` | Série temporal de snapshots diários |
| `GET /simulations/{id}/cfd` | Dados de Cumulative Flow Diagram |

| DTO | Campos |
|---|---|
| `SimulationSummaryResponse` | id, organizationId, wipLimit, teamSize, seedValue, status, currentDay |
| `SimulationDayResponse` | simulationId, day, throughput, wipCount, blockedCount, avgAgingDays |
| `CfdDataPoint` | day, throughputCumulative, wipCount, blockedCount |

**Linguagem Ubíqua:** CFD, Time-Series, Throughput Cumulative, Pagination, Day Series

---

## Padrões de Integração

| Relação | Padrão DDD | Estado | Descrição |
|---|---|---|---|
| `domain/` → todos os módulos | **Shared Kernel** | Atual | Entidades, VOs e `DomainError` compartilhados — mudanças requerem coordenação entre módulos |
| `http_api` → `usecases` | **Customer-Supplier** | Atual | `http_api` (customer) consome use cases (supplier) via interfaces CQS — supplier define o contrato |
| `sql_persistence` → `domain` | **Conformist** | Atual | Persistence aceita o modelo de domínio sem tradução — tabelas Exposed espelham entidades |
| `Analytics` → `Simulation` | **ACL** | Planejado | Analytics deve consumir `DailySnapshot` via Anti-Corruption Layer para isolar seu modelo de leitura do modelo de execução |
| `Simulation` → `Policy Engine` | **Open Host Service** | Futuro | Policy Engine expõe protocolo estável para que Simulation resolva decisões automaticamente |
| `Forecasting` → `Analytics` | **Customer-Supplier** | Futuro | Forecasting consome dados agregados de Analytics via contrato versionado |

---

## Contextos Candidatos à Extração

### Forecasting

**Motivação:** *The Principles of Product Development Flow* (Reinertsen) — análise quantitativa de fluxo gera insights sobre lead time e capacidade preditiva que vão além da visualização histórica.

**Responsabilidade futura:**
- Distribuição de lead time (percentis P50/P85/P95)
- Previsão de throughput por período
- Monte Carlo simulation para estimativas probabilísticas de entrega

**Relação esperada:** Customer-Supplier downstream de Analytics (consome `CfdDataPoint` e `SimulationDayResponse` sem acoplar ao modelo de execução)

---

### Policy Engine

**Motivação:** *Kanban from the Inside* (Burrows) — políticas explícitas são o 4º dos 6 valores do Kanban; atualmente as políticas (WIP limit, prioridade por `ServiceClass`, regras de escalation) estão embutidas no `SimulationEngine`.

**Responsabilidade futura:**
- Regras de escalation por `ServiceClass`
- Automação de decisões (MOVE/BLOCK) baseada em políticas configuráveis
- Configuração de limites e critérios por step

**Relação esperada:** Open Host Service — expõe protocolo estável para que `SimulationEngine` delegue decisões automatizadas sem acoplar ao motor de regras

---

## Referências Teóricas

| Obra | Autor | Aplicação neste projeto |
|---|---|---|
| *Kanban from the Inside* | Mike Burrows | 9 valores, 6 práticas e 4 classes de serviço como lente de design dos BCs |
| *The Principles of Product Development Flow* | Donald Reinertsen | Métricas de fluxo (throughput, WIP, lead time) e base do Analytics BC |
| *Domain-Driven Design* | Eric Evans | Padrões de Context Map (Shared Kernel, ACL, Customer-Supplier, Open Host Service) |
| *Implementing DDD* | Vaughn Vernon | Guidance de Bounded Context e integration patterns |
