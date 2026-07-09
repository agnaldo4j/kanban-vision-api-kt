# Performance Baseline — July 2026 · Native Image in production (GAP-BB, ADR-0032)

Comparative measurement of the production-artifact swap: fat JAR on the GraalVM JDK (JIT, Phase 1) → **Native Image binary (AOT)**. BOTH images were measured in containers in the same session, with the **DB wiped before each run** (`docker compose down -v`) and the same Docker VM (15.6 GB — reconfigured for this session; absolute numbers don't compare to previous baselines, only column-by-column here — ADR-0027 policy).

> *Language note: translated to English on 2026-07-09; all measurements are unchanged from the original snapshot.*

## Environment

| Item | Value |
|---|---|
| Hardware | Apple M2 Pro, 12 cores, 32 GB RAM · Docker Desktop VM 15.6 GB |
| OS | macOS 27.0 · Docker 29.6.1 |
| Stack | Full `docker compose`, DB wiped before each run |
| App B (control) | fat JAR on `container-registry.oracle.com/graalvm/jdk:25` (JIT, Phase 1/ADR-0030) |
| App A (GAP-BB) | `native-image` binary (app + migration binary) on Oracle Linux 9 slim |
| Tool | k6 v2.1.0, `load/simulation-journey.js`, `baseline` profile |
| Date | 2026-07-08 |

## Comparative results

| Metric | JIT (fat JAR) | Native Image | Δ |
|---|---|---|---|
| Requests | 595,601 (2,481.3 req/s) | 553,361 (2,305.3 req/s) | −7.1% |
| HTTP failures | 0.00% | **0.00%** | = |
| Overall p95 | 15.83 ms | 16.73 ms | +5.7% |
| Startup (`Application started` log) | 1.077 s | **0.120 s** | **−89% (9×)** |
| Memory post-boot | 354.8 MiB | **73.6 MiB** | **−79%** |
| Memory post-smoke | 422.4 MiB | **81.7 MiB** | **−81%** |
| Memory post-baseline | 640.7 MiB | **41.5 MiB** | **−94%** |
| Image size | 820 MB | **326 MB** | **−60%** |

### Latency per endpoint (p95, JIT → Native)

| Endpoint | JIT p95 | Native p95 | Δ |
|---|---|---|---|
| create | 9.43 ms | 10.29 ms | +9.1% |
| run_day | 14.36 ms | 15.73 ms | +9.5% |
| snapshot | 4.95 ms | 6.04 ms | +22.0% |
| cfd | 5.09 ms | 6.18 ms | +21.4% |
| list | 27.21 ms | 25.88 ms | −4.9% |

## Reading the results

- **The trade-off predicted by ADR-0030/0032 held in both directions**: the JIT keeps ~7% more peak throughput; the native binary delivers a **9× faster startup, a fraction of the memory (−79% to −94%) and a 60% smaller image** — exactly the gains that motivated Phase 2 (K8s: probes, HPA, density, cold start).
- Zero failures on both runs (1.1 million requests combined); k6 journey 100% on both.
- **Migrations proven on a virgin DB** on both native paths: app startup and the dedicated `kanban-vision-migrate` binary (k8s Job, ADR-0013) — via `FLYWAY_LOCATIONS=filesystem:` (Flyway's ClassPathScanner can't read resources from the binary).
- Latent bug fixed in passing: `MIGRATION_POOL_SIZE=1` blew the timeout — Flyway 12 uses two concurrent connections; the k8s Job would have failed on the JVM too.
- **Known limitation (follow-up card)**: in the native binary the event loop retains the OTel context between requests — `KtorServerTelemetry` suppresses new SERVER spans and chains requests into one trace under load. JDBC/manual spans and log↔trace correlation work; k8s production has traces off by default (`OTEL_TRACES_EXPORTER=none`). Upstream investigation recorded on the board.
- Building the image needs ~7 GB RAM (native-image): CI ubuntu-latest (16 GB) fits; a local Docker Desktop needs a VM ≥ ~10 GB.

## How to reproduce

```bash
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose down -v   # wipe the DB
docker build -t kanban-vision-api:local .                               # compiles the 2 binaries
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose up -d --no-build
docker compose exec -T postgres psql -U kanban -d kanbanvision -c \
  "INSERT INTO organizations (id, name) VALUES ('11111111-1111-4111-8111-111111111111', 'k6-load-org') ON CONFLICT (id) DO NOTHING;"
k6 run load/simulation-journey.js                     # smoke (100% checks)
k6 run -e PROFILE=baseline load/simulation-journey.js # official measurement
# JIT control: build the previous revision via git worktree and repeat with down -v first.
```

> Policy (ADR-0027): immutable snapshot; a new measurement = a new file. Previous baselines: `performance-baseline-2026-07.md`, `performance-baseline-2026-07-graalvm.md`, `performance-baseline-2026-07-otel-sdk.md`.
