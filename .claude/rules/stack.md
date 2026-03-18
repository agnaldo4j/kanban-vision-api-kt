# Stack & CI/CD

## Tech Stack

| Concern | Library |
|---|---|
| HTTP | Ktor 3.1.2 (Netty engine) |
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
| Functional types | Arrow-kt (Either, Raise, zipOrAccumulate) |
| Testing | JUnit 5.11.4 + MockK 1.14.2 |
| OpenAPI | ktor-openapi 5.6.0 + ktor-swagger-ui 5.6.0 |
| Static analysis | Detekt 1.23.7 |
| Formatting | KtLint 1.5.0 |
| Coverage | JaCoCo (≥ 95% per module) |
| Containerisation | Docker multi-stage (`eclipse-temurin:21-jre`) + docker-compose |
| Kubernetes | Manifests in `k8s/` (Namespace, ConfigMap, Deployment, Service, Ingress, HPA, PDB) |
| Observability | Prometheus 2.54 + Grafana 11.3 + OTel Java Agent |
| Java | Java 21 (Gradle 8.13 incompatible with newer versions) |

## CI/CD — GitHub Actions (`.github/workflows/ci.yml`)

**Job `quality`** — every PR and push to `main`:
1. Setup Java 21 (Temurin)
2. `./gradlew testAll` — Detekt + KtLint + tests + JaCoCo gate
3. Upload artifacts (14 days): test reports, Detekt, JaCoCo
4. Post PR comments: Detekt summary + JaCoCo coverage diff

**Job `build`** — runs after `quality`:

| Trigger | Action |
|---|---|
| Pull Request | Build Docker image only (no push) — validates Dockerfile |
| Push to `main` | Build + push: `sha-<short>` + `latest` |
| Tag `v*.*.*` | Build + push: `sha-<short>` + `v<version>` + `latest` |

Registry: `ghcr.io/agnaldo4j/kanban-vision-api-kt`