# CLAUDE.md

This file is the entry point for Claude Code in this project.
Detailed rules are organized in `.claude/rules/` (loaded automatically).

## Build & Run Commands

```bash
./gradlew testAll                                        # all tests + quality gates
./gradlew :domain:test                                   # per-module
./gradlew :domain:test --tests "*.BoardTest"             # single class
./gradlew :http_api:buildFatJar                          # fat JAR
JWT_DEV_MODE=true ./gradlew :http_api:run                # run (dev mode)
./gradlew ktlintFormat                                   # auto-fix formatting
```

> **Java 21 required.** Gradle 8.13 is incompatible with newer JDKs.
> Pin via `gradle.properties` (`org.gradle.java.home`) or `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

```bash
# Full stack (API + PostgreSQL + Prometheus + Grafana)
GRAFANA_ADMIN_PASSWORD=admin docker compose up --build

# Dev mode (enables POST /auth/token)
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose up --build
# Ports: API=8080  Prometheus=9090  Grafana=3000
```

## Rules (`.claude/rules/`)

| File | Loaded | Content |
|---|---|---|
| `architecture.md` | always | Modules, conventions, pitfalls, dependency rule |
| `workflow.md` | always | Kanban Board Protocol, Gap Execution Protocol |
| `stack.md` | always | Tech stack, CI/CD |
| `kotlin-quality.md` | `**/*.kt`, `**/*.gradle.kts` | Detekt, KtLint, JaCoCo |
| `testing.md` | `**/test/**/*.kt`, `**/*Test.kt` | Test conventions, MockK pitfalls |
| `migrations.md` | `**/db/migration/*.sql` | Flyway naming, schema history |

## Skills (`.claude/skills/`)

> Project-specific convention — invoke with `/skill-name`.

| Skill | When to use |
|---|---|
| `/ddd` | Model Entities, Value Objects, Aggregates, Domain Events |
| `/adr` | Propose or review Architecture Decision Records |
| `/clean-architecture` | Decide where a class or dependency belongs |
| `/screaming-architecture` | Evaluate if package structure communicates domain intent |
| `/solid-principles` | Review cohesion, coupling and responsibilities |
| `/fp-oo-kotlin` | Either, Arrow-kt, immutability, pure functions |
| `/refactoring` | Identify code smells and apply refactoring techniques |
| `/testing-and-observability` | JUnit 5 + MockK tests, MDC/logging |
| `/kotlin-quality-pipeline` | Fix Detekt/KtLint violations, adjust JaCoCo |
| `/openapi-quality` | Audit and improve OpenAPI/Swagger specs |
| `/db-migrations` | Flyway migrations and PostgreSQL schema |
| `/c4-model` | Update C4 diagrams in README after architecture changes |
| `/definition-of-done` | Verify DoD before marking any task complete |
| `/microservices-modular-monolith` | Evaluate module boundaries, plan extraction |
| `/opentelemetry` | JSON logs, Prometheus metrics, OTel Agent, Grafana stack |
| `/local-and-production-environment` | Dockerfile, docker-compose, Kubernetes manifests |
| `/evolutionary-change` | Plan incremental changes, J-Curve, 1-gap-per-session protocol |
| `/xp-kanban` | XP + Kanban practices — **includes Board Protocol** (pull/push GitHub Project) |

## Docs

- **Explicit policies**: `docs/politicas-explicitas.md` — column criteria, pull policy, quality gates, ADR rules, branch naming.
- **Quality analysis**: `README.md` → section "Avaliação de Qualidade" — 18-skill scorecard (8.5/10).
- **ADR roadmap**: `adr/ADR-0004-avaliacao-qualidade-gaps-priorizados.md` — gap list P1–P4.
