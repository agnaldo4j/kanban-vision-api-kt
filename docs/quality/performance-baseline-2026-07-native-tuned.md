# Performance Baseline — July 2026 · Tuned native runtime (GAP-BR, ADR-0035 → ADR-0036)

Measurement of the runtime parameters ADR-0035 proposed (`--gc=G1`, PGO, k8s right-size), taken
**under the production resource envelope** — memory **and** CPU — as ADR-0035's Confirmation requires.
All arms were measured **in the same session**, on the same Docker VM, with the **DB wiped before each
run** (`docker compose down -v`), control and treatment under the same limits. Per ADR-0027 policy the
absolute numbers **do not** compare to `performance-baseline-2026-07-native.md` (that session ran a
15.6 GB VM; this one runs 16.7 GB) — only column-by-column within this file. **Arm A is this session's
anchor.**

> **Why the CPU envelope is here.** `k8s/03-deployment.yml` capped the pod at `limits.cpu: 500m`, and
> under that quota the runtime reports **`Effective CPU Count: 1`** (`java -XshowSettings:system` with
> `--cpus=0.5` → CPU Quota 50000us / Period 100000us). G1's whole rationale is parallelism, so a
> benchmark on an unconstrained host would have measured a configuration that does not exist in
> production. Every prior baseline in this directory was measured **without** any cgroup limit.

## Environment

| Item | Value |
|---|---|
| Hardware | Apple M2 Pro, 12 cores (host) · **Docker Desktop VM: NCPU=4, 16.7 GB** |
| OS | macOS 27.0 · Docker 29.6.1 |
| Stack | Full `docker compose`, DB wiped before each run |
| Container limits | `docker-compose.limits.yml` — `mem_limit`/`cpus` mirroring the k8s pod |
| Control | Serial GC (SubstrateVM default), current `main` |
| Treatments | `--gc=G1` · `--pgo` · CPU curve |
| Tool | k6 v2.1.0, `load/simulation-journey.js`, `baseline` profile (4 min, 0→20 VUs) |
| Date | 2026-07-16 |

> The host has 12 cores but **containers only ever see the VM's 4** — `NCPU=4`. Earlier baselines
> recorded "12 cores"; that is the host, not what the workload ran on.

## Comparative results

| Arm | GC | CPU | Mem limit | req/s | Overall p95 | Failures | `memory.peak` | `oom_kill` | `max` (throttle) |
|---|---|---|---|---|---|---|---|---|---|
| **A** (anchor) | Serial | none (4) | none | 2197.5 | 16.08 ms | 0.00% | 103.2 MiB | 0 | 0 |
| **B** (control) | Serial | 0.5 | 256Mi | 673.5 | 84.15 ms | 0.00% | **82.8 MiB** | 0 | 0 |
| **C** | **G1** | 0.5 | 256Mi | **522.3** | 90.08 ms | 0.00% | 97.8 MiB | 0 | 0 |
| **D** | G1 | 0.5 | 512Mi | 620.8 | 86.86 ms | 0.00% | 100.3 MiB | 0 | 0 |
| **E** | G1 | none (4) | none | 2302.1 | 15.55 ms | 0.00% | 317.0 MiB | 0 | 0 |
| **F** | **Serial + PGO** | 0.5 | 256Mi | **785.8** | 81.73 ms | 0.00% | **80.3 MiB** | 0 | 0 |
| **G** | Serial + PGO | **1.0** | 256Mi | **1989.8** | 32.94 ms | 0.00% | 92 MiB | 0 | 0 |
| **H** | Serial + PGO | 2.0 | 256Mi | 2602.5 | 14.54 ms | 0.00% | 89 MiB | 0 | 0 |

Image size: Serial **324 MB** · G1 **350 MB** (+8.0%) · Serial+PGO **296 MB** (−8.6%) · instrumented 516 MB.

100% of k6 checks passed on every arm (0 failed checks across ~1.9M requests). No arm violated a k6
threshold (`create`/`snapshot`/`cfd`/`list` p95 < 300 ms, `run_day` < 500 ms).

### Verdicts (`scripts/perf-regression.sh` — tolerance: throughput −10%, p95 +20%, error +0.01)

