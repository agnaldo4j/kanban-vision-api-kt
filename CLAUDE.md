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

> **Gradle daemon: Java 25** (ADR-0024). Detekt 2.x removed the old blocker — Detekt 1.23.8's
> embedded Kotlin compiler aborted on a Java 25 daemon (`> 25.0.3`).
> Use `.sdkmanrc` (`sdk env`) to select Java 25 — the JDK lives in SDKMAN, not in the macOS
> JVM registry (do NOT use `/usr/libexec/java_home`: only Corretto 17 and 8 are registered there).
> **Compilation + runtime target: Java 25 LTS.** Daemon, toolchain and runtime share one JDK.
> Gradle 9.6.1 (wrapper).

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
| `security.md` | `**/*.kt` | OWASP Top 10 (2025) — security rules, forbidden patterns, checklists |
| `kotlin-quality.md` | `**/*.kt`, `**/*.gradle.kts` | Detekt, KtLint, JaCoCo |
| `testing.md` | `**/test/**/*.kt`, `**/*Test.kt` | Test conventions, MockK pitfalls |
| `migrations.md` | `**/db/migration/*.sql` | Flyway naming, schema history |

## Skills (`.claude/skills/`)

> Project-specific convention — invoke with `/skill-name`.

| Skill | When to use |
|---|---|
| `/owasp` | Audit and fix security issues — OWASP Top 10 (2025) |
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
| `/load-testing` | k6 load tests — baseline p95, perfis, thresholds e workflow manual (ADR-0027) |
| `/local-and-production-environment` | Dockerfile, docker-compose, Kubernetes manifests |
| `/graalvm` | GraalVM em produção — JIT vs Native Image, fases, reachability metadata (ADR-0030) |
| `/evolutionary-change` | Plan incremental changes, J-Curve, 1-gap-per-session protocol |
| `/xp-kanban` | XP + Kanban practices — **includes Board Protocol** (pull/push GitHub Project) |
| `/circular-dependency-control` | Detect, classify and eliminate circular dependencies (class, package, Gradle module) |

## Docs

- **Explicit policies**: `docs/politicas-explicitas.md` — step criteria, pull policy, quality gates, ADR rules, branch naming.
- **Quality analysis**: [Wiki — Quality Analysis](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Quality-Analysis) — 18-skill scorecard (8.5/10).
- **Backlog de gaps**: [GitHub Project #6](https://github.com/users/agnaldo4j/projects/6) — única fonte de progresso (ADR-0023). Medição de qualidade: `docs/quality/`.
