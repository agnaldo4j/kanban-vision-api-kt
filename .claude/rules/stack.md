# Stack & CI/CD

## Tech Stack

| Concern | Library |
|---|---|
| HTTP | Ktor 3.5.1 (Netty engine) |
| Authentication | JWT Bearer (`ktor-server-auth-jwt`) |
| Rate Limiting | `ktor-server-rate-limit` (100 req/min per IP) |
| Serialization | kotlinx.serialization |
| DI | Koin 4.2.2 |
| JDBC | Raw JDBC + HikariCP 7.1.0 |
| DB Migrations | Flyway 12.10.0 |
| Production DB | PostgreSQL (JDBC driver `org.postgresql:postgresql` 42.7.12) |
| Test DB | Embedded PostgreSQL (zonky) |
| Metrics | Micrometer + Prometheus (`/metrics`) |
| Logging | SLF4J + Logback + logstash-logback-encoder (JSON via `LOG_FORMAT=json`) |
| Functional types | Arrow-kt 2.2.3 (Either, Raise, zipOrAccumulate) |
| Testing | JUnit Jupiter 6.1.1 + MockK 1.14.11 |
| Mutation testing | PITest core 1.25.3 / Gradle plugin 1.19.0 (STRONGER mutators, `domain/` SimulationEngine focus) |
| OpenAPI | ktor-openapi 5.7.0 + ktor-swagger-ui 5.7.0 |
| Static analysis | Detekt 2.0.0-alpha.5 (`dev.detekt` — ADR-0024; jvmTarget follows the toolchain) |
| Formatting | KtLint 1.5.0 |
| Coverage | JaCoCo (≥ 97% per module) |
| Containerisation | Docker multi-stage (`eclipse-temurin:25-jre`) + docker-compose |
| Kubernetes | Manifests in `k8s/` (Namespace, ConfigMap, Deployment, Service, Ingress, HPA, PDB) |
| Observability | Prometheus 2.54 + Grafana 11.3 + OTel Java Agent 2.14.0 (API 1.63.0) |
| Java | Java 25 LTS (Gradle 9.6.1 wrapper; Foojay resolver auto-provisions toolchain) |
| Kotlin | 2.4.0 |

## CI/CD — GitHub Actions (`.github/workflows/ci.yml`)

**Job `quality`** — every PR and push to `main`:
1. Setup Java 25 (Temurin)
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