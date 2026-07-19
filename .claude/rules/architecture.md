# Architecture — Hexagonal / Clean Architecture

Dependency flow (Dependency Rule — never invert):
```
http_api → usecases → domain-simulation → domain-kanban → domain-common
sql_persistence → (domain-simulation · domain-kanban · domain-common) + usecases
http_api → sql_persistence   (wiring only, via Koin DI)
```

> **Domain split by bounded context (ADR-0038):** the old single `:domain` module is three modules —
> `:domain-common` (base kernel), `:domain-kanban` (Kanban Management BC), `:domain-simulation`
> (Simulation BC) — with the graph `domain-simulation → domain-kanban → domain-common`. Package FQNs
> are unchanged (`com.kanbanvision.domain.*`); only the Gradle module boundaries are new.

> **Enforced by Konsist + JUnit** (ADR-0026): the Dependency Rule, domain purity, ports placement, the
> bounded-context boundaries (Kanban Management BC ↛ Simulation BC, per `docs/context-map.md`),
> whole-graph package acyclicity (`PackageCycleTest`, GAP-BN), the contract-package rule
> (`ContractPackageTest`, GAP-BS/ADR-0033 — no cross-module import of `*.internal`) and the
> **project-dependency graph** `simulation → kanban → common` (`ProjectDependencyGraphTest`,
> GAP-CL/ADR-0038 — parses the `build.gradle.kts`, since Konsist can't see Gradle `project` deps) are
> fitness functions in `architecture/src/test/kotlin/` — they run in `testAll` and fail CI.

## Modules

- **The domain is three pure-Kotlin modules (ADR-0038), zero framework dependencies:**
  - **domain-common** — the neutral base kernel: `Domain<ID>`, `Audit`, the open `DomainError` interface and the generic `CommonError` sealed group (`ValidationError`/`PersistenceError`/`ServiceUnavailable`/`Forbidden`). Depends on nothing.
  - **domain-kanban** — Kanban Management BC. `Board` is the Aggregate Root: `Board.addStep(name, requiredAbility)` enforces unique **step** name per board; `Board.addCard(step, title, description)` validates the step belongs to the board. Contains `Board`/`Card`/`Step`/`Worker`/`Ability`, the `Organization`/`Tribe`/`Squad`/`PolicySet` topology, the `KanbanError` sealed group, and the kanban IDs (`@JvmInline value class BoardId/StepId/CardId` — ADR-0034). Depends on `domain-common`.
  - **domain-simulation** — Simulation BC. `Simulation`/`Scenario`/`SimulationEngine`/`DailySnapshot`/sealed `Decision`, `DomainEvent`, the `SimulationError` sealed group, and the simulation IDs (`SimulationId`/`ScenarioId`). Depends on `domain-kanban` (customer-supplier: `Scenario` holds a `Board`, `SimulationEngine` drives kanban entities) and `domain-common`.
- **usecases** — Application layer. CQS pattern: each use case accepts one `Command` (mutates) or `Query` (reads). Repository interfaces (ports) live under `repositories/` — NOT in domain.
- **sql_persistence** — **Exposed DSL** + HikariCP (no raw JDBC). Implements the three repository ports (`Organization`, `Simulation`, `Snapshot` — the Board/Card/Step ports were removed in GAP-BF). Flyway migrations: `V1__initial_schema.sql` (all tables + FK indexes + CHECK constraints, incl. `UNIQUE(steps.board_id, steps.name)`) and `V2__jsonb_simulation_blobs.sql` (TEXT→JSONB, ADR-0013). There is no `V3`. JSON serialization via `kotlinx.serialization` surrogate classes in `internal/serializers/`. Integration tests use Embedded PostgreSQL (zonky). **Implementation packages** (`internal/repositories`, `internal/tables`, `internal/serializers`) are module-private by the `*.internal` contract-package rule (GAP-BS); only the root types (`DatabaseFactory`, `DatabaseConfig`, `DbCircuitBreaker`) are the public bootstrap contract.
- **http_api** — Ktor (Netty) + Koin DI. `Main.kt` wires everything. Plugins: Observability, Authentication (JWT Bearer), Metrics (Micrometer), RateLimit (100 req/min), Serialization, StatusPages, Routing, OpenApi. `AppModule` binds implementations to ports.

## Key Conventions

- **Convention plugin**: `buildSrc/src/main/kotlin/kanban.kotlin-common.gradle.kts` — Kotlin JVM, `jvmToolchain(25)`, Detekt 2.x (`dev.detekt`), KtLint, JaCoCo, JUnit for all modules.
- **Versions**: Declared inline per `build.gradle.kts`. No `libs.versions.toml`.
- **Kotlin serialization plugin**: applied in `http_api` and `sql_persistence` without version — already on classpath from `buildSrc`.
- **Domain stays pure**: the three domain modules (`:domain-common`/`:domain-kanban`/`:domain-simulation`) must have zero framework imports (`DomainPurityTest` scopes all `com.kanbanvision.domain` production files).
- **Ports-and-adapters**: interfaces in `usecases/repositories/`; implementations in `sql_persistence/internal/repositories/`. Routes call use cases, never repositories.
- **CQS**: `CreateBoardUseCase` ← `CreateBoardCommand`; `GetBoardUseCase` ← `GetBoardQuery`.
- **Error handling**: `Either<DomainError, T>` via Arrow-kt. `either { ensure(...) {} }` + `zipOrAccumulate` for multi-field validation. Routes use `.fold(ifLeft = { respondWithDomainError(it) }, ifRight = { ... })`.
- **Pacotes de contrato vs `*.internal`**: fitness function Konsist (`architecture/ContractPackageTest`, GAP-BS/ADR-0033) proíbe qualquer import cross-module de um pacote `*.internal` — a única exceção é o `AppModule` (seam de DI). Subsume a antiga regra `Jdbc*`/`Exposed*`-só-no-AppModule (ADR-0028): os adapters agora vivem em `persistence.internal.repositories`. O `ForbiddenImport` do Detekt cobre imports de segurança (`ObjectInputStream`, `MessageDigest`).
- **Package root**: `com.kanbanvision`

## Exposed ORM (sql_persistence only)

Reference: https://www.jetbrains.com/help/exposed/home.html

Exposed is the **approved ORM for new persistence code** in `sql_persistence/`. It replaces raw JDBC for new repositories while keeping HikariCP and Flyway unchanged.

### Mode: DSL only (not DAO)

Use **DSL mode** exclusively — it aligns with the FP style of the project (pure transformations, immutable data, no entity mutation).

```
DSL  ✅ — functional, composable, returns values
DAO  ❌ — mutable entity objects violate the immutability rule
```

### Modules (add to `sql_persistence/build.gradle.kts`)

```kotlin
val exposedVersion = "1.3.1"
implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
```

### Table Definitions

Define table objects alongside their repository. Never define table objects in `domain/` or `usecases/`.

```kotlin
// sql_persistence/internal/tables/SimulationsTable.kt
object SimulationsTable : Table("simulations") {
    val id             = uuid("id")
    val organizationId = uuid("organization_id")
    val wipLimit       = integer("wip_limit")
    val teamSize       = integer("team_size")
    val seedValue      = long("seed_value")
    val state          = text("state")    // JSON blob

    override val primaryKey = PrimaryKey(id)
}
```

- **No `SchemaUtils.create()`** — Flyway manages schema exclusively.
- Column types must match the existing Flyway-managed schema exactly.
- JSON blobs: use `text()` column + `kotlinx.serialization` for encoding/decoding.

### HikariCP Integration

Wire Exposed into the existing HikariCP `DataSource` (already created in `AppModule`):

```kotlin
// sql_persistence/ExposedDatabase.kt
import org.jetbrains.exposed.v1.jdbc.Database

fun connectExposed(dataSource: DataSource): Database =
    Database.connect(datasource = dataSource)
```

Call once at startup in `AppModule`. The same `HikariDataSource` used by raw JDBC repositories can be shared.

### Transactions

All Exposed operations must run inside `transaction {}`. The block returns its last expression — use this to return values without mutable state:

```kotlin
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

// ✅ Return value from transaction — pure at the call site
val simulation: Simulation? = transaction {
    SimulationsTable
        .selectAll()
        .where { SimulationsTable.id eq simulationId.value }
        .singleOrNull()
        ?.toDomain()
}
```

### Wrapping with Arrow `Either` (mandatory pattern)

Every repository method must return `Either<DomainError, T>`. Wrap `transaction {}` inside `catch {}` to convert `ExposedException` into typed domain errors:

```kotlin
import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

suspend fun findSimulationById(id: SimulationId): Either<DomainError, Simulation?> =
    either {
        catch({
            transaction {
                SimulationsTable
                    .selectAll()
                    .where { SimulationsTable.id eq id.value }
                    .singleOrNull()
                    ?.toDomain()
            }
        }) { e: Exception ->
            raise(DomainError.PersistenceError(e.message ?: "DB error"))
        }
    }
```

### Mapping `ResultRow` to Domain Entities

Map rows to domain objects using pure extension functions — never embed mapping logic in the query chain:

```kotlin
// sql_persistence/mappers/SimulationMapper.kt
fun ResultRow.toDomain(): Simulation =
    Simulation(
        id             = SimulationId(this[SimulationsTable.id]),
        organizationId = OrganizationId(this[SimulationsTable.organizationId]),
        wipLimit       = this[SimulationsTable.wipLimit],
        teamSize       = this[SimulationsTable.teamSize],
        seedValue      = this[SimulationsTable.seedValue],
        state          = Json.decodeFromString(this[SimulationsTable.state]),
    )
```

### DSL Query Patterns

```kotlin
// SELECT all with filter
transaction {
    SimulationsTable.selectAll()
        .where { SimulationsTable.organizationId eq orgId.value }
        .orderBy(SimulationsTable.createdAt to SortOrder.DESC)
        .map { it.toDomain() }
}

// INSERT
transaction {
    SimulationsTable.insert {
        it[id]             = simulation.id.value
        it[organizationId] = simulation.organizationId.value
        it[wipLimit]       = simulation.wipLimit
        it[teamSize]       = simulation.teamSize
        it[seedValue]      = simulation.seedValue
        it[state]          = Json.encodeToString(simulation.state)
    }
}

// UPDATE
transaction {
    SimulationsTable.update({ SimulationsTable.id eq simulation.id.value }) {
        it[state] = Json.encodeToString(simulation.state)
    }
}

// DELETE
transaction {
    SimulationsTable.deleteWhere { SimulationsTable.id eq id.value }
}
```

### Rules for `sql_persistence/`

| Rule | Detail |
|---|---|
| DSL only | No DAO entities — immutability rule |
| No schema creation | Flyway owns schema; never call `SchemaUtils.create()` |
| Wrap in `either {}` | All repository methods return `Either<DomainError, T>` |
| Map outside chain | `ResultRow.toDomain()` is a pure extension function |
| Shared HikariCP | One `DataSource` shared between Exposed and any raw JDBC |
| Konsist (ContractPackageTest) | `*.internal` packages (incl. `internal/repositories`, `internal/tables`, `internal/serializers`) not importable cross-module except `AppModule` (GAP-BS/ADR-0033; subsumes the ADR-0028 `Jdbc`/`Exposed` rule) |

---

## Known Pitfalls

- **Board management not wired**: after GAP-BF there is no `Board`/`Step`/`Card` repository or use case — the `Board` aggregate exists (invariants exercised by domain tests) but has no persistence. If Board management is wired later, load existing steps/cards and hydrate (`board.copy(steps = existingSteps)`) before calling `board.addStep()` / `board.addCard()`.
- **`raise()` inside `either {}`**: member of `Raise<E>` — available implicitly inside the DSL block. Do NOT `import arrow.core.raise.raise` — it is not a top-level function.
- **JWT_DEV_MODE**: `POST /auth/token` only mounted when `JWT_DEV_MODE=true`. Never true in production.
- **Prometheus metric naming**: `Counter.builder("kanban.simulation.days.executed")` → `kanban_simulation_days_executed_total`.
- **Exposed `transaction {}` is blocking**: runs synchronously on the current thread. Do not call inside a Ktor coroutine handler without a dispatcher; use `withContext(Dispatchers.IO) { transaction { ... } }`.
- **ResultRow fields must be read inside `transaction {}`**: lazy-loaded text/blob columns throw outside the transaction boundary. Always `map { it.toDomain() }` before the block ends.