| Comparison | Result |
|---|---|
| **B → C** (Serial → G1, pod envelope) | **throughput −22.45% → REGRESSÃO.** ADR-0035 criterion (a) **failed** |
| **B → F** (Serial → Serial+PGO, pod envelope) | **+16.67%**, p95 −0.5…−4.2% → no regression |
| **A → E** (Serial → G1, 4 CPUs) | **+4.76%**, p95 −6…−9% → no regression |

### Latency per endpoint (p95, ms)

| Endpoint | A (Serial, 4cpu) | B (Serial, 0.5) | C (G1, 0.5) | F (Serial+PGO, 0.5) | E (G1, 4cpu) |
|---|---|---|---|---|---|
| create | 10.35 | 81.20 | 85.92 | **77.76** | 9.46 |
| run_day | 14.99 | 85.91 | 92.11 | **83.60** | 14.03 |
| snapshot | 8.62 | 79.18 | 84.06 | **76.00** | 7.89 |
| cfd | 8.78 | 79.58 | 84.37 | **76.67** | 7.95 |
| list | 24.24 | 84.67 | 90.53 | **84.27** | 24.58 |

## Sizing verified under limit (ADR-0035 Confirmation b)

The first baseline in this repo measured under a **real** container limit. Evidence is read from
cgroup v2 inside the container — `docker stats` merely recomputes `memory.current − inactive_file`
(cAdvisor's working-set formula), while the kernel already exposes the exact high-water mark:

```bash
docker exec kanban-vision-app sh -c 'cat /sys/fs/cgroup/memory.peak'    # exact, no sampling gap
docker exec kanban-vision-app sh -c 'cat /sys/fs/cgroup/memory.events'  # oom_kill / max
```

- **`memory.peak` is cumulative since container start** → read once at the end; it cannot miss a
  sub-second spike. This is why it replaces the post-boot/post-smoke/post-baseline RSS triplet of the
  earlier baselines — that triplet sampled RSS *post hoc* and so reported the impossible-looking
  `41.5 < 73.6` (post-baseline lower than post-boot). It was never a peak.
- **`memory.peak` includes reclaimable page cache** ⇒ conservative upper bound. That is why the
  unlimited arm A (103.2 MiB) reads *higher* than the limited arm B (82.8 MiB): under a limit the
  kernel reclaims cache instead of letting it grow.
- **Every arm: `oom_kill 0` and `max 0`** — no OOM kill and the limit was **never even reached**.
  `docker inspect .State.OOMKilled` = false, 0 restarts. (`memory.events` is stronger than
  `docker inspect`, which only fires if PID 1 dies.)

**Right-size verdict:** peak **80–92 MiB** under load, flat across every CPU level, inside a
**256Mi** limit — ~2.8× headroom, and `max 0` proves memory never bound the workload. **The
right-size costs zero throughput**; the arm B drop vs arm A is 100% CPU.

**Migration Job (Epsilon), virgin DB, 256Mi + 0.5 CPU:** 2 migrations applied (v1, v2) in 28 ms,
`exit 0`, no OOM. ⚠️ Epsilon **never frees** ⇒ the Job's peak is a function of **total allocation**
over its life, not its live set. Safe for today's 8 KB of DDL; a large data backfill could blow it.

## The CPU curve (Serial + PGO, 256Mi)

| `limits.cpu` | req/s | Δ vs 500m | Overall p95 | `memory.peak` |
|---|---|---|---|---|
| **500m** (current) | 785.8 | — | 81.73 ms | 80.3 MiB |
| **1000m** | **1989.8** | **+153%** | **32.94 ms (−60%)** | 92 MiB |
| **2000m** | 2602.5 | +231% | 14.54 ms (−82%) | 89 MiB |

**0.5 → 1 CPU is superlinear** (+153% from 2× the quota): removing the CFS throttle stops requests
queueing during throttled slices. **1 → 2 adds only +31%** — the knee is at 1 CPU. Memory is flat
across the curve, so the 256Mi right-size holds at any CPU level.

## Reading the results

- **G1 is refuted under the production envelope (−22.4%), and vindicated off it (+4.76%).** Both are
  true, and only the first one matters: with `Effective CPU Count: 1` the G1 runs ~1 GC thread — it
  pays a parallel collector's overhead without receiving the parallelism. ADR-0035's rationale was
  right in principle and inapplicable to this pod. **More heap does not rescue it**: at 512Mi (arm D)
  G1 is still −7.8% against Serial at 256Mi, so no `-XX:MaxRAMPercentage` closes the gap.
- **The bottleneck was never the GC.** `limits.cpu: 500m` costs **−69.4%** (arm A → B: 2197.5 → 673.5)
  and +423% p95. ADR-0035 set out to recover +7% via GC/PGO while the CPU cap was eating 69% — the
  wrong target by an order of magnitude. The −7.1% that motivated ADR-0032/0035 was itself measured
  on an **unconstrained host**, a configuration production has never run.
- **PGO wins on every axis** (+16.67% throughput, better p95 on all endpoints, 80.3 vs 82.8 MiB
  memory, 296 vs 324 MB image). It attacks the axis opposite to G1's: it reduces CPU work per request,
  and at 0.5 CPU the app is CPU-bound. **PGO alone more than reverses the −7.1% native→JIT deficit.**
- **The app is healthy at 0.5 CPU** — 0% failures, 100% of checks, p95 84 ms against a 300 ms
  threshold. The cap is not a bug; it is an undocumented density-vs-latency choice.
- **PGO profile: 39.6 MB** (5.1 MB gzipped) against an **8.9 MB** repository. Versioned gzipped with a
  `gunzip` step in the Dockerfile — no Git LFS, and no doubling the CI native build. Captured on
  **arm64** while CI builds **amd64**; `.iprof` is counter-based (branch frequencies, call counts), so
  it should port — verify the build log for rejection warnings.
- Arm A (2197.5) vs `performance-baseline-2026-07-native.md` (2305.3): −4.7%. Different session and VM
  (15.6 → 16.7 GB) — exactly why ADR-0027 forbids cross-file absolute comparison.

## How to reproduce

```bash
export JWT_DEV_MODE=true TRUSTED_PROXY_COUNT=1 GRAFANA_ADMIN_PASSWORD=admin
# Control (Serial): build from a git worktree of the pre-GAP-BR revision.
docker compose down -v                                   # wipe the DB — ADR-0027
docker build -t kanban-vision-api:serial . && docker tag kanban-vision-api:serial kanban-vision-api:local

# Arm A — unlimited
docker compose up -d --no-build
docker compose exec -T postgres psql -U kanban -d kanbanvision -c \
  "INSERT INTO organizations (id, name) VALUES ('11111111-1111-4111-8111-111111111111','k6-load-org') ON CONFLICT (id) DO NOTHING;"
k6 run load/simulation-journey.js                                        # smoke: expect 100% checks
k6 run -e PROFILE=baseline --summary-export=arm-A.json load/simulation-journey.js

# Arm B — pod envelope (memory AND cpu). This is the comparison that decides.
docker compose down -v
docker compose -f docker-compose.yml -f docker-compose.limits.yml up -d --no-build
# ...seed, smoke, baseline --summary-export=arm-B.json
docker exec kanban-vision-app sh -c 'cat /sys/fs/cgroup/memory.peak; cat /sys/fs/cgroup/memory.events'

# PGO capture (outside `docker build` — it has no DB and no k6):
#   1. buildArgs.add("--pgo-instrument")  → docker build -t kanban-vision-api:instr .
#   2. run with command: ["-XX:ProfilesDumpFile=/tmp/default.iprof"]   (appuser cannot write /app)
#   3. drive with `k6 run -e PROFILE=baseline` (representative workload)
#   4. docker stop -t 60   (graceful — the dump only happens on shutdown; the 10s default SIGKILLs)
#   5. docker cp kanban-vision-app:/tmp/default.iprof . && gzip -9
#   6. drop at http_api/src/pgo-profiles/main/ — the Gradle plugin finds *.iprof by convention

scripts/perf-regression.sh arm-B.json arm-C.json    # Serial → G1 under the envelope
scripts/perf-regression.sh arm-B.json arm-F.json    # Serial → Serial+PGO under the envelope
```

> Policy (ADR-0027): immutable snapshot; a new measurement = a new file. Previous baselines:
> `performance-baseline-2026-07.md`, `performance-baseline-2026-07-graalvm.md`,
> `performance-baseline-2026-07-otel-sdk.md`, `performance-baseline-2026-07-native.md`.
