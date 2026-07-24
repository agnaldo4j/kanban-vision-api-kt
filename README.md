[![CI](https://github.com/agnaldo4j/kanban-vision-api-kt/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/agnaldo4j/kanban-vision-api-kt/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/agnaldo4j/kanban-vision-api-kt/graph/badge.svg)](https://codecov.io/gh/agnaldo4j/kanban-vision-api-kt)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Last Commit](https://img.shields.io/github/last-commit/agnaldo4j/kanban-vision-api-kt)](https://github.com/agnaldo4j/kanban-vision-api-kt/commits/main)

[![Kotlin](https://img.shields.io/badge/kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Ktor](https://img.shields.io/badge/ktor-3.5.1-087CFA?logo=ktor&logoColor=white)](https://ktor.io/)
[![Gradle](https://img.shields.io/badge/gradle-9.6.1-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![Java](https://img.shields.io/badge/java-25%20LTS-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![GraalVM](https://img.shields.io/badge/graalvm-native--image%20in%20production-EE8100)](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/GraalVM)
[![Arrow](https://img.shields.io/badge/arrow--kt-2.2.3-E91E63)](https://arrow-kt.io/)
[![Koin](https://img.shields.io/badge/koin-4.2.2-F88900)](https://insert-koin.io/)
[![Kotest Property](https://img.shields.io/badge/kotest--property-6.2.2-2D5BE3)](https://kotest.io/docs/proptest/property-based-testing.html)
[![PITest](https://img.shields.io/badge/pitest-mutation%20testing-CC0000)](https://pitest.org/)
[![Detekt](https://img.shields.io/badge/detekt-2.0.0--alpha.5-9146FF)](https://detekt.dev/)
[![Konsist](https://img.shields.io/badge/konsist-architecture%20fitness-4B9E4B)](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Architecture-Fitness-Functions)
[![SBOM](https://img.shields.io/badge/SBOM-CycloneDX-FF6D00)](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Security-Supply-Chain)
[![SCA](https://img.shields.io/badge/SCA-osv--scanner-4285F4)](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Security-Supply-Chain)
[![k6](https://img.shields.io/badge/k6-load%20testing-7D64FF)](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Performance-Load-Testing)
[![OpenTelemetry](https://img.shields.io/badge/opentelemetry-2.29.0-425CC7?logo=opentelemetry&logoColor=white)](https://opentelemetry.io/)
[![Kubernetes](https://img.shields.io/badge/kubernetes-manifests-326CE5?logo=kubernetes&logoColor=white)](k8s/)
[![Docker](https://img.shields.io/badge/docker-ghcr.io-2496ED?logo=docker&logoColor=white)](https://github.com/agnaldo4j/kanban-vision-api-kt/pkgs/container/kanban-vision-api-kt)

# Kanban Vision API

> Organization-scoped Kanban flow simulator exposed as a versioned REST API — built with Kotlin, Clean Architecture and Arrow-kt on Ktor, and shipped to production as a GraalVM Native Image.

---

## Features

- **Kanban flow simulation** — model boards, steps, cards and workers (abilities, seniority, WIP limits); run day-by-day simulations with per-day decisions, flow metrics and cumulative flow (CFD)
- **Organization-scoped** — boards and scenarios are isolated per organization
- **Versioned REST API** — business routes under `/api/v1`, documented with OpenAPI 3 + Swagger UI (ADR-0022)
- **Clean Architecture** — pure domain layer split by bounded context into three Gradle modules (`domain-simulation` → `domain-kanban` → `domain-common`, dependency direction enforced by a fitness function), ports-and-adapters, strict dependency rule enforced by Konsist
- **Functional error handling** — `Either<DomainError, T>` via Arrow-kt throughout
- **Production-ready** — JWT auth, distributed rate limiting over Redis (ADR-0041/0042), Prometheus metrics, Grafana dashboards, OTel SDK traces; runs in production as a GraalVM Native Image (≈9× faster startup, −79% memory — ADR-0032)
- **In-cluster observability** — self-contained Kubernetes manifests for the whole stack: Prometheus (`kubernetes_sd` scrape) → Alertmanager + alert-sink → Grafana, no Operator/CRD (ADR-0043), alongside the docker-compose stack
- **Quality gates** — Detekt + KtLint + JaCoCo ≥ 98% + PITest mutation testing + Konsist architecture fitness (incl. class-cycle detection) + a semantic `config-lint` check (amtool/promtool over the observability configs, advisory) run on every PR
- **Supply chain security** — CycloneDX SBOM (over the app **and** the migration binary's classpath) + osv-scanner CVE gate blocking every image build; both native binaries are smoke-tested against ephemeral Postgres
- **Property-based testing** — Kotest generators validate domain invariants with randomized inputs

---

## Quick Start

```bash
# Clone
git clone https://github.com/agnaldo4j/kanban-vision-api-kt.git
cd kanban-vision-api-kt

# Run full stack (API + PostgreSQL + Prometheus + Grafana)
GRAFANA_ADMIN_PASSWORD=admin docker compose up --build

# Dev mode — enables POST /auth/token (JWT_DEV_MODE) and /swagger (ENABLE_SWAGGER)
JWT_DEV_MODE=true ENABLE_SWAGGER=true GRAFANA_ADMIN_PASSWORD=admin docker compose up --build
```

| Service | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger (requires `ENABLE_SWAGGER=true` — off by default) |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |

> **Note:** `--build` compiles the production **GraalVM Native Image** (ADR-0032) — the first build takes several minutes and needs a Docker VM with ≥ 10 GB RAM. For a fast JVM dev loop (no native build), start only PostgreSQL and run the app on the JVM — it still needs a reachable database, since it runs Flyway migrations and opens a HikariCP pool at startup:
>
> ```bash
> GRAFANA_ADMIN_PASSWORD=admin docker compose up -d postgres
> JWT_DEV_MODE=true ./gradlew :http_api:run     # connects to localhost:5432; override via DATABASE_URL
> ```
>
> Dev and tests run on the JVM (`./gradlew :http_api:buildFatJar` for a runnable JAR); only the container image is native.

> Java 25 (LTS) is the single JDK for daemon, build and runtime (ADR-0024). See [Development Guide](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Development-Guide).

---

## Documentation

| Page | Description |
|---|---|
| [Architecture](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Architecture) | Hexagonal arch, C4 diagrams, class diagrams (modules/domain) and sequence diagrams |
| [Development Guide](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Development-Guide) | Local setup, testing, quality gates, troubleshooting |
| [API Reference](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/API-Reference) | Endpoints, JWT auth, OpenAPI |
| [JVM](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/JVM) | Java 25 single-JDK model, convention plugin, config cache, fat JAR, JIT |
| [GraalVM](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/GraalVM) | Native Image in production, reachability metadata, AOT trade-offs |
| [Observability](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Observability) | Prometheus + Alertmanager + Grafana (compose **and** in-cluster k8s, ADR-0043), OTel SDK traces, structured logs |
| [Operations](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Operations) | Native Docker image, Kubernetes manifests (app + observability stack), CI/CD, env vars |
| [Quality Analysis](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Quality-Analysis) | 22-skill scorecard (9.35/10, transparent mean), gap roadmap |
| [Security Supply Chain](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Security-Supply-Chain) | CycloneDX SBOM, osv-scanner CVE gate, exception policy, red-gate runbook |
| [Architecture Fitness Functions](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Architecture-Fitness-Functions) | Konsist rules enforcing the hexagonal architecture in CI |
| [Performance Load Testing](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Performance-Load-Testing) | k6 journey, profiles, and baselines (JVM JIT → GraalVM → Native, ~2,300 req/s in production) |

---

## Contributing

See [Explicit Policies](docs/politicas-explicitas.md) for board protocol, quality gates, ADR rules and branch conventions.

All changes go through PRs — WIP limit 1, `./gradlew testAll` must be green before opening a PR.

---

## License

MIT — see [LICENSE](LICENSE).
