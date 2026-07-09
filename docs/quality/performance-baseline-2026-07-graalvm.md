# Performance Baseline — July 2026 · GraalVM Phase 1 (GAP-AY, ADR-0030)

Comparative measurement of the production runtime swap: `eclipse-temurin:25-jre-alpine` → **Oracle GraalVM JDK 25 (Graal JIT)**, Phase 1 of ADR-0030. BOTH images were measured **in the same session, in the same environment and with the same protocol** — the absolute numbers differ from the 2026-07-05 baseline (machine in a different condition); the valid comparison is column-by-column in this table, not against the previous document (ADR-0027 policy: same environment).

> *Language note: translated to English on 2026-07-09; all measurements are unchanged from the original snapshot.*

## Environment

| Item | Value |
|---|---|
| Hardware | Apple M2 Pro, 12 cores, 32 GB RAM |
| OS | macOS 27.0 · Docker 29.6.1 (Desktop) |
| Stack | Full `docker compose` (app + PostgreSQL 16 + Tempo + Prometheus + Grafana), OTel agent 2.29.0 active |
| App A (control) | fat JAR, `eclipse-temurin:25-jre-alpine`, LOG_FORMAT=json |
| App B (Phase 1) | fat JAR, `container-registry.oracle.com/graalvm/jdk:25` (Graal JIT), LOG_FORMAT=json — build resolved by the tag **at measurement time**: Oracle GraalVM 25.0.3+9.1 (`java -version`); the `:25` tag floats by patch |
| Tool | k6 v2.1.0, script `load/simulation-journey.js`, `baseline` profile |
| Date | 2026-07-07 |

## Load

Same journey and profile as the 2026-07 baseline: each iteration = one distinct client (unique `X-Forwarded-For`); 1× create → 5× run day → days → snapshot → cfd → list (≈10 requests). `baseline` profile: ramp 0→5 VUs (30s) → 5→20 (1m) → 20 constant (2m) → 20→0 (30s). Total 4m.

## Comparative results

| Metric | Temurin 25 (JRE) | Oracle GraalVM 25 (JIT) | Δ |
|---|---|---|---|
| Requests | 210,471 (876.9 req/s) | 235,231 (**980.0 req/s**) | **+11.8%** |
| Complete journeys | 21,047 (87.7/s) | 23,523 (98.0/s) | +11.8% |
| HTTP failures | 0.00% | **0.00%** | = |
| Overall p95 | 60.47 ms | **47.10 ms** | **−22.1%** |
| Startup (`Application started` log) | 1.564 s | 1.684 s | ≈ tie |
| Warm memory (post-smoke) | 371.5 MiB | 379.8 MiB | ≈ tie |
| Memory post-baseline (load peak) | 464.9 MiB | 515.2 MiB | +10.8% |
| Image size | 297 MB | 820 MB | +176% |

### Latency per endpoint (p95, Temurin → GraalVM)

| Endpoint | Temurin p95 | GraalVM p95 | Δ | k6 threshold |
|---|---|---|---|---|
| `POST /api/v1/simulations` (create) | 22.91 ms | **20.34 ms** | −11.2% | < 300 ms ✅ |
| `POST .../run` (run_day) | 38.01 ms | **33.02 ms** | −13.1% | < 500 ms ✅ |
| `GET .../days/{d}/snapshot` | 11.60 ms | **10.52 ms** | −9.3% | < 300 ms ✅ |
| `GET .../cfd` | 11.72 ms | **10.58 ms** | −9.7% | < 300 ms ✅ |
| `GET /api/v1/simulations?organizationId=` (list) | 103.78 ms | **73.50 ms** | −29.2% | < 300 ms ✅ |

## Reading the results

- **Graal JIT delivers the expected Phase 1 gain**: throughput +11.8% and overall p95 −22%, with the largest gain exactly on the costliest endpoint (`list`, −29%). Zero failures on both runs.
- **Startup and warm memory are equivalent** — expected: the startup/memory gains are a Phase 2 (Native Image) promise, not JIT mode's.
- **Trade-offs**: 820 MB image vs 297 MB (full Oracle Linux JDK vs alpine JRE) and +10.8% memory under peak. Accepted in Phase 1; Phase 2 (Native Image) reverses both dramatically.
- **Memory vs k8s limits (512Mi)**: memory was measured **without a cgroup limit** (Docker Desktop, no `--memory`) — the heap grows freely and the number doesn't map 1:1 to the pod. Under the Deployment's 512Mi limit the JVM auto-sizes the heap from the cgroup (`MaxRAMPercentage`), identical behaviour on both runtimes — unlimited Temurin also reached 464.9 MiB. The pod memory budget is a pre-existing rollout concern, not this swap's; monitor `container_memory_working_set_bytes` on the first Phase 1 deploy.
- The compose healthcheck (`wget` inside the container) and the journey smoke (100% checks) pass on the new base; `useradd` uid 1000 preserved for the k8s `securityContext`.

## How to reproduce

```bash
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose up --build -d
docker compose exec -T postgres psql -U kanban -d kanbanvision -c \
  "INSERT INTO organizations (id, name) VALUES ('11111111-1111-4111-8111-111111111111', 'k6-load-org') ON CONFLICT (id) DO NOTHING;"
k6 run -e PROFILE=baseline load/simulation-journey.js
# Temurin control: build the previous revision's Dockerfile (git show <rev>:Dockerfile),
# retag as kanban-vision-api:local and recreate only the app service — same session, same stack.
```

> Policy (ADR-0027): k6 thresholds are an executable signal, not a PR gate. This document is an immutable snapshot; a new measurement = a new file. Previous baseline: `performance-baseline-2026-07.md`.
