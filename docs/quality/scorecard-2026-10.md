# Quality Scorecard — post-GAP-DD/DE (evolução das dimensões abaixo de 9.0)

Full re-score on `main` after the **evolution cycle** that targeted the two dimensions still below 9.0 in
`scorecard-2026-09.md`: **GAP-DD** (#341) added the constructor-injection (Koin DI) cycle fitness function,
and **GAP-DE** (#342) added p99 tail-latency tracking to the k6 baseline. Only these two dimensions moved;
the other 20 were re-verified against the current code and held. This file is the in-repo, **immutable
snapshot** (ADR-0023) and the source for the wiki `Quality-Analysis` page. It supersedes `scorecard-2026-09.md`
(it does not edit it).

## Overall — 9.37 / 10  (was 9.35)

**Method (reproducible):** the overall is the **simple arithmetic mean of the dimension scores** —
recomputable from the table (`206.1 / 22 = 9.37`).

What moved, honestly — both were **principled plateaus**, so the cycle chose the highest-value increment for
each and **explicitly accepted** the residual cap (rather than chase the score):

- **Circular-dependency 8.7 → 9.0** — GAP-DD (#341) closed the highest-severity blind spot the
  `/circular-dependency-control` skill itself names: a **constructor-injection cycle** (`A(B)` + `B(A)` →
  Koin `StackOverflowError` at wiring), invisible to `PackageCycleTest` (import graph) and `ClassCycleTest`
  (intra-package composition) because wiring is assembled in `AppModule`, cross-package, resolved by *type*
  (`get()`). `DiWiringCycleTest` parses the `single { }` bindings into a port→impl map, reads each component's
  primary-constructor parameter types (Konsist) and runs the shared `findCycle` DFS — green today (the wiring
  is a DAG), so its value is a **regression guard**, like `ProjectDependencyGraphTest`. Review hardened it:
  self-injection self-edges are preserved (a real Koin cycle, ≠ data composition) and a wired-component
  simple-name collision now fails loud. **Residual cap (why not higher):** still a static import/declaration
  analysis, not a runtime call-graph (method-body refs, reflection, `typealias` stay out of scope).
- **Performance (load-testing) 8.8 → 8.9** — GAP-DE (#342) enriched the SLO signal from p95-only to
  **p95 + p99** (tail latency): `summaryTrendStats` now materialises `p(99)`, `baseThresholds` carry
  per-endpoint p99 SLO budgets, and `perf-regression.sh` compares the tail with its own `P99_RISE_PCT`
  tolerance (graceful-skip until the CI reference is re-bootstrapped). **Residual cap (why still < 9.0):**
  the deliberate **no per-PR blocking gate** (ADR-0027: a flaky perf gate is worse than none) and the single
  reference environment remain — an **accepted trade-off, not a defect**; the empirical p99 baseline lands on
  the next reference-machine run / CI-reference bootstrap (ADR-0039), never fabricated in a noisy env.

**Only Performance now sits below 9.0** (8.9); Circular-dependency crossed to 9.0.

> Overlap note (unchanged method): excluding the two overlapping rows (**GraalVM**, **Circular-dependency**),
> the **20-skill** mean is **9.395** (`187.9 / 20`).

## 22-skill scorecard

| # | Skill | Dimension | Prev (2026-09) | New | Δ |
|---|---|---|---|---|---|
| 1 | ddd | Domain modeling | 9.7 | 9.7 | — |
| 2 | clean-architecture | Layering | 9.6 | 9.6 | — |
| 3 | screaming-architecture | Package intent | 9.3 | 9.3 | — |
| 4 | solid-principles | SOLID | 9.4 | 9.4 | — |
| 5 | fp-oo-kotlin | Functional Kotlin | 9.5 | 9.5 | — |
| 6 | refactoring | Code health | 9.3 | 9.3 | — |
| 7 | testing-and-observability | Test quality | 9.7 | 9.7 | — |
| 8 | kotlin-quality-pipeline | CI gates | 9.8 | 9.8 | — |
| 9 | openapi-quality | API docs | 9.0 | 9.0 | — |
| 10 | db-migrations | Schema | 9.1 | 9.1 | — |
| 11 | microservices-modular-monolith | Boundaries | 9.4 | 9.4 | — |
| 12 | local-and-production-environment | Infra | 9.5 | 9.5 | — |
| 13 | opentelemetry | Observability | 9.5 | 9.5 | — |
| 14 | evolutionary-change | Change mgmt | 9.4 | 9.4 | — |
| 15 | adr | Decision records | 9.7 | 9.7 | — |
| 16 | c4-model | Architecture docs | 9.3 | 9.3 | — |
| 17 | xp-kanban | Engineering practices | 9.4 | 9.4 | — |
| 18 | definition-of-done | DoD | 9.2 | 9.2 | — |
| 19 | owasp | Security | 9.2 | 9.2 | — |
| 20 | load-testing | Performance efficiency | 8.8 | **8.9** | +0.1 |
| 21 | graalvm | GraalVM / Native runtime | 9.2 | 9.2 | — |
| 22 | circular-dependency-control | Circular-dependency control | 8.7 | **9.0** | +0.3 |

**Mean = 206.1 / 22 = 9.37.** Two dimensions rose (Circular-dependency, Performance); twenty held; none regressed.

## Held (unchanged since the 2026-09 re-score)

All 20 other dimensions were re-verified against the current code — no material change since #340: DDD 9.7,
Clean 9.6, Screaming 9.3, SOLID 9.4, Functional Kotlin 9.5, Refactoring 9.3, Test quality 9.7, CI gates 9.8,
OpenAPI 9.0, Schema 9.1, Microservices 9.4, Infra 9.5, Observability 9.5, Change mgmt 9.4, ADR 9.7,
c4-model 9.3, XP/Kanban 9.4, DoD 9.2, Security 9.2, GraalVM 9.2.

## Key metrics (current)

- **Modules:** **7** Gradle modules + the test-only `architecture`. Graph `simulation → kanban → common`.
- **Coverage:** JaCoCo gate **≥ 98%/module** (ADR-0029); measured overall ~99.4%.
- **Architecture fitness:** Konsist + JUnit — now **12 suites** including the new **`DiWiringCycleTest`**
  (constructor-injection cycle) alongside `PackageCycleTest`, `ClassCycleTest`/`ClassGraphTest`/`CycleDetectionTest`
  and `ProjectDependencyGraphTest`.
- **Performance:** k6 baseline p95 ~22 ms, ~1,644 req/s (ADR-0027, manual); **p99 tail-latency now tracked**
  (GAP-DE); scheduled non-blocking regression tripwire (ADR-0039).
- **Supply chain:** CycloneDX SBOM over `runtimeClasspath` + `migrationRuntime` + osv-scanner, blocking; 0 exceptions.

## Residual debt & candidate gaps for the next cycle

Recorded state, not a schedule (ADR-0023 — board #6 is the single source of progress). This cycle closed the
last DI-wiring cycle blind spot and instrumented p99; what remains:

- **Performance (8.9):** the **sole** dimension below 9.0 — capped **by design** (ADR-0027: no per-PR gate,
  shared runners too noisy; single reference environment). p99 is now instrumented; its empirical baseline
  lands on the next reference-machine run / CI-reference bootstrap (ADR-0039).
- **Circular-dependency (9.0):** static import/declaration graphs only; a runtime call-graph is out of scope.
- **Security (9.2):** HS256 symmetric JWT (no asymmetric rotation), no automated DAST/pentest.
- **Microservices (9.4):** unified DB schema with cross-context FKs (Database-per-context deferred, no driver).
- **GraalVM (9.2):** native API smoke is still a probed subset of routes.
