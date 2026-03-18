[![CI](https://github.com/agnaldo4j/kanban-vision-api-kt/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/agnaldo4j/kanban-vision-api-kt/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-21-orange.svg)](https://adoptium.net/)
[![Kotlin](https://img.shields.io/badge/kotlin-2.x-purple.svg)](https://kotlinlang.org/)

# Kanban Vision API

> Multi-tenant Kanban flow simulator via REST API — built with Kotlin, Clean Architecture, Arrow-kt and Ktor.

---

## Features

- **Kanban simulation** — model boards, columns and cards; run day-by-day flow simulations
- **Multi-tenant** — tenant-scoped boards and scenarios
- **Clean Architecture** — pure domain layer, ports-and-adapters, strict dependency rule
- **Functional error handling** — `Either<DomainError, T>` via Arrow-kt throughout
- **Production-ready** — JWT auth, rate limiting, Prometheus metrics, Grafana dashboards, OTel traces
- **Quality gates** — Detekt + KtLint + JaCoCo ≥ 95% enforced on every PR

---

## Quick Start

```bash
# Clone
git clone https://github.com/agnaldo4j/kanban-vision-api-kt.git
cd kanban-vision-api-kt

# Run full stack (API + PostgreSQL + Prometheus + Grafana)
GRAFANA_ADMIN_PASSWORD=admin docker compose up --build

# Dev mode — enables POST /auth/token for local JWT generation
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose up --build
```

| Service | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |

> Java 21 required for local Gradle builds. See [Development Guide](../../wiki/Development-Guide).

---

## Documentation

| Page | Description |
|---|---|
| [Architecture](../../wiki/Architecture) | Hexagonal arch, C4 diagrams, modules, CQS, error handling |
| [Development Guide](../../wiki/Development-Guide) | Local setup, testing, quality gates, troubleshooting |
| [API Reference](../../wiki/API-Reference) | Endpoints, JWT auth, OpenAPI |
| [Observability](../../wiki/Observability) | Prometheus, Grafana dashboards, OTel Agent, structured logs |
| [Operations](../../wiki/Operations) | Docker, Kubernetes manifests, CI/CD, env vars |
| [Quality Analysis](../../wiki/Quality-Analysis) | 18-skill scorecard (8.5/10), gap roadmap |

---

## Contributing

See [Explicit Policies](docs/politicas-explicitas.md) for board protocol, quality gates, ADR rules and branch conventions.

All changes go through PRs — WIP limit 1, `./gradlew testAll` must be green before opening a PR.

---

## License

MIT — see [LICENSE](LICENSE).
