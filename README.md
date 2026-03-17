[![CI](https://github.com/agnaldo4j/kanban-vision-api-kt/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/agnaldo4j/kanban-vision-api-kt/actions/workflows/ci.yml)

# Kanban Vision API

> Simulador de fluxo Kanban multi-organização via API REST — construído em Kotlin com foco em arquitetura limpa, qualidade de código e boas práticas de engenharia de software.

---

## Quick Start

### Com Docker Compose (recomendado)

```bash
# 1. Clone o repositório
git clone https://github.com/agnaldo4j/kanban-vision-api-kt.git
cd kanban-vision-api-kt

# 2. Suba o stack completo (API + PostgreSQL + Prometheus + Grafana)
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker-compose up --build
```

Serviços disponíveis:
| Serviço | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger |
| Métricas | http://localhost:8080/metrics |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / admin) |

### Com Gradle (desenvolvimento local)

```bash
# 1. Configure Java 21 (obrigatório — o projeto não é compatível com Java 25+)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
echo "org.gradle.java.home=$JAVA_HOME" >> gradle.properties

# 2. Compile e rode todos os testes + quality gates
./gradlew testAll

# 3. Suba apenas o banco de dados
docker run -d --name kanban-db \
  -e POSTGRES_DB=kanbanvision \
  -e POSTGRES_USER=kanban \
  -e POSTGRES_PASSWORD=kanban \
  -p 5432:5432 \
  postgres:16

# 4. Execute a aplicação (modo dev: habilita endpoint /auth/token para obter JWT)
JWT_DEV_MODE=true ./gradlew :http_api:run
```

Com `JWT_DEV_MODE=true`, o endpoint `POST /auth/token` fica disponível para gerar tokens JWT sem credenciais. **Não usar em produção.**

Acesse a documentação interativa: **http://localhost:8080/swagger**

---

## Sobre o projeto

O **Kanban Vision** é um simulador de fluxo Kanban para múltiplas organizações. Ele expõe dois domínios via API REST:

1. **Board Management** — criar quadros (`Board`), organizar colunas (`Column`) e mover cartões (`Card`) entre estágios.
2. **Simulation Engine** — criar cenários de simulação (`Scenario`) por tenant, executar dias de simulação com decisões configuráveis (`RunDay`), persistir snapshots diários (`DailySnapshot`) e recuperar métricas de fluxo (`FlowMetrics`).

O projeto foi concebido como uma **referência prática** de arquitetura hexagonal em Kotlin, demonstrando como separar domínio, casos de uso, persistência e entrega HTTP de forma clara e testável.

---

## Arquitetura

O projeto segue os princípios de **Clean Architecture** (Arquitetura Hexagonal) combinados com **Screaming Architecture** — os módulos e pacotes expressam a intenção do negócio, não os frameworks utilizados.

```
┌─────────────────────────────────────┐
│             http_api                │  ← Entrega HTTP (Ktor + Koin)
│  routes / plugins / di              │
└──────────────┬──────────────────────┘
               │ depende de
┌──────────────▼──────────────────────┐
│             usecases                │  ← Casos de uso (CQS)
│  board / card / column / scenario   │
│  repositories (ports)               │
└──────────────┬──────────────────────┘
               │ depende de
┌──────────────▼──────────────────────┐
│              domain                 │  ← Núcleo do negócio (puro Kotlin)
│  model / valueobjects / simulation  │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│          sql_persistence            │  ← Adaptador de banco (JDBC + HikariCP)
│  repositories / serializers         │
└─────────────────────────────────────┘
```

### Fluxo de dependências

```
http_api → usecases → domain
sql_persistence → domain
sql_persistence → usecases   (implementa as interfaces de repositório)
http_api → sql_persistence   (somente na camada de DI via Koin)
```

O módulo `domain` não conhece nenhum framework. O módulo `usecases` não conhece banco de dados nem HTTP.

---

## Arquitetura — Diagramas C4

### Nível 1 — Contexto do Sistema

```mermaid
C4Context
  title Nível 1 — Contexto do Sistema

  Person(user, "Desenvolvedor / Usuário", "Consome a API via HTTP REST para gerenciar quadros Kanban e executar simulações de fluxo.")

  System(api, "Kanban Vision API", "Simulador de fluxo Kanban multi-organização. Expõe Board Management e Simulation Engine via REST.")

  SystemDb_Ext(db, "PostgreSQL", "Armazena boards, colunas, cartões, tenants, cenários de simulação e snapshots diários.")

  Rel(user, api, "HTTP REST + JWT Bearer", "JSON")
  Rel(api, db, "JDBC", "SQL")
```

### Nível 2 — Containers

```mermaid
C4Container
  title Nível 2 — Containers

  Person(user, "Usuário")

  System_Boundary(sys, "Kanban Vision API") {
    Container(http, "http_api", "Kotlin / Ktor 3 + Koin", "Ponto de entrada HTTP. Rotas, plugins, serialização e injeção de dependências.")
    Container(uc, "usecases", "Kotlin puro", "Casos de uso da aplicação. Define ports (interfaces de repositório). Padrão CQS.")
    Container(dom, "domain", "Kotlin puro — zero frameworks", "Entidades, objetos de valor, motor de simulação e regras de negócio puras.")
    Container(sql, "sql_persistence", "Kotlin / JDBC + HikariCP", "Implementa os ports de repositório. SQL puro, serialização JSON de estado, schema PostgreSQL.")
  }

  SystemDb_Ext(db, "PostgreSQL", "Banco de dados relacional")

  Rel(user, http, "HTTP/REST + JWT Bearer", "JSON")
  Rel(http, uc, "invoca casos de uso")
  Rel(uc, dom, "usa entidades e regras de negócio")
  Rel(sql, dom, "depende de (Dependency Rule)")
  Rel(sql, uc, "implementa interfaces de repositório (ports)")
  Rel(http, sql, "wiring via Koin DI apenas")
  Rel(sql, db, "JDBC", "SQL")
```

### Nível 3 — Componentes: http_api

```mermaid
C4Component
  title Nível 3 — Componentes: http_api

  Container_Ext(uc, "usecases", "Casos de uso invocados pelas rotas")

  Container_Boundary(http, "http_api") {
    Component(board_r, "BoardRoutes", "Ktor Route", "POST /boards · GET /boards/{id}")
    Component(column_r, "ColumnRoutes", "Ktor Route", "POST /columns · GET /columns/{id} · GET /boards/{boardId}/columns")
    Component(card_r, "CardRoutes", "Ktor Route", "POST /cards · GET /cards/{id} · PATCH /cards/{id}/move")
    Component(scenario_r, "ScenarioRoutes", "Ktor Route", "POST /scenarios · GET /scenarios/{id} · POST /scenarios/{scenarioId}/run · GET /scenarios/{scenarioId}/days/{day}/snapshot")
    Component(analytics_r, "ScenarioAnalyticsRoutes", "Ktor Route", "GET /scenarios/{scenarioId}/days/{day}/movements · GET /scenarios/{scenarioId}/metrics")
    Component(health_r, "HealthRoutes", "Ktor Route", "GET /health · GET /health/live · GET /health/ready")
    Component(app_mod, "AppModule", "Koin Module", "Wiring: repositórios concretos → interfaces de port · use cases")
    Component(either_a, "EitherRespond", "Adapter", "Converte DomainError em status HTTP + corpo JSON com requestId")
    Component(obs, "Observability Plugin", "Ktor Plugin", "MDC context por requisição · header X-Request-ID")
    Component(auth, "Authentication Plugin", "Ktor Plugin", "JWT Bearer via ktor-server-auth-jwt. Valida sub, audience, issuer e expiração.")
    Component(metrics_p, "Metrics Plugin", "Ktor Plugin", "MicrometerMetrics auto-instrumenta rotas. GET /metrics expõe Prometheus scrape.")
    Component(rl, "RateLimit Plugin", "Ktor Plugin", "100 req/min por IP (global). Retorna 429 Too Many Requests ao exceder.")
    Component(openapi, "OpenApi Plugin", "Ktor Plugin", "Swagger UI em /swagger · spec JSON em /api.json")
  }

  Rel(board_r, uc, "invoca use cases de Board")
  Rel(column_r, uc, "invoca use cases de Column")
  Rel(card_r, uc, "invoca use cases de Card")
  Rel(scenario_r, uc, "invoca use cases de Scenario")
  Rel(analytics_r, uc, "invoca use cases de Analytics")
  Rel(board_r, either_a, "usa para mapear erros")
  Rel(column_r, either_a, "usa para mapear erros")
  Rel(card_r, either_a, "usa para mapear erros")
  Rel(scenario_r, either_a, "usa para mapear erros")
  Rel(analytics_r, either_a, "usa para mapear erros")
```

### Nível 3 — Componentes: domain

```mermaid
C4Component
  title Nível 3 — Componentes: domain

  Container_Boundary(dom, "domain") {
    Component(board_e, "Board / Column / Card", "Kotlin data class", "Entidades de gerenciamento de quadros Kanban com seus value objects.")
    Component(scenario_e, "Scenario / ScenarioConfig", "Kotlin data class", "Entidade de cenário de simulação: tenant, WIP limit, team size, seed.")
    Component(sim_state, "SimulationState / SimulationDay", "Kotlin data class", "Estado atual da simulação: dia corrente, itens ativos, políticas.")
    Component(engine, "SimulationEngine", "Kotlin class", "Motor de simulação: processa WorkItems, aplica Decisions, gera DailySnapshot.")
    Component(snapshot, "DailySnapshot / FlowMetrics", "Kotlin data class", "Resultado imutável de um dia: throughput, WIP, aging, movimentos.")
    Component(workitem, "WorkItem / WorkItemState / ServiceClass", "Kotlin data class + enum", "Item de trabalho com ciclo de vida: WAITING → IN_PROGRESS → DONE / BLOCKED.")
    Component(decision, "Decision / DecisionType", "Kotlin data class + enum", "Decisão aplicada no dia: ADD_ITEM, MOVE_ITEM, BLOCK_ITEM, UNBLOCK_ITEM.")
    Component(movement, "Movement / MovementType", "Kotlin data class + enum", "Evento de movimentação de item registrado no snapshot.")
    Component(policy, "PolicySet", "Kotlin data class", "Conjunto de políticas Kanban aplicadas à simulação: WIP limit.")
    Component(vobj, "Value Objects", "Kotlin @JvmInline value class", "BoardId · CardId · ColumnId · TenantId · ScenarioId · WorkItemId · SimulationDay.")
  }

  Rel(engine, sim_state, "lê e produz novo estado")
  Rel(engine, decision, "aplica no dia")
  Rel(engine, snapshot, "produz ao final do dia")
  Rel(sim_state, workitem, "contém lista de")
  Rel(sim_state, policy, "aplica regras de")
  Rel(snapshot, movement, "contém lista de")
  Rel(snapshot, workitem, "registra estado final de")
  Rel(scenario_e, sim_state, "tem estado associado (1:1)")
  Rel(board_e, vobj, "identificado por")
  Rel(scenario_e, vobj, "identificado por")
```

### Sequência — Board Management

```mermaid
sequenceDiagram
  participant C as Cliente HTTP
  participant R as BoardRoutes / ColumnRoutes / CardRoutes
  participant UC as Use Cases
  participant REPO as Jdbc*Repository
  participant DB as PostgreSQL

  Note over C,DB: Criar quadro com coluna e cartão

  C->>R: POST /api/v1/boards {name} + Bearer <token>
  R->>UC: CreateBoardUseCase.execute(CreateBoardCommand)
  UC->>REPO: save(board)
  REPO->>DB: INSERT INTO boards
  DB-->>REPO: ok
  UC-->>R: BoardId
  R-->>C: 201 Created {id, name}

  C->>R: POST /api/v1/columns {boardId, name} + Bearer <token>
  R->>UC: CreateColumnUseCase.execute(CreateColumnCommand)
  UC->>REPO: save(column)
  REPO->>DB: INSERT INTO columns
  DB-->>REPO: ok
  UC-->>R: ColumnId
  R-->>C: 201 Created {id, boardId, name, position}

  C->>R: POST /api/v1/cards {columnId, title} + Bearer <token>
  R->>UC: CreateCardUseCase.execute(CreateCardCommand)
  UC->>REPO: save(card)
  REPO->>DB: INSERT INTO cards
  DB-->>REPO: ok
  UC-->>R: CardId
  R-->>C: 201 Created {id, columnId, title, position}
```

### Sequência — Simulation Engine

```mermaid
sequenceDiagram
  participant C as Cliente HTTP
  participant R as ScenarioRoutes
  participant UC as Use Cases
  participant SE as SimulationEngine
  participant SR as JdbcScenarioRepository
  participant SS as JdbcSnapshotRepository
  participant DB as PostgreSQL

  Note over C,DB: Criar cenário e executar um dia de simulação

  C->>R: POST /api/v1/scenarios {tenantId, wipLimit, teamSize, seedValue} + Bearer <token>
  R->>UC: CreateScenarioUseCase.execute(CreateScenarioCommand)
  UC->>SR: save(scenario) + saveState(initialState)
  SR->>DB: INSERT scenarios + scenario_states
  DB-->>SR: ok
  UC-->>R: ScenarioId
  R-->>C: 201 Created {scenarioId}

  C->>R: POST /api/v1/scenarios/{id}/run {decisions:[]} + Bearer <token>
  R->>UC: RunDayUseCase.execute(RunDayCommand)
  UC->>SR: findById(scenarioId)
  SR->>DB: SELECT scenarios
  DB-->>SR: scenario
  SR-->>UC: Scenario
  UC->>SR: findState(scenarioId)
  SR->>DB: SELECT scenario_states
  DB-->>SR: state
  SR-->>UC: ScenarioState
  UC->>SE: runDay(currentState, decisions)
  SE-->>UC: SimulationResult(snapshot, newState)
  UC->>SR: saveState(scenarioId, newState)
  SR->>DB: INSERT/UPDATE scenario_states
  DB-->>SR: ok
  UC->>SS: save(snapshot)
  SS->>DB: INSERT daily_snapshots
  DB-->>SS: ok
  UC-->>R: DailySnapshot
  R-->>C: 200 OK {day, metrics, movements}

  C->>R: GET /api/v1/scenarios/{id}/days/1/snapshot + Bearer <token>
  R->>UC: GetDailySnapshotUseCase.execute(GetDailySnapshotQuery)
  UC->>SS: findByDay(scenarioId, day=1)
  SS->>DB: SELECT daily_snapshots
  DB-->>SS: row (JSON)
  SS-->>UC: DailySnapshot
  UC-->>R: DailySnapshot
  R-->>C: 200 OK {day, metrics:{throughput,wipCount,...}, movements:[...]}
```

### Classes — Domínio

```mermaid
classDiagram
  class Board {
    +BoardId id
    +String name
  }
  class Column {
    +ColumnId id
    +BoardId boardId
    +String name
    +Int position
  }
  class Card {
    +CardId id
    +ColumnId columnId
    +String title
    +String description
    +Int position
  }
  class Scenario {
    +ScenarioId id
    +TenantId tenantId
    +ScenarioConfig config
    +create(tenantId, config) Scenario
  }
  class ScenarioConfig {
    +Int wipLimit
    +Int teamSize
    +Long seedValue
  }
  class SimulationState {
    +SimulationDay currentDay
    +PolicySet policySet
    +List~WorkItem~ items
  }
  class SimulationEngine {
    +runDay(scenarioId, state, decisions, seed) SimulationResult
  }
  class SimulationResult {
    +SimulationState newState
    +DailySnapshot snapshot
  }
  class DailySnapshot {
    +ScenarioId scenarioId
    +SimulationDay day
    +FlowMetrics metrics
    +List~Movement~ movements
  }
  class FlowMetrics {
    +Int throughput
    +Int wipCount
    +Int blockedCount
    +Double avgAgingDays
  }
  class WorkItem {
    +WorkItemId id
    +String title
    +WorkItemState state
    +ServiceClass serviceClass
    +Int agingDays
  }
  class WorkItemState {
    <<enumeration>>
    TODO
    IN_PROGRESS
    BLOCKED
    DONE
  }
  class Decision {
    +DecisionId id
    +DecisionType type
    +Map~String,String~ payload
  }
  class DecisionType {
    <<enumeration>>
    ADD_ITEM
    MOVE_ITEM
    BLOCK_ITEM
    UNBLOCK_ITEM
  }
  class Movement {
    +WorkItemId workItemId
    +MovementType type
    +SimulationDay day
    +String reason
  }
  class PolicySet {
    +Int wipLimit
  }

  Board "1" *-- "0..*" Column : contém
  Column "1" *-- "0..*" Card : contém
  Scenario "1" --> "1" ScenarioConfig : configurado por
  SimulationState "1" *-- "0..*" WorkItem : gerencia
  SimulationState "1" --> "1" PolicySet : aplica
  SimulationEngine ..> SimulationState : processa
  SimulationEngine ..> Decision : aplica
  SimulationEngine ..> SimulationResult : produz
  SimulationResult "1" --> "1" SimulationState : novo estado
  SimulationResult "1" --> "1" DailySnapshot : snapshot
  DailySnapshot "1" --> "1" FlowMetrics : contém
  DailySnapshot "1" *-- "0..*" Movement : registra
  WorkItem --> WorkItemState : tem estado
  Decision --> DecisionType : é do tipo
```

---

## Autenticação

Todas as rotas sob `/api/v1` exigem **JWT Bearer token** no header `Authorization`.

```
Authorization: Bearer <token>
```

### Obter um token (modo dev)

```bash
# Iniciar com JWT_DEV_MODE=true
JWT_DEV_MODE=true ./gradlew :http_api:run

# Gerar token (válido por 1 hora)
curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"subject":"meu-usuario","tenantId":"<uuid>"}' | jq -r '.token'
```

O token gerado contém as claims `sub` (subject) e `tenantId`. Em produção, a emissão de tokens deve ser delegada a um Identity Provider externo.

### Endpoints públicos (sem autenticação)

| Endpoint | Descrição |
|---|---|
| `GET /health` | Liveness simples (sempre 200) |
| `GET /health/live` | Liveness probe — processo está vivo? |
| `GET /health/ready` | Readiness probe — banco está acessível? |
| `GET /metrics` | Métricas Prometheus (scraping) |
| `GET /swagger` | Documentação interativa |
| `GET /api.json` | Spec OpenAPI 3.x |

---

## Padrão CQS (Command Query Separation)

Cada caso de uso recebe um objeto tipado que implementa `Command` (modifica estado) ou `Query` (lê estado), com validação explícita antes da execução:

### Board Management

```
CreateBoardCommand       → CreateBoardUseCase           → BoardId
GetBoardQuery            → GetBoardUseCase              → Board

CreateColumnCommand      → CreateColumnUseCase          → ColumnId
GetColumnQuery           → GetColumnUseCase             → Column
ListColumnsByBoardQuery  → ListColumnsByBoardUseCase    → List<Column>

CreateCardCommand        → CreateCardUseCase            → CardId
MoveCardCommand          → MoveCardUseCase              → Unit
GetCardQuery             → GetCardUseCase               → Card
```

### Simulation Engine

```
CreateScenarioCommand    → CreateScenarioUseCase        → ScenarioId
RunDayCommand            → RunDayUseCase                → DailySnapshot
GetScenarioQuery         → GetScenarioUseCase           → ScenarioWithState
GetDailySnapshotQuery    → GetDailySnapshotUseCase      → DailySnapshot
GetMovementsByDayQuery   → GetMovementsByDayUseCase     → List<Movement>
GetFlowMetricsRangeQuery → GetFlowMetricsRangeUseCase   → List<FlowMetrics>
```

---

## Tratamento de Erros (Either)

Os erros de domínio são modelados como valores com Arrow-kt `Either<DomainError, T>`, eliminando exceções como mecanismo de controle de fluxo.

### Hierarquia DomainError

```
sealed class DomainError
├── ValidationError(message)      → HTTP 400
├── InvalidDecision(reason)       → HTTP 400
├── BoardNotFound(id)             → HTTP 404
├── ColumnNotFound(id)            → HTTP 404
├── CardNotFound(id)              → HTTP 404
├── TenantNotFound(id)            → HTTP 404
├── ScenarioNotFound(id)          → HTTP 404
├── DayAlreadyExecuted(day)       → HTTP 409
└── PersistenceError(message)     → HTTP 500
```

### Padrão nos Use Cases

```kotlin
suspend fun execute(command: RunDayCommand): Either<DomainError, DailySnapshot>
```

### Padrão nas Rotas

```kotlin
useCase.execute(command).fold(
    ifLeft  = { error  -> call.respondWithDomainError(error) },
    ifRight = { result -> call.respond(result) },
)
```

---

## Módulos

| Módulo | Responsabilidade |
|---|---|
| `domain` | Entidades, objetos de valor, motor de simulação, regras de negócio puras |
| `usecases` | Casos de uso, interfaces de repositório (ports), CQS |
| `sql_persistence` | Implementações JDBC, serialização JSON de estado, migrations Flyway |
| `http_api` | Rotas HTTP, serialização, injeção de dependências, ponto de entrada |

---

## Stack

| Preocupação | Tecnologia |
|---|---|
| Linguagem | Kotlin 2.1 |
| HTTP | Ktor 3 (Netty) |
| Autenticação | JWT Bearer (`ktor-server-auth-jwt`) |
| Rate Limiting | `ktor-server-rate-limit` (100 req/min por IP) |
| Serialização | kotlinx.serialization |
| Injeção de dependência | Koin 4 |
| Pool de conexões | HikariCP 7.0.2 |
| Migrations | Flyway 10.21.0 |
| Banco de produção | PostgreSQL |
| Banco de testes | Embedded PostgreSQL (zonky) |
| Logging | SLF4J + Logback + logstash-logback-encoder (JSON em produção) |
| Métricas | Micrometer + Prometheus (`/metrics`) |
| Testes | JUnit 5 + MockK |
| Documentação API | ktor-openapi 5.6.0 + Swagger UI |
| Análise estática | Detekt |
| Estilo de código | KtLint |
| Cobertura | JaCoCo (mínimo 95%) |
| Build | Gradle 8 (Kotlin DSL) |
| Java | Java 21 |

---

## Estrutura de pacotes

```
domain/
├── model/
│   ├── Board.kt, Card.kt, Column.kt
│   ├── valueobjects/   BoardId, CardId, ColumnId, TenantId, ScenarioId, WorkItemId
│   ├── tenant/         Tenant.kt
│   ├── scenario/       Scenario, ScenarioConfig, SimulationState, SimulationDay,
│   │                   SimulationResult, DailySnapshot
│   ├── workitem/       WorkItem, WorkItemState, ServiceClass
│   ├── decision/       Decision, DecisionId, DecisionType
│   ├── movement/       Movement, MovementType
│   ├── metrics/        FlowMetrics
│   └── policy/         PolicySet
└── simulation/
    └── SimulationEngine.kt

usecases/
├── cqs/                Command.kt, Query.kt
├── board/              CreateBoardUseCase, GetBoardUseCase + commands/queries
├── card/               CreateCardUseCase, GetCardUseCase, MoveCardUseCase + commands/queries
├── column/             CreateColumnUseCase, GetColumnUseCase, ListColumnsByBoardUseCase + commands/queries
├── scenario/           CreateScenarioUseCase, RunDayUseCase, GetScenarioUseCase,
│                       GetDailySnapshotUseCase + commands/queries
└── repositories/       BoardRepository, CardRepository, ColumnRepository,
                        TenantRepository, ScenarioRepository, SnapshotRepository

sql_persistence/
├── DatabaseFactory.kt        (HikariCP pool + Flyway migrations)
├── resources/db/migration/   V1__initial_schema.sql, V2__add_indexes_and_constraints.sql
├── repositories/             JdbcBoardRepository, JdbcCardRepository, JdbcColumnRepository,
│                             JdbcTenantRepository, JdbcScenarioRepository, JdbcSnapshotRepository
└── serializers/              SimulationStateSerializer, DailySnapshotSerializer

http_api/
├── Main.kt
├── adapters/           EitherRespond.kt
├── di/                 AppModule.kt
├── metrics/            DomainMetrics.kt
├── plugins/            Observability, Authentication, Metrics, RateLimit,
│                       Routing, Serialization, StatusPages, OpenApi
└── routes/             BoardRoutes, CardRoutes, ColumnRoutes, HealthRoutes,
                        ScenarioRoutes, ScenarioAnalyticsRoutes, AuthRoutes (dev)
```

---

## API REST

Todas as rotas sob `/api/v1` requerem `Authorization: Bearer <token>`. Endpoints públicos estão listados na seção [Autenticação](#autenticação).

Documentação interativa disponível em `/swagger`.

### Quadros (Boards)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/v1/boards` | Cria um novo quadro |
| `GET` | `/api/v1/boards/{id}` | Busca um quadro pelo ID |

### Colunas (Columns)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/v1/columns` | Cria uma coluna em um quadro |
| `GET` | `/api/v1/columns/{id}` | Busca uma coluna pelo ID |
| `GET` | `/api/v1/boards/{boardId}/columns` | Lista todas as colunas de um quadro |

### Cartões (Cards)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/v1/cards` | Cria um cartão em uma coluna |
| `GET` | `/api/v1/cards/{id}` | Busca um cartão pelo ID |
| `PATCH` | `/api/v1/cards/{id}/move` | Move o cartão para outra coluna/posição |

### Simulação (Scenarios)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/v1/scenarios` | Cria um cenário de simulação para um tenant |
| `GET` | `/api/v1/scenarios/{scenarioId}` | Retorna o cenário e o estado atual |
| `POST` | `/api/v1/scenarios/{scenarioId}/run` | Executa um dia de simulação |
| `GET` | `/api/v1/scenarios/{scenarioId}/days/{day}/snapshot` | Retorna o snapshot de um dia |
| `GET` | `/api/v1/scenarios/{scenarioId}/days/{day}/movements` | Lista os movimentos de um dia |
| `GET` | `/api/v1/scenarios/{scenarioId}/metrics?fromDay=X&toDay=Y` | Agrega métricas de fluxo em um intervalo |

### Exemplos curl — Simulação

```bash
# Obter token (requer JWT_DEV_MODE=true)
TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"subject":"dev","tenantId":"00000000-0000-0000-0000-000000000001"}' | jq -r '.token')

# Criar um cenário
curl -s -X POST http://localhost:8080/api/v1/scenarios \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"tenantId":"<uuid>","wipLimit":3,"teamSize":2,"seedValue":42}' | jq

# Executar o próximo dia (sem decisões)
curl -s -X POST http://localhost:8080/api/v1/scenarios/<scenarioId>/run \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"decisions":[]}' | jq

# Executar com decisões
curl -s -X POST http://localhost:8080/api/v1/scenarios/<scenarioId>/run \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"decisions":[{"type":"ADD_ITEM","payload":{"title":"Nova tarefa","serviceClass":"STANDARD"}}]}' | jq

# Consultar snapshot do dia 1
curl -s http://localhost:8080/api/v1/scenarios/<scenarioId>/days/1/snapshot \
  -H "Authorization: Bearer $TOKEN" | jq
```

**Tipos de decisão disponíveis:** `MOVE_ITEM`, `BLOCK_ITEM`, `UNBLOCK_ITEM`, `ADD_ITEM`

---

## Observabilidade

### Health Checks

| Endpoint | Comportamento |
|---|---|
| `GET /health` | Sempre 200 — liveness simples |
| `GET /health/live` | 200 se o processo está vivo |
| `GET /health/ready` | 200 se o PostgreSQL está acessível; 503 caso contrário |

`/health/ready` é o endpoint indicado para **readiness probe** do Kubernetes.

### Métricas (Prometheus)

```bash
curl http://localhost:8080/metrics
```

O endpoint `/metrics` expõe métricas no formato Prometheus text (`text/plain; version=0.0.4`):
- Métricas HTTP automáticas via `MicrometerMetrics` (latência, contagem por rota/status)
- `kanban_simulation_days_executed_total` — total de dias de simulação executados

### Logging

Por padrão, logs são emitidos em formato texto. Para JSON (produção):

```bash
LOG_FORMAT=json ./gradlew :http_api:run
```

O encoder `LogstashEncoder` inclui automaticamente `requestId` do MDC como campo JSON, facilitando filtragem em Loki, CloudWatch ou Datadog.

### Correlação de Requisições

Cada requisição recebe um `requestId` propagado em toda a execução:

- **Header de entrada**: `X-Request-ID` — reutilizado se enviado pelo cliente
- **Header de resposta**: `X-Request-ID` — sempre presente
- **MDC**: `requestId`, `scenarioId` e `day` são adicionados ao contexto de log
- **Erros**: todas as respostas `4xx`/`5xx` incluem `requestId` no corpo JSON

---

## Documentação OpenAPI

- **Swagger UI**: `http://localhost:8080/swagger`
- **OpenAPI JSON**: `http://localhost:8080/api.json`

---

## Como executar

### Pré-requisitos

- Java 21
- PostgreSQL (ou Docker)

### Configuração do banco

```bash
docker run -d \
  --name kanban-db \
  -e POSTGRES_DB=kanbanvision \
  -e POSTGRES_USER=kanban \
  -e POSTGRES_PASSWORD=kanban \
  -p 5432:5432 \
  postgres:16
```

### Com Docker Compose (recomendado)

```bash
# Stack completo: API + PostgreSQL + Prometheus + Grafana
GRAFANA_ADMIN_PASSWORD=<senha> docker-compose up --build

# Com dev token habilitado
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=<senha> docker-compose up --build
```

### Subir a aplicação (Gradle)

```bash
# Modo desenvolvimento (token endpoint disponível)
JWT_DEV_MODE=true ./gradlew :http_api:run

# Modo produção
./gradlew :http_api:run
```

### Build do JAR

```bash
./gradlew :http_api:buildFatJar
java -jar http_api/build/libs/kanban-vision-api.jar
```

### Build da imagem Docker

```bash
docker build -t kanban-vision-api:local .
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/kanbanvision \
  -e JWT_SECRET=dev-secret \
  kanban-vision-api:local
```

---

## Kubernetes

Manifestos prontos em `k8s/`, aplicáveis com `kubectl` ou `kustomize`:

```bash
# Preencher secrets (editar k8s/secrets.env.example → k8s/secrets.env)
cp k8s/secrets.env.example k8s/secrets.env
# editar k8s/secrets.env com valores reais

# Deploy completo via kustomize
kubectl apply -k k8s/

# Verificar rollout
kubectl rollout status deployment/kanban-vision-api -n kanban-vision
```

| Manifesto | Recurso |
|---|---|
| `00-namespace.yml` | Namespace `kanban-vision` |
| `01-configmap.yml` | Variáveis de configuração (não-sensíveis) |
| `02-secret.template.yml` | Template de Secret (preencher antes do deploy) |
| `03-deployment.yml` | Deployment com liveness/readiness/startup probes + rolling update |
| `04-service.yml` | Service ClusterIP |
| `05-ingress.yml` | Ingress |
| `06-hpa.yml` | HorizontalPodAutoscaler (CPU target 70%) |
| `07-pdb.yml` | PodDisruptionBudget (minAvailable=1) |

As probes utilizam `/health/live` (liveness) e `/health/ready` (readiness).

---

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) — dois jobs:

### Job `quality` — todo PR e push para `main`

1. Setup Java 21 (Temurin)
2. `./gradlew testAll` — Detekt + KtLint + testes + JaCoCo (≥ 95% por módulo)
3. Upload de artefatos (14 dias): relatórios de teste, Detekt, JaCoCo
4. Comentários automáticos no PR: Detekt summary + JaCoCo coverage diff

### Job `build` — roda após `quality`

| Evento | Ação |
|---|---|
| Pull Request | Build da imagem (sem push) — valida Dockerfile |
| Push para `main` | Build + push para GHCR como `latest` e `sha-<short>` |
| Tag `v*.*.*` | Build + push como `v<version>` |

Registry: `ghcr.io/agnaldo4j/kanban-vision-api-kt`

---

## Qualidade de código

```bash
# Todos os testes + análise + cobertura
./gradlew testAll

# Por módulo
./gradlew :domain:check
./gradlew :usecases:check
./gradlew :sql_persistence:check
./gradlew :http_api:check

# Formatar código automaticamente (KtLint)
./gradlew ktlintFormat
```

O pipeline de qualidade exige:
- **Detekt** — análise estática sem violações (`warningsAsErrors = true`)
- **KtLint** — estilo de código consistente
- **JaCoCo** — cobertura mínima de 95% de instruções por módulo

---

## Testes

```bash
# Rodar todos
./gradlew testAll

# Por módulo
./gradlew :domain:test
./gradlew :usecases:test
./gradlew :sql_persistence:test
./gradlew :http_api:test

# Uma classe específica
./gradlew :domain:test --tests "com.kanbanvision.domain.model.BoardTest"
```

- **Testes de domínio** — unitários puros, sem dependências externas.
- **Testes de use case** — MockK para isolar repositórios, `kotlinx-coroutines-test`.
- **Testes de persistência** — integração com Embedded PostgreSQL (zonky).
- **Testes de rota** — `testApplication` do Ktor + Koin + MockK.

---

## Variáveis de configuração

Configuradas via `application.conf`, com fallback para variáveis de ambiente:

| Variável de ambiente | Padrão | Descrição |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/kanbanvision` | URL JDBC do PostgreSQL |
| `DATABASE_DRIVER` | `org.postgresql.Driver` | Driver JDBC |
| `DATABASE_USER` | `kanban` | Usuário do banco |
| `DATABASE_PASSWORD` | `kanban` | Senha do banco |
| `DATABASE_POOL_SIZE` | `10` | Tamanho do pool HikariCP |
| `JWT_SECRET` | *(configurar em produção)* | Segredo HMAC256 para assinar tokens |
| `JWT_ISSUER` | `kanban-vision` | Issuer dos tokens JWT |
| `JWT_AUDIENCE` | `kanban-vision-clients` | Audience dos tokens JWT |
| `JWT_TTL_MS` | `3600000` | TTL do token em ms (1 hora) |
| `JWT_DEV_MODE` | `false` | Habilita `POST /auth/token` (somente dev) |
| `LOG_FORMAT` | *(texto)* | `json` para logs estruturados em produção |

---

## Troubleshooting

### Java 21 não encontrado

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew testAll
```

### PostgreSQL recusado na inicialização

```bash
docker ps | grep kanban-db
docker start kanban-db
```

### 401 Unauthorized nas rotas

Certifique-se de incluir o header `Authorization: Bearer <token>` em todas as requisições para `/api/v1`. Use `JWT_DEV_MODE=true` e `POST /auth/token` para obter um token em desenvolvimento.

### 429 Too Many Requests

O rate limit é de 100 requisições por minuto por IP. Reduza a frequência das requisições ou aguarde o janela de 1 minuto reiniciar.

### JaCoCo falhando com cobertura abaixo de 95%

```bash
./gradlew :sql_persistence:test :sql_persistence:jacocoTestCoverageVerification
# Relatório HTML: build/reports/jacoco/test/html/index.html
```

### Detekt ou KtLint bloqueando o build

```bash
./gradlew ktlintFormat   # corrige formatação automaticamente
./gradlew detekt         # lista violações Detekt
```

---

## Licença

Este projeto é de uso educacional e de referência arquitetural.
