# Architecture — Hexagonal / Clean Architecture

Dependency flow (Dependency Rule — never invert):
```
http_api → usecases → domain
sql_persistence → domain
sql_persistence → usecases
http_api → sql_persistence   (wiring only, via Koin DI)
```

## Modules

- **domain** — Pure Kotlin. Zero framework dependencies. `Board` is the Aggregate Root for Board Management: `Board.addColumn(name)` enforces column name uniqueness per board; `Board.addCard(column, title, description)` validates the column belongs to the board. Contains board entities (`Board`, `Card`, `Column`), simulation entities, value objects (`BoardId`, `CardId`, `ColumnId`, `TenantId`, `ScenarioId`, `WorkItemId`).
- **usecases** — Application layer. CQS pattern: each use case accepts one `Command` (mutates) or `Query` (reads). Repository interfaces (ports) live under `repositories/` — NOT in domain.
- **sql_persistence** — JDBC + HikariCP. Implements all ports. Flyway migrations: `V1__initial_schema.sql`, `V2__add_indexes_and_constraints.sql`, `V3__unique_column_name_per_board.sql` (UNIQUE on `columns(board_id, name)`). JSON serialization via `kotlinx.serialization` surrogate classes in `serializers/`. Integration tests use Embedded PostgreSQL (zonky).
- **http_api** — Ktor (Netty) + Koin DI. `Main.kt` wires everything. Plugins: Observability, Authentication (JWT Bearer), Metrics (Micrometer), RateLimit (100 req/min), Serialization, StatusPages, Routing, OpenApi. `AppModule` binds implementations to ports.

## Key Conventions

- **Convention plugin**: `buildSrc/src/main/kotlin/kanban.kotlin-common.gradle.kts` — Kotlin JVM, `jvmToolchain(21)`, Detekt, KtLint, JaCoCo, JUnit for all modules.
- **Versions**: Declared inline per `build.gradle.kts`. No `libs.versions.toml`.
- **Kotlin serialization plugin**: applied in `http_api` and `sql_persistence` without version — already on classpath from `buildSrc`.
- **Domain stays pure**: `:domain` must have zero framework imports.
- **Ports-and-adapters**: interfaces in `usecases/repositories/`; implementations in `sql_persistence/repositories/`. Routes call use cases, never repositories.
- **CQS**: `CreateBoardUseCase` ← `CreateBoardCommand`; `GetBoardUseCase` ← `GetBoardQuery`.
- **Error handling**: `Either<DomainError, T>` via Arrow-kt. `either { ensure(...) {} }` + `zipOrAccumulate` for multi-field validation. Routes use `.fold(ifLeft = { respondWithDomainError(it) }, ifRight = { ... })`.
- **ForbiddenImport**: Detekt rule prevents `Jdbc*Repository` imports outside `AppModule`.
- **Package root**: `com.kanbanvision`

## Known Pitfalls

- **Board aggregate hydration**: `JdbcBoardRepository.findById()` returns `Board(columns = emptyList())`. Use cases must load existing columns/cards and build a hydrated board (`board.copy(columns = existingColumns)`) before calling `board.addColumn()` / `board.addCard()`. Same for `Column(cards = emptyList())` before `board.addCard()`.
- **`raise()` inside `either {}`**: member of `Raise<E>` — available implicitly inside the DSL block. Do NOT `import arrow.core.raise.raise` — it is not a top-level function.
- **JWT_DEV_MODE**: `POST /auth/token` only mounted when `JWT_DEV_MODE=true`. Never true in production.
- **Prometheus metric naming**: `Counter.builder("kanban.simulation.days.executed")` → `kanban_simulation_days_executed_total`.