# Performance Baseline — July 2026 (GAP-AR, ADR-0027)

The project's first performance-efficiency measurement (the only ISO/IEC 25010 characteristic without a measurement until then). Numbers measured **locally against docker compose** — future comparisons must use the same environment and parameters.

> *Language note: translated to English on 2026-07-09; all measurements are unchanged from the original snapshot.*

## Environment

| Item | Value |
|---|---|
| Hardware | Apple M2 Pro, 12 cores, 32 GB RAM |
| OS | macOS 27.0 · Docker 29.3.1 (Desktop) |
| Stack | Full `docker compose` (app + PostgreSQL 16 + Tempo + Prometheus + Grafana), OTel agent 2.29.0 active |
| App | fat JAR, `eclipse-temurin:25-jre`, LOG_FORMAT=json |
| Tool | k6 v2.1.0, script `load/simulation-journey.js`, `baseline` profile |
| Date | 2026-07-05 |

## Load

- **Journey per iteration** (each iteration = one distinct client via a unique `X-Forwarded-For`, to measure the server and not the 100 req/min/client rate limiter): issuing the token doesn't count; 1× create simulation → 5× run day → days → snapshot → cfd → list (≈10 requests).
- **`baseline` profile**: ramp 0→5 VUs (30s) → 5→20 (1m) → 20 constant (2m) → 20→0 (30s). Total 4m.

## Results

| Metric | Value |
|---|---|
| Requests | 394,681 (**1,644 req/s**) |
| Complete journeys | 39,468 (164/s) |
| HTTP failures | **0.00%** |
| Overall p95 | **22.09 ms** (avg 8.79 ms · p90 17.94 ms · max 297 ms) |

### Latency per endpoint (p95 / median / avg)

| Endpoint | p95 | med | avg | k6 threshold |
|---|---|---|---|---|
| `POST /api/v1/simulations` (create) | **13.83 ms** | 5.99 ms | 6.61 ms | < 300 ms ✅ |
| `POST .../run` (run_day) | **21.53 ms** | 10.45 ms | 10.92 ms | < 500 ms ✅ |
| `GET .../days/{d}/snapshot` | **7.49 ms** | 2.84 ms | 3.32 ms | < 300 ms ✅ |
| `GET .../cfd` | **7.70 ms** | 2.91 ms | 3.42 ms | < 300 ms ✅ |
| `GET /api/v1/simulations?organizationId=` (list) | **31.95 ms** | 16.18 ms | 16.55 ms | < 300 ms ✅ |

Note: `list` is the costliest endpoint and grows with accumulated volume (39k simulations created during the test) — the first candidate for pagination if latency rises in future measurements.

## How to reproduce

```bash
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose up --build -d
docker compose exec -T postgres psql -U kanban -d kanbanvision -c \
  "INSERT INTO organizations (id, name) VALUES ('11111111-1111-4111-8111-111111111111', 'k6-load-org') ON CONFLICT (id) DO NOTHING;"
k6 run -e PROFILE=baseline load/simulation-journey.js
```

## Measurement context (bugs found along the way)

This first measurement required fixing the fat-JAR runtime, which was broken in local production without any gate catching it (CI builds the image but never runs it): OTel agent 2.14.0 incompatible with logback 1.5.37; the programmatic `embeddedServer` didn't read `application.conf`; Flyway with no plugins because `META-INF/services` wasn't merged (KTOR-8987); mute logback (logback 1.5.x removed `<if>`/Janino); Koin with no `MeterRegistry` binding. Fixes in the GAP-AR PR — details live in each fix's commits.

> Policy (ADR-0027): k6 thresholds are an executable signal, not a PR gate. Changing a threshold requires a new measurement documented here.
