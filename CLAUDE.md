# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Compile all modules
./gradlew compileKotlin

# Run all tests
./gradlew testAll
# or per module
./gradlew :domain:test
./gradlew :usecases:test
./gradlew :persistence:test
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
persistence → domain
http_api → persistence   (wiring only, via Koin DI)
```

- **domain** — Pure Kotlin. No framework dependencies. Contains entities (`Board`, `Card`, `Column`), value objects (`BoardId`, `CardId`, `ColumnId`), domain events (`sealed interface DomainEvent`), and repository ports (interfaces).
- **usecases** — Application layer. Depends on `domain` via `api()`. Use case classes receive repository ports via constructor injection.
- **persistence** — Exposed ORM + HikariCP + PostgreSQL. Implements domain port interfaces. `DatabaseFactory` initialises the connection pool and runs schema migration via `SchemaUtils`. H2 is available for tests.
- **http_api** — Entry point. Ktor (Netty engine) + Koin DI. `Main.kt` wires everything. Plugins live in `plugins/` (Serialization, StatusPages, Routing). Routes live in `routes/`. `AppModule` binds repository implementations to domain ports and wires use cases.

## Key Conventions

- **Convention plugin**: `buildSrc/src/main/kotlin/kanban.kotlin-common.gradle.kts` applies Kotlin JVM plugin, sets `jvmToolchain(21)`, and configures JUnit Platform for all modules. All submodule `build.gradle.kts` apply `id("kanban.kotlin-common")`.
- **Version catalog**: All dependency versions live in `gradle/libs.versions.toml`. Submodules reference aliases like `libs.ktor.server.core`.
- **Kotlin serialization plugin** is applied in `http_api` using `id("org.jetbrains.kotlin.plugin.serialization")` (no version) because the plugin is already on the classpath from `buildSrc`.
- **Domain stays pure**: `:domain` module must have zero framework dependencies.
- **Ports-and-adapters**: Repository interfaces are defined in `domain/port/`. Implementations are in `persistence/repositories/`. Routes call use cases, never repositories directly.
- **Package root**: `com.kanbanvision`

## Stack

| Concern | Library |
|---|---|
| HTTP | Ktor 3 (Netty engine) |
| Serialization | kotlinx.serialization |
| DI | Koin 4 |
| ORM | Exposed 0.61 |
| Connection pool | HikariCP |
| Production DB | PostgreSQL |
| Test DB | H2 (in-memory) |
| Testing | JUnit 5 + MockK |
