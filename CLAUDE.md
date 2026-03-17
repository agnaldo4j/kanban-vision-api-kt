# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Compile all modules
./gradlew compileKotlin

# Run all tests + quality gates
./gradlew testAll
# or per module
./gradlew :domain:test
./gradlew :usecases:test
./gradlew :sql_persistence:test
./gradlew :http_api:test

# Run a single test class
./gradlew :domain:test --tests "com.kanbanvision.domain.model.BoardTest"

# Build fat JAR (http_api module)
./gradlew :http_api:buildFatJar

# Run the application (dev mode — exposes POST /auth/token)
JWT_DEV_MODE=true ./gradlew :http_api:run
```

> **Java version**: This project requires Java 21. It is pinned via `gradle.properties` (`org.gradle.java.home`). Gradle 8.13 is incompatible with Java versions newer than 21 — ensure `JAVA_HOME` points to a Java 21 JDK before running any Gradle command.

### Docker / docker-compose

```bash
# Start full stack (app + PostgreSQL + Prometheus + Grafana)
GRAFANA_ADMIN_PASSWORD=admin docker compose up --build

# Dev mode with token endpoint enabled
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose up --build

# Ports: API=8080, Prometheus=9090, Grafana=3000
```

> `LOG_FORMAT=json` is set automatically in docker-compose. Grafana auto-provisioned at http://localhost:3000 — user: `GRAFANA_ADMIN_USER` (default `admin`), password: `GRAFANA_ADMIN_PASSWORD` (required).

## Architecture

Hexagonal / Clean Architecture with four Gradle subprojects. Dependency flow:

```
http_api → usecases → domain
sql_persistence → domain
sql_persistence → usecases
http_api → sql_persistence   (wiring only, via Koin DI)
```

- **domain** — Pure Kotlin. No framework dependencies. Contains board entities (`Board`, `Card`, `Column`) and simulation entities (`Tenant`, `Scenario`, `ScenarioConfig`, `SimulationDay`, `SimulationState`, `SimulationResult`, `DailySnapshot`) plus work items (`WorkItem`, `WorkItemState`, `ServiceClass`), decisions (`Decision`, `DecisionId`, `DecisionType`), movements (`Movement`, `MovementType`), metrics (`FlowMetrics`), policies (`PolicySet`), and the simulation engine (`SimulationEngine`). Value objects: `BoardId`, `CardId`, `ColumnId`, `TenantId`, `ScenarioId`, `WorkItemId`.
- **usecases** — Application layer. Depends on `domain` via `api()`. Follows CQS pattern: each use case accepts a `Command` or `Query` object. Use cases receive repository ports via constructor injection. **Repository interfaces (ports) live here under `repositories/`**, not in domain.
- **sql_persistence** — JDBC + HikariCP + PostgreSQL. Implements all repository interfaces. `DatabaseFactory` initialises the connection pool and runs Flyway migrations (`db/migration/V1__initial_schema.sql`, `V2__add_indexes_and_constraints.sql`). Uses `kotlinx.serialization` for JSON serialization of complex types (`SimulationState`, `DailySnapshot`) via private surrogate data classes in `serializers/`. Integration tests use Embedded PostgreSQL (zonky).
- **http_api** — Entry point. Ktor (Netty engine) + Koin DI. `Main.kt` wires everything. Plugins: Observability (MDC + requestId), Authentication (JWT Bearer), Metrics (Micrometer/Prometheus), RateLimit (100 req/min per IP), Serialization, StatusPages, Routing, OpenApi. Routes: `BoardRoutes`, `CardRoutes`, `ColumnRoutes`, `HealthRoutes`, `ScenarioRoutes` (includes analytics: movements by day, flow metrics range), `AuthRoutes` (dev-only token endpoint). `AppModule` binds repository implementations to ports and wires all use cases.

## Key Conventions

- **Convention plugin**: `buildSrc/src/main/kotlin/kanban.kotlin-common.gradle.kts` applies Kotlin JVM plugin, sets `jvmToolchain(21)`, configures Detekt, KtLint, JaCoCo, and JUnit Platform for all modules. All submodule `build.gradle.kts` apply `id("kanban.kotlin-common")`.
- **Versions**: Dependency versions are declared inline in each module's `build.gradle.kts`. There is no `libs.versions.toml`.
- **Kotlin serialization plugin** is applied in `http_api` and `sql_persistence` using `id("org.jetbrains.kotlin.plugin.serialization")` (no version) because the plugin is already on the classpath from `buildSrc`.
- **Domain stays pure**: `:domain` module must have zero framework dependencies.
- **Ports-and-adapters**: Repository interfaces (ports) are defined in `usecases/repositories/`. Implementations are in `sql_persistence/repositories/`. Routes call use cases, never repositories directly.
- **CQS pattern**: Use cases in `:usecases` are separated into commands (`Command` interface with `validate()`) and queries (`Query` interface). Example: `CreateBoardUseCase` accepts `CreateBoardCommand`.
- **Error handling**: Domain errors are modelled as `Either<DomainError, T>` using Arrow-kt. Use cases use `either { ensure(...) { } }` with `zipOrAccumulate` for multi-field validation. Routes call `.fold(ifLeft = { respondWithDomainError(it) }, ifRight = { ... })`.
- **Coverage gate**: JaCoCo enforces 95% minimum instruction coverage per module. Build fails if not met.
- **Package root**: `com.kanbanvision`

## Known Pitfalls

- **MockK + `@JvmInline value class`**: `any()` matcher with typed inline value classes (e.g., `any<ScenarioId>()`) may fail at runtime. Use specific values or untyped `any()` for the first argument when mocking methods that accept inline value class parameters.
- **Detekt `LargeClass` threshold**: 200 lines. Test files that grow beyond this must be split into focused test classes (e.g., `ScenarioCreationRoutesTest`, `ScenarioRunDayRoutesTest`, `ScenarioQueryRoutesTest`).
- **`CreateScenarioUseCase` generates its own ID**: The use case calls `Scenario.create()` internally which generates a new `ScenarioId`. When mocking `scenarioRepository.saveState(...)` in tests for this use case, use `any()` for the scenarioId argument instead of a fixed value.
- **`IntegrationTestSetup.closeDataSource()` / `reinitDataSource()`**: Use these helpers in `@BeforeEach`/`@AfterEach` to force `PersistenceError` paths in JDBC integration tests.
- **JWT_DEV_MODE**: The `POST /auth/token` endpoint is only mounted when `JWT_DEV_MODE=true`. In tests that exercise `AuthRoutes`, ensure this env var or the Koin module sets dev mode. Production deployments must never set this to `true`.
- **Prometheus metric naming**: Micrometer converts dots to underscores and appends `_total` to counters. `Counter.builder("kanban.simulation.days.executed")` → Prometheus exposes as `kanban_simulation_days_executed_total`.

## Code Quality

All quality tools run as part of `./gradlew testAll`:

| Tool | Purpose | Config |
|---|---|---|
| Detekt 1.23.7 | Static analysis | `config/detekt/detekt.yml` |
| KtLint 1.5.0 | Code formatting | Kotlin official style |
| JaCoCo | Coverage verification | 95% minimum instruction coverage |

Detekt is configured with `warningsAsErrors = true`. Key thresholds: cyclomatic complexity 10, max line length 140, max functions per class 15, max lines per class 200.

## Skills (Claude Code)

Skills are stored in `.claude/skills/` and loaded automatically by Claude Code. Use `/skill-name` to invoke:

| Skill | When to use |
|---|---|
| `/ddd` | Modelar Entities, Value Objects, Aggregates, Domain Events, Bounded Contexts, EventStorming |
| `/adr` | Propor ou revisar Architecture Decision Records antes de qualquer mudança significativa |
| `/clean-architecture` | Decidir onde uma classe, dependência ou módulo deve residir |
| `/screaming-architecture` | Avaliar se a estrutura de pacotes comunica o domínio |
| `/solid-principles` | Revisar coesão, acoplamento e responsabilidades de classes |
| `/fp-oo-kotlin` | Escrever funções puras, Either, Arrow-kt, imutabilidade |
| `/refactoring` | Identificar code smells e aplicar técnicas de refactoring |
| `/testing-and-observability` | Escrever testes JUnit 5 + MockK, configurar MDC/logging |
| `/kotlin-quality-pipeline` | Corrigir violações Detekt/KtLint, ajustar JaCoCo |
| `/openapi-quality` | Auditar e melhorar specs OpenAPI/Swagger nas rotas |
| `/db-migrations` | Gerenciar Flyway migrations e schema PostgreSQL |
| `/c4-model` | Atualizar diagramas C4 no README após mudanças de arquitetura |
| `/definition-of-done` | Verificar critérios de conclusão antes de marcar uma tarefa como done |
| `/microservices-modular-monolith` | Avaliar boundaries de módulos, decidir extração para microserviço, planejar migração incremental |
| `/opentelemetry` | Implementar logs JSON, métricas Prometheus, distributed tracing (OTel Agent), health check com dependências e stack Grafana local |
| `/local-and-production-environment` | Criar Dockerfile, docker-compose, manifestos Kubernetes (Deployment, Service, Ingress, HPA, PDB) e operar o ambiente local com Minikube |
| `/evolutionary-change` | Planejar e executar mudanças de forma incremental/normativa, evitando crises estruturais, regressões e esgotamento de contexto LLM — aplica o J-Curve, Identity Threat Theory e protocolo 1-gap-por-sessão |

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) runs on every push to `main` and on pull requests against `main`. Two jobs:

**Job `quality`** (runs on every PR and push):
1. Setup Java 21 (Temurin)
2. Run `./gradlew testAll` (includes Detekt, KtLint, tests, JaCoCo coverage gate)
3. Upload artifacts (14-day retention): test reports, Detekt reports, JaCoCo coverage
4. Post Detekt analysis summary and JaCoCo coverage diff as PR comments

**Job `build`** (runs after `quality`):
1. On pull requests: builds Docker image only (no push) — validates Dockerfile
2. On `main` push: builds + pushes to GHCR — tags: `sha-<short>` + `latest`
3. On `v*.*.*` tag: builds + pushes to GHCR — tags: `sha-<short>` + `v<version>` + `latest`
   - Layer cache via GitHub Actions cache

## Stack

| Concern | Library |
|---|---|
| HTTP | Ktor 3.1.2 (Netty engine) |
| Authentication | JWT Bearer (`ktor-server-auth-jwt`) |
| Rate Limiting | `ktor-server-rate-limit` (100 req/min per IP) |
| Serialization | kotlinx.serialization |
| DI | Koin 4.1.1 |
| JDBC | Raw JDBC + HikariCP 7.0.2 |
| DB Migrations | Flyway 10.21.0 |
| Production DB | PostgreSQL 42.7.5 |
| Test DB | Embedded PostgreSQL (zonky) |
| Metrics | Micrometer + Prometheus (`/metrics`) |
| Logging | SLF4J + Logback + logstash-logback-encoder (JSON via `LOG_FORMAT=json`) |
| Functional types | Arrow-kt (Either, Raise, zipOrAccumulate) |
| Testing | JUnit 5.11.4 + MockK 1.14.2 |
| OpenAPI | ktor-openapi 5.6.0 + ktor-swagger-ui 5.6.0 |
| Static analysis | Detekt 1.23.7 |
| Formatting | KtLint 1.5.0 |
| Coverage | JaCoCo |
| Containerisation | Docker multi-stage (`eclipse-temurin:21-jre`) + docker-compose |
| Kubernetes | Manifests in `k8s/` (Namespace, ConfigMap, Deployment, Service, Ingress, HPA, PDB) |
| Observability stack | Prometheus 2.54 + Grafana 11.3 (auto-provisioned dashboard + alerts) |
