# Performance Baseline — July 2026 · Traces without javaagent (GAP-AZ, ADR-0031)

Comparative measurement of removing the OTel Java Agent: traces moved to the OTel SDK + build-time library instrumentation (ADR-0031). BOTH images run Oracle GraalVM JDK 25 (Phase 1, ADR-0030) and were measured **in the same session, with the DB WIPED before each run** (`docker compose down -v`) — the `list` endpoint grows with accumulated volume and contaminates any comparison without a reset (the lesson from this measurement).

> *Language note: translated to English on 2026-07-09; all measurements are unchanged from the original snapshot.*

## Environment

| Item | Value |
|---|---|
| Hardware | Apple M2 Pro, 12 cores, 32 GB RAM |
| OS | macOS 27.0 · Docker 29.6.1 (Desktop) |
| Stack | Full `docker compose`, DB wiped before each run |
| App B (control) | fat JAR + OTel javaagent 2.29.0 in the ENTRYPOINT (Phase 1 commit) |
| App A (GAP-AZ) | fat JAR with library instrumentation 2.29.0-alpha (ktor-3.0, jdbc, logback-mdc) + SDK autoconfigure 1.63.0 — **no javaagent** |
| Tool | k6 v2.1.0, `load/simulation-journey.js`, `baseline` profile |
| Date | 2026-07-07 |

## Comparative results

| Metric | With javaagent | Without javaagent (SDK) | Δ |
|---|---|---|---|
| Requests | 331,521 (1,381.0 req/s) | 365,861 (**1,524.3 req/s**) | **+10.4%** |
| HTTP failures | 0.00% | **0.00%** | = |
| Overall p95 | 25.99 ms | **24.10 ms** | −7.3% |
| Startup (`Application started` log) | 1.97 s | 1.825 s | −0.15 s |
| Memory post-smoke | 365 MiB | **277 MiB** | **−24%** |
| Memory post-baseline (peak) | 738.8 MiB | **354.9 MiB** | **−52%** |

### Latency per endpoint (p95, agent → SDK)

| Endpoint | Agent p95 | SDK p95 | Δ |
|---|---|---|---|
| create | 16.12 ms | **13.84 ms** | −14.1% |
| run_day | 25.97 ms | **22.15 ms** | −14.7% |
| snapshot | 8.85 ms | **7.34 ms** | −17.1% |
| cfd | 8.99 ms | **7.34 ms** | −18.4% |
| list | 35.80 ms | 35.75 ms | ≈ |

## Signal parity verified (ADR-0031 Confirmation)

In a single journey trace (Tempo, local compose): the HTTP server span (scope `io.opentelemetry.ktor-3.0`) + the manual `simulation.run_day` span (scope `kanban-vision-api`) + JDBC `INSERT/SELECT` spans (scope `io.opentelemetry.jdbc`) — all nested; `trace_id` and `span_id` present in the JSON logs and resolving to the correct trace in Tempo.

## Reading the results

- **Removing the agent improved everything**: +10.4% throughput, lower p95 on every business endpoint and **half the memory under peak** — the agent's real cost was in memory (bytecode instrumentation + internal state), not startup (~0.15 s).
- Bundled bug fix: the JSON logs' `traceId`/`spanId` (camelCase) fields were **never populated** — the real OTel MDC keys are `trace_id`/`span_id` (snake_case); log↔trace correlation only started actually working in this delivery.
- **Pitfall discovered**: running the new fat JAR WITH the javaagent breaks boot (`DuplicatePluginException: OpenTelemetry` — the agent and `KtorServerTelemetry` collide). There is no hybrid mode: agent OR library.
- **Measurement protocol corrected**: always `docker compose down -v` before each run — accumulated simulations penalize `list` and skew the overall p95 across runs.

## How to reproduce

```bash
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose down -v   # wipe the DB
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose up --build -d
docker compose exec -T postgres psql -U kanban -d kanbanvision -c \
  "INSERT INTO organizations (id, name) VALUES ('11111111-1111-4111-8111-111111111111', 'k6-load-org') ON CONFLICT (id) DO NOTHING;"
k6 run load/simulation-journey.js                     # smoke (100% checks)
k6 run -e PROFILE=baseline load/simulation-journey.js # official measurement
# Control: build the previous revision via git worktree (the old Dockerfile with the new code
# does NOT boot — DuplicatePluginException) and repeat with down -v first.
```

> Policy (ADR-0027): k6 thresholds are an executable signal, not a PR gate. Immutable snapshot; a new measurement = a new file. Previous baselines: `performance-baseline-2026-07.md` (Temurin+agent), `performance-baseline-2026-07-graalvm.md` (Phase 1).
