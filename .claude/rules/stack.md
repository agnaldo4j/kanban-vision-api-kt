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
| DB Migrations | Flyway 12.11.0 |
| Production DB | PostgreSQL (JDBC driver `org.postgresql:postgresql` 42.7.13) |
| Test DB | Embedded PostgreSQL (zonky) |
| Metrics | Micrometer + Prometheus (`/metrics`) |
| Logging | SLF4J + Logback + logstash-logback-encoder (JSON via `LOG_FORMAT=json`) |
| Functional types | Arrow-kt 2.2.3 (Either, Raise, zipOrAccumulate) |
| Testing | JUnit Jupiter 6.1.2 + MockK 1.14.11 |
| Mutation testing | PITest core 1.25.3 / Gradle plugin 1.19.0 (STRONGER; gates: `domain-common` 90% · `domain-kanban` 78% · `domain-simulation` 73% · `usecases` 55% · `sql_persistence` 65% · `http_api` 45% (plugins/adapters/events)) |
| OpenAPI | ktor-openapi 5.7.0 + ktor-swagger-ui 5.7.0 |
| Static analysis | Detekt 2.0.0-alpha.5 (`dev.detekt` — ADR-0024; jvmTarget follows the toolchain) |
| Architecture fitness | Konsist 0.17.3 + JUnit — módulo test-only `architecture/` (ADR-0026); 19 fitness tests, incl. o grafo de `project` deps `simulation → kanban → common` (`ProjectDependencyGraphTest`, ADR-0038); roda no `testAll` |
| Load testing | k6 2.x — scripts em `load/`, baseline p95 em `docs/quality/` (versão exata da medição registrada lá; ADR-0027); workflow manual, **nunca gate de PR**. Sinal agendado de regressão (`perf-regression.yml`, cron semanal) compara CI-vs-referência-de-CI com tolerância larga — tripwire não-bloqueante (ADR-0039) |
| SBOM | CycloneDX Gradle plugin 3.3.0 (`org.cyclonedx.bom`, root; runtimeClasspath only — ADR-0025) |
| SCA | osv-scanner v2 (action `google/osv-scanner-action@v2.3.8`) — blocking gate; exceptions in `osv-scanner.toml` |
| Formatting | KtLint 1.5.0 |
| Coverage | JaCoCo (≥ 98% per module — ADR-0029) |
| Containerisation | Docker multi-stage: GraalVM **Native Image** (binários app + migração — ADR-0032) sobre Oracle Linux 9 slim + docker-compose. Dev/testes seguem JVM (`buildFatJar` disponível) |
| Kubernetes | Manifests in `k8s/` (Namespace, ConfigMap, Deployment, Service, Ingress, HPA, PDB) |
| Observability | Prometheus 2.54 + Grafana 11.3 + OTel SDK/instrumentação de biblioteca 2.29.0 (API 1.64.0, sem javaagent — ADR-0031) |
| Java | Java 25 LTS (Gradle 9.6.1 wrapper; Foojay resolver auto-provisions toolchain) |
| Kotlin | 2.4.10 |

## CI/CD — GitHub Actions (`.github/workflows/ci.yml`)

**Job `quality`** — every PR and push to `main`:
1. Setup Java 25 (Temurin)
2. `./gradlew testAll` — Detekt + KtLint + tests + JaCoCo gate
3. `./gradlew pitestAll` — PITest mutation testing across the 6 product modules (`domain-common`/`domain-kanban`/`domain-simulation`/`usecases`/`sql_persistence`/`http_api`; mandatory in CI, locally opt-in since not in `check`)
4. Upload artifacts (14 days): test reports, Detekt, JaCoCo, PITest HTML
5. Post PR comments: Detekt summary + JaCoCo coverage diff
6. Upload coverage to Codecov

**Job `supply-chain`** — every PR and push to `main`, parallel to `quality` (ADR-0025):
1. `./gradlew cyclonedxBom` — aggregate SBOM (runtimeClasspath of all modules) at `build/reports/cyclonedx/bom.json`
2. Upload SBOM artifact (14 days)
3. osv-scanner scans the SBOM against OSV.dev — **fails the job on any known CVE**; documented exceptions only via `osv-scanner.toml`
4. Post PR comment: Supply Chain Report (component count, active exceptions, findings table)

**Job `pr-size`** — every PR (skips forks), parallel to `quality` (GAP-BH):
1. Count changed lines (`additions + deletions`) via `gh api .../pulls/{n}/files` — no checkout
2. Exclude committed GraalVM reachability metadata (`**/META-INF/native-image/**`) from the count
3. Post PR comment: PR Size Report (counted vs 400 threshold) — **non-blocking**, never fails the build (the ≤400-line limit is a heuristic, `politicas §6`)

**Job `flow-metrics`** — every PR (skips forks), parallel to `quality` (GAP-BI):
1. Run `scripts/flow-metrics.sh` — rolling engineering-flow snapshot (PR cycle/lead time, cadence, PR-size, board WIP) over recent merged PRs
2. Post PR comment: Flow Metrics Report — **non-blocking** (never fails the build)
3. WIP line needs a PAT with `read:project` in secret `FLOW_PROJECT_TOKEN`; without it the script degrades WIP to "unavailable" (cycle/lead/cadence/size still reported)

**Job `build`** — runs after `quality` + `supply-chain`:

| Trigger | Action |
|---|---|
| Pull Request | Build Docker image only (no push) — validates Dockerfile, then **smoke test** |
| Push to `main` | Build + push: `sha-<short>` + `latest` |
| Tag `v*.*.*` | Build + push: `sha-<short>` + `v<version>` + `latest` |

Registry: `ghcr.io/agnaldo4j/kanban-vision-api-kt`

On PRs the built **native image** is booted against an ephemeral Postgres and probed
(readiness + liveness + a no-token `401` that serialises `DomainErrorResponse` in the Native Image —
reachability metadata, cf. GAP-BM). Results post as a sticky **Smoke Test Report** PR comment; any
failed probe **fails the job** (blocking gate — the same class of runtime bug that stayed invisible
until GAP-AR / PR #225).