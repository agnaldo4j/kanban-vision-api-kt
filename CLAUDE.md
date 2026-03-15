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

# Run the application
./gradlew :http_api:run
```

> **Java version**: This project requires Java 21. It is pinned via `gradle.properties` (`org.gradle.java.home`). The system default is Java 25 which is incompatible with Gradle 8.13.

## Architecture

Hexagonal / Clean Architecture with four Gradle subprojects. Dependency flow:

```
http_api → usecases → domain
sql_persistence → domain
sql_persistence → usecases
http_api → sql_persistence   (wiring only, via Koin DI)
```

- **domain** — Pure Kotlin. No framework dependencies. Contains entities (`Board`, `Card`, `Column`) and value objects (`BoardId`, `CardId`, `ColumnId`). Repository ports (interfaces) are defined here under `repositories/`.
- **usecases** — Application layer. Depends on `domain` via `api()`. Follows CQS pattern: each use case accepts a `Command` or `Query` object. Use cases receive repository ports via constructor injection.
- **sql_persistence** — JDBC + HikariCP + PostgreSQL. Implements domain repository interfaces. `DatabaseFactory` initialises the connection pool and runs schema creation via raw SQL. H2 is available for tests.
- **http_api** — Entry point. Ktor (Netty engine) + Koin DI. `Main.kt` wires everything. Plugins live in `plugins/` (Observability, Serialization, StatusPages, Routing, OpenApi). Routes live in `routes/`. `AppModule` binds repository implementations to domain ports and wires use cases.

## Key Conventions

- **Convention plugin**: `buildSrc/src/main/kotlin/kanban.kotlin-common.gradle.kts` applies Kotlin JVM plugin, sets `jvmToolchain(21)`, configures Detekt, KtLint, JaCoCo, and JUnit Platform for all modules. All submodule `build.gradle.kts` apply `id("kanban.kotlin-common")`.
- **Versions**: Dependency versions are declared inline in each module's `build.gradle.kts`. There is no `libs.versions.toml`.
- **Kotlin serialization plugin** is applied in `http_api` using `id("org.jetbrains.kotlin.plugin.serialization")` (no version) because the plugin is already on the classpath from `buildSrc`.
- **Domain stays pure**: `:domain` module must have zero framework dependencies.
- **Ports-and-adapters**: Repository interfaces are defined in `domain/repositories/`. Implementations are in `sql_persistence/repositories/`. Routes call use cases, never repositories directly.
- **CQS pattern**: Use cases in `:usecases` are separated into commands (`Command` interface with `validate()`) and queries (`Query` interface). Example: `CreateBoardUseCase` accepts `CreateBoardCommand`.
- **Coverage gate**: JaCoCo enforces 90% minimum instruction coverage per module. Build fails if not met.
- **Package root**: `com.kanbanvision`

## Code Quality

All quality tools run as part of `./gradlew testAll`:

| Tool | Purpose | Config |
|---|---|---|
| Detekt 1.23.7 | Static analysis | `config/detekt/detekt.yml` |
| KtLint 1.5.0 | Code formatting | Kotlin official style |
| JaCoCo | Coverage verification | 90% minimum instruction coverage |

Detekt is configured with `warningsAsErrors = true`. Key thresholds: cyclomatic complexity 10, max line length 140, max functions per class 15.

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) runs on every push to `main` and on pull requests against `main`:

1. Setup Java 21 (Temurin)
2. Run `./gradlew testAll` (includes Detekt, KtLint, tests, JaCoCo coverage gate)
3. Upload artifacts (14-day retention): test reports, Detekt reports, JaCoCo coverage

## Stack

| Concern | Library |
|---|---|
| HTTP | Ktor 3.1.2 (Netty engine) |
| Serialization | kotlinx.serialization |
| DI | Koin 4.1.0 |
| JDBC | Raw JDBC + HikariCP 6.3.0 |
| Production DB | PostgreSQL 42.7.5 |
| Test DB | H2 2.3.232 (in-memory) |
| Testing | JUnit 5.11.4 + MockK 1.14.2 |
| OpenAPI | ktor-openapi 5.0.2 + ktor-swagger-ui 5.0.2 |
| Static analysis | Detekt 1.23.7 |
| Formatting | KtLint 1.5.0 |
| Coverage | JaCoCo |