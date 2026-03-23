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
val exposedVersion = "1.1.1"
implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
```

### Table Definitions

Define table objects alongside their repository. Never define table objects in `domain/` or `usecases/`.

```kotlin
// sql_persistence/tables/SimulationsTable.kt
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
| ForbiddenImport | `Exposed*Repository` imports restricted to `AppModule` (same as `Jdbc*`) |

---

## Known Pitfalls

- **Board aggregate hydration**: `JdbcBoardRepository.findById()` returns `Board(columns = emptyList())`. Use cases must load existing columns/cards and build a hydrated board (`board.copy(columns = existingColumns)`) before calling `board.addColumn()` / `board.addCard()`. Same for `Column(cards = emptyList())` before `board.addCard()`.
- **`raise()` inside `either {}`**: member of `Raise<E>` — available implicitly inside the DSL block. Do NOT `import arrow.core.raise.raise` — it is not a top-level function.
- **JWT_DEV_MODE**: `POST /auth/token` only mounted when `JWT_DEV_MODE=true`. Never true in production.
- **Prometheus metric naming**: `Counter.builder("kanban.simulation.days.executed")` → `kanban_simulation_days_executed_total`.
- **Exposed `transaction {}` is blocking**: runs synchronously on the current thread. Do not call inside a Ktor coroutine handler without a dispatcher; use `withContext(Dispatchers.IO) { transaction { ... } }`.
- **ResultRow fields must be read inside `transaction {}`**: lazy-loaded text/blob columns throw outside the transaction boundary. Always `map { it.toDomain() }` before the block ends.