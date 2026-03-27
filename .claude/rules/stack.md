# Stack & CI/CD

## Tech Stack

| Concern | Library |
|---|---|
| HTTP | Ktor 3.4.1 (Netty engine) |
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
| Functional types | Arrow-kt 2.0.1 (Either, Raise, zipOrAccumulate) |
| Testing | JUnit Jupiter 6.0.3 + MockK 1.14.9 |
| Mutation testing | PITest 1.15.0 (STRONGER mutators, `domain/` SimulationEngine focus) |
| OpenAPI | ktor-openapi 5.6.0 + ktor-swagger-ui 5.6.0 |
| Static analysis | Detekt 1.23.8 |
| Formatting | KtLint 1.5.0 |
| Coverage | JaCoCo (≥ 96% per module) |
| Containerisation | Docker multi-stage (`eclipse-temurin:21-jre`) + docker-compose |
| Kubernetes | Manifests in `k8s/` (Namespace, ConfigMap, Deployment, Service, Ingress, HPA, PDB) |
| Observability | Prometheus 2.54 + Grafana 11.3 + OTel Java Agent 2.14.0 (API 1.47.0) |
| Java | Java 21 (Gradle 8.13 incompatible with newer versions) |
| Kotlin | 2.3.0 |

## CI/CD — GitHub Actions (`.github/workflows/ci.yml`)

**Job `quality`** — every PR and push to `main`:
1. Setup Java 21 (Temurin)
2. `./gradlew testAll` — Detekt + KtLint + tests + JaCoCo gate
3. `./gradlew :domain:pitest` — PITest mutation testing (mandatory in CI; locally opt-in since not in `check`)
4. Upload artifacts (14 days): test reports, Detekt, JaCoCo, PITest HTML
5. Post PR comments: Detekt summary + JaCoCo coverage diff
6. Upload coverage to Codecov

**Job `build`** — runs after `quality`:

| Trigger | Action |
|---|---|
| Pull Request | Build Docker image only (no push) — validates Dockerfile |
| Push to `main` | Build + push: `sha-<short>` + `latest` |
| Tag `v*.*.*` | Build + push: `sha-<short>` + `v<version>` + `latest` |

Registry: `ghcr.io/agnaldo4j/kanban-vision-api-kt`