# OTel context leak between requests in the native binary — diagnosis and mitigation (GAP-BC)

**Date**: 2026-07-08 · **Follow-up of**: ADR-0032 / GAP-BB (`docs/quality/performance-baseline-2026-07-native.md`)
**Versions**: Ktor 3.5.1 · opentelemetry-java-instrumentation 2.29.0-alpha · OTel SDK 1.63.0 · GraalVM 25

> *Language note: translated to English on 2026-07-09; the diagnosis, evidence and numbers are unchanged from the original snapshot.*

## Symptom (observed in GAP-BB)

In the native binary, under load, the Netty event loop retains the previous request's OTel context: `KtorServerTelemetry`'s `Instrumenter` suppresses new SERVER spans (suppression by `SpanKind`) and the JDBC/manual spans of following requests chain into one giant trace or are born as traces with no SERVER root.

## Root cause (upstream)

Ktor's `SuspendFunctionGun` (SFG) runs interceptors with a shared continuation; on suspensions with an external dispatcher (`withContext(Dispatchers.IO)`), the OTel `ThreadContextElement` (`Context.asContextElement()`) gets `updateThreadContext` without the paired `restoreThreadContext` — the event-loop thread stays bound to the stale context.

- [KTOR-9431](https://youtrack.jetbrains.com/issue/KTOR-9431) — exactly this bug; **fixed in Ktor 3.4.3** (PR ktorio/ktor#5503, contained in 3.5.1).
- [opentelemetry-java-instrumentation#16430](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/16430) — same symptom; closed with workarounds; instrumentation 2.29.0 already embeds the `EmptyInterceptor` palliative.
- [KTOR-6802](https://youtrack.jetbrains.com/issue/KTOR-6802) — an autoreload variant; still open.

**The two known fixes are already in the project's versions and the leak still reproduces in the native binary** — the manifestation is native-image specific (the KTOR-9431 fix uses "conditional redispatch" that behaves differently under AOT). On the JVM the leak does NOT reproduce anymore (validated with real Netty + 512 concurrent requests).

## Empirical evidence (local docker compose, k6 smoke journey, Tempo API)

| Scenario | Requests | Traces sampled | Traces with exactly 1 SERVER span | Notes |
|---|---|---|---|---|
| Native, default | 20,791 (0 failures) | 5,000 | **14 (0.3%)** | 4,986 traces with no SERVER root; largest trace: **9,822 spans** |
| Native, `-Dio.ktor.internal.disable.sfg=true` | 22,041 (0 failures) | 5,000 | **5,000 (100%)** | largest trace: 7 spans; route names preserved (`GET .../{simulationId}/cfd`); smoke throughput 734 vs 692 req/s (≥ baseline) |
| Native, final image (flag in ENTRYPOINT) | 14,871 (0 failures) | 5,000 | **5,000 (100%)** | rebuild with this PR's Dockerfile; same result |
| JVM (sequential test host + concurrent Netty) | 24 + 512 | — | 100% | `TelemetryContextIsolationTest` — permanent safety net |

Methodology: `docker compose up --build` (full stack with Tempo), seeded org, `k6 run load/simulation-journey.js` (smoke), analysis via Tempo's `GET /api/search` + `GET /api/traces/{id}` counting `SPAN_KIND_SERVER` spans per trace.

## Mitigation applied

`-Dio.ktor.internal.disable.sfg=true` in the Dockerfile `ENTRYPOINT` (native runtime). The flag swaps the SFG for `DebugPipelineContext` (the conventional coroutines path, without the shared-continuation optimization). No latency/throughput regression in the smoke; route names preserved (the context-reset mitigation evaluated in GAP-BB degraded the name — this one doesn't).

- k8s production stays on `OTEL_TRACES_EXPORTER=none` by default (unchanged); with the flag in the ENTRYPOINT, enabling traces via a patch now produces intact traces.
- Dev/tests on the JVM don't need the flag.

## Follow-ups

- Re-evaluate the flag on every Ktor 3.x release (look for a native SFG fix) and on opentelemetry-java-instrumentation 2.x.
- The upstream issue was **not** filed in this round (decision recorded on the GAP-BC card); this file holds the repro material should reporting be decided.
- Side finding (out of scope, candidate card): in native, the error path of `POST /auth/token` with an invalid payload fails to serialize `DomainErrorResponse` ("Cannot reflectively read or write field ... $$serializer") — a reachability-metadata gap on the error path.
