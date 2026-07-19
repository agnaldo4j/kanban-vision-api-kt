# Performance — `days` endpoint threshold basis (GAP-CR, ADR-0027)

Basis for the k6 p95 threshold added for the `days` endpoint
(`GET /api/v1/simulations/{id}/days`, the Analytics day-series) in the scheduled
regression signal (`.github/workflows/perf-regression.yml`, ADR-0039).

This is **not** a fresh full baseline run — it documents, per ADR-0027 ("changing a
threshold requires a new measurement documented here"), the **measured basis** by which
the `days` threshold is set, transparently.

## Why `days` had no threshold until now

The `days` request has existed and been exercised in `load/simulation-journey.js` since
GAP-AR (#225) — tagged `endpoint: 'days'`, with business-invariant checks, after 5 days
are run. But k6 only materializes a `http_req_duration{endpoint:X}` submetric in
`--summary-export` when tag `X` has a **threshold**. `days` had none, so its p95 never
surfaced and it could not be compared by `scripts/perf-regression.sh`. GAP-CN reverted an
attempt to add it to the comparator for exactly this reason (a tagged endpoint without a
threshold is rejected — ADR-0027).

## Threshold basis: the GET-read class

`days` is the same **GET analytics-read class** as its siblings `cfd` and `snapshot`, all
issued in the same journey iteration against the same simulation state. Those siblings were
measured in the authoritative local baseline
[`performance-baseline-2026-07.md`](performance-baseline-2026-07.md) (2026-07-05, `baseline`
profile, Apple M2 Pro / docker compose):

| Sibling GET-read | p95 (measured) | k6 threshold |
|---|---|---|
| `GET .../days/{d}/snapshot` (snapshot) | 7.49 ms | < 300 ms ✅ |
| `GET .../cfd` (cfd) | 7.70 ms | < 300 ms ✅ |
| `GET .../simulations?organizationId=` (list) | 31.95 ms | < 300 ms ✅ |

`days` ran in that **same** measurement journey (it is step 3 of `create → run day ×5 →
days → snapshot → cfd → list`) — only its per-endpoint submetric was not materialized. It is
a bounded read of a 5-element day series, of the same shape and cost class as `cfd`/`snapshot`.

**Therefore the threshold is set to the class value `p(95) < 300` ms** in `baseThresholds`
(`load/simulation-journey.js`) — consistent with every GET read in the journey.

## The `days`-specific empirical number

The threshold's only functional role in the regression signal is to **materialize** the
submetric for the CI-vs-CI comparison (whose authoritative tolerances are wide and
CI-measured, ADR-0039). The **empirical, `days`-specific p95** is captured when the CI
reference is (re-)bootstrapped: dispatch `perf-regression.yml`, review the `k6-summary`
artifact — which will now contain `http_req_duration{endpoint:days}` — and commit it as
`load/ci-reference-summary.json` (ADR-0039 / skill `/load-testing`). Until then the
comparator skips `days` gracefully (`scripts/perf-regression.sh` — a submetric missing on
either side is a `continue`, never an error).

## Policy

- **ADR-0027**: k6 thresholds are an executable signal, **never a PR gate**. The authoritative
  latency baseline is the **local** measurement in `performance-baseline-2026-07.md`.
- **ADR-0039**: the CI reference (`load/ci-reference-summary.json`) is a coarse, non-blocking,
  CI-measured **tripwire**, distinct from the local baseline — and is a human, diff-reviewed
  bootstrap step, not measurable outside the runner.
