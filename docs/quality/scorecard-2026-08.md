# Quality Scorecard — post-ADR-0038 (module extraction complete)

Full re-score on `main` @ `bb190c0`, after the ADR-0038 cycle (GAP-CE..CL, PRs #295–#302) split the
single `:domain` module into three bounded-context modules — `:domain-common`, `:domain-kanban`,
`:domain-simulation` — with the dependency graph `simulation → kanban → common` enforced by a new
fitness function. Each of the 22 dimensions was re-assessed against the current codebase with the
corresponding `/skill` rubric and the live CI signals. This file is the in-repo, immutable snapshot
(ADR-0023) and the source for the wiki `Quality-Analysis` page. It supersedes `scorecard-2026-07.md`
(it does not edit it).

## Overall — 9.28 / 10  (was 9.12)

**Method (reproducible):** the overall is the **simple arithmetic mean of the dimension scores** —
recomputable from the table (`204.2 / 22 = 9.28`).

Two things move the headline, and honesty demands both:
- **Microservices / Modular Monolith 8.8 → 9.4** — the intentional `[E]` structural debt named in the
  previous scorecard ("the two contexts share one `domain` module … raising it needs an ADR-approved
  extraction, not a quick fix") is **resolved** by ADR-0038 (GAP-CE..CL): the context boundary is now a
  physical Gradle-module boundary, and the `simulation → kanban → common` direction is machine-enforced.
  It crosses 9.0 for the first time. This rippled honest gains into **Circular-dependency control
  (7.8 → 8.3)**, **Screaming Architecture (9.0 → 9.3)**, and small bumps to DDD, Clean Architecture,
  Refactoring, ADR, c4-model and Evolutionary Change.
- **A genuine full re-score corrected three dimensions the previous scorecard held stale.** The July
  scorecard (`f157674`) predated a run of hardening gaps that landed before `bb190c0`: **Security
  8.5 → 9.0** (GAP-BK gated Swagger behind `ENABLE_SWAGGER`; GAP-BL added HSTS, a stricter `/auth`
  rate limit and spoof-resistant `X-Forwarded-For` keying; the JWT now requires a non-blank
  `organizationId` claim that tenant-scopes the queries), **Performance 8.0 → 8.7** (GAP-BO added k6
  stress/soak/spike profiles and the `perf-regression.sh` comparator), **GraalVM 9.0 → 9.1** (GAP-BM
  closed the native error-path `DomainErrorResponse` serialization; the CI smoke test now probes it).
  These gains are **not** ADR-0038's — they are earlier work this re-score verified against the code
  instead of carrying the stale July notes forward.

**Only two dimensions now sit below 9.0** (Performance 8.7, Circular-dependency 8.3).

> Overlap note (unchanged): **GraalVM** overlaps Infra + Performance, and **Circular-dependency control**
> overlaps Clean Architecture + Refactoring. Excluding those two overlapping rows, the **20-skill** mean
> is **9.34** (`186.8 / 20`).

## 22-skill scorecard

| # | Skill | Dimension | Prev | New | Δ |
|---|---|---|---|---|---|
| 1 | ddd | Domain modeling | 9.6 | **9.7** | +0.1 |
| 2 | clean-architecture | Layering | 9.5 | **9.6** | +0.1 |
| 3 | screaming-architecture | Package intent | 9.0 | **9.3** | +0.3 |
| 4 | solid-principles | SOLID | 9.4 | 9.4 | — |
| 5 | fp-oo-kotlin | Functional Kotlin | 9.5 | 9.5 | — |
| 6 | refactoring | Code health | 9.2 | **9.3** | +0.1 |
| 7 | testing-and-observability | Test quality | 9.7 | 9.7 | — |
| 8 | kotlin-quality-pipeline | CI gates | 9.7 | 9.7 | — |
| 9 | openapi-quality | API docs | 9.0 | 9.0 | — |
| 10 | db-migrations | Schema | 9.1 | 9.1 | — |
| 11 | microservices-modular-monolith | Boundaries | 8.8 | **9.4** | +0.6 |
| 12 | local-and-production-environment | Infra | 9.3 | 9.3 | — |
| 13 | opentelemetry | Observability | 9.2 | 9.2 | — |
| 14 | evolutionary-change | Change mgmt | 9.2 | **9.4** | +0.2 |
| 15 | adr | Decision records | 9.6 | **9.7** | +0.1 |
| 16 | c4-model | Architecture docs | 9.0 | **9.2** | +0.2 |
| 17 | xp-kanban | Engineering practices | 9.4 | 9.4 | — |
| 18 | definition-of-done | DoD | 9.2 | 9.2 | — |
| 19 | owasp | Security | 8.5 | **9.0** | +0.5 |
| 20 | load-testing | Performance efficiency | 8.0 | **8.7** | +0.7 |
| 21 | graalvm | GraalVM / Native runtime | 9.0 | **9.1** | +0.1 |
| 22 | circular-dependency-control | Circular-dependency control | 7.8 | **8.3** | +0.5 |

**Mean = 204.2 / 22 = 9.28.** Twelve dimensions rose; ten held; none regressed.

## What moved, and why (evidence)

- **Microservices/Modular 8.8 → 9.4** — the shared-`domain`-module debt is gone. `:domain` is split into
  `:domain-common` (`Domain`/`Audit`/`DomainError`/`CommonError`), `:domain-kanban` (Board/Card/Step/
  Worker/Ability + Organization/Tribe/Squad/PolicySet + `KanbanError`) and `:domain-simulation`
  (Simulation/Scenario/SimulationEngine/DailySnapshot + `SimulationError`). The customer-supplier edge
  `simulation → kanban` is now a real `project` dependency, and a new fitness function
  (`ProjectDependencyGraphTest`) asserts the whole `simulation → kanban → common` graph and blocks any
  inversion (it parses the `build.gradle.kts` because Konsist can't see `project` deps; the parser was
  hardened to catch multiline / named-arg declaration forms). **Residual cap (why not higher):** the DB
  schema is still unified with cross-context FKs (`simulations.organization_id`, steps/cards → boards) —
  Database-per-context is the deferred Option-4 work in ADR-0038, not done here.
- **Circular-dependency 7.8 → 8.3** — the project-dependency graph is now explicitly asserted
  (`ProjectDependencyGraphTest`), closing the class of "someone declares an inverted `project(...)` edge"
  that `HexagonalArchitectureTest` (package-level) can't see. `PackageCycleTest` (whole-graph) still holds.
  Residual cap unchanged: class↔class cycles inside a single package are not analysed (import-based,
  package granularity).
- **Screaming Architecture 9.0 → 9.3** — the Gradle module names now *scream* the bounded contexts
  (`domain-kanban`, `domain-simulation`, `domain-common`); a newcomer reads the context map from
  `settings.gradle.kts`. Errors are co-located with their aggregates (`KanbanError` in `model.kanban`,
  `SimulationError` in `model.simulation`); the generic `domain.errors` package is retired.
- **Evolutionary Change 9.2 → 9.4** — ADR-0038 was executed as an eight-step J-Curve (GAP-CE..CL), each
  a single green PR: phase 1 untangled packages within the one module (Scenario/ScenarioRules move,
  DomainError split, DomainEvent move, Refs split, base isolation), phase 2 extracted the three Gradle
  modules — CI green at every step, ADR-first for the `[E]` decision. A textbook incremental structural
  change with no big-bang.
- **DDD 9.7 / Clean 9.6 / Refactoring 9.3 / ADR 9.7 / c4-model 9.2** — strategic design sharpened
  (contexts as modules, per-context value-class IDs and errors); the dependency rule now holds across
  three acyclic domain modules; the extraction is a disciplined behaviour-preserving refactoring; ADR-0038
  even predicted the hard-coded module refs and the graph fitness function it now needs; the architecture
  diagrams (wiki `Architecture`/`Architecture-Domain`, `docs/context-map.md`) were updated this cycle.

### Corrected by the full re-score (fixed by intervening gaps, not by ADR-0038)
The July scorecard held these on stale evidence; verified against the code at `bb190c0`:
- **Security 8.5 → 9.0** — the named residuals are closed: JWT `validate{}` requires a non-blank
  `organizationId` claim and the simulation use cases tenant-scope by it (`ListSimulationsQuery`,
  `CreateSimulationCommand`); `configureOpenApiRoutes` is gated behind `ENABLE_SWAGGER` (GAP-BK);
  `SecurityHeaders` adds HSTS + `X-Frame-Options`/`nosniff`/CSP; `RateLimit` registers a stricter
  `/auth` limit (5/min) with spoof-resistant `X-Forwarded-For` keying, spoof-proof by default when
  `TRUSTED_PROXY_COUNT=0` (GAP-BL). Held below 9.5: HS256 symmetric JWT and no automated DAST.
- **Performance efficiency 8.0 → 8.7** — `load/simulation-journey.js` ships smoke/baseline/**stress/
  soak/spike** profiles exposed through `.github/workflows/load-test.yml` (workflow_dispatch), plus the
  executable `scripts/perf-regression.sh` comparator that fails on regression beyond tolerance
  (GAP-BO). Held below 9.0 **by design**: the baseline is machine-dependent so the run stays **manual,
  not a PR gate** (ADR-0027 — shared CI runners are too noisy); it is a single reference environment.
- **GraalVM 9.0 → 9.1** — the native error-path `DomainErrorResponse` serialization is fixed with
  committed reachability metadata, and the CI **smoke test now probes a no-token `401`** that
  serialises it in the Native Image (GAP-BM). Native all-route smoke is still a subset.

### Held (unchanged, no material change since July)
SOLID 9.4, Functional Kotlin 9.5, OpenAPI 9.0, Schema 9.1, Infra 9.3, Observability 9.2, XP/Kanban 9.4,
DoD 9.2, Test quality 9.7, CI gates 9.7 — scores carry forward from `scorecard-2026-07.md`.

## Key metrics (current)

- **Modules:** **7** Gradle modules — `domain-common`, `domain-kanban`, `domain-simulation`, `usecases`,
  `sql_persistence`, `http_api`, plus the test-only `architecture`. Domain dependency graph
  `domain-simulation → domain-kanban → domain-common` (fitness-enforced).
- **Tests:** ~540 across the product modules + Konsist; pyramid = unit · Kotest property ·
  embedded-Postgres integration · `testApplication` route · Pact contract.
- **Coverage:** JaCoCo gate **≥ 98%/module** (ADR-0029), all 7 modules; measured overall ~99%.
- **Mutation (PITest, STRONGER):** domain-common 100 (gate 90) · domain-kanban 82 (78) ·
  domain-simulation 77 (73) · usecases 60 (55) · sql_persistence 72 (65) · http_api 51 (45).
- **Static analysis:** Detekt 2.0.0-alpha.5 `warningsAsErrors`, **0 violations** · KtLint 1.5.0, 0.
- **Architecture fitness:** Konsist + JUnit **19 fitness tests** across 8 suites (Hexagonal, Ports
  placement, Conventions, Domain purity, Context boundary, Contract package, Package cycle, and the new
  **Project dependency graph**). `ContextBoundaryTest`/`DomainPurityTest` are now module-agnostic
  (scope-all + package filter) so they survive the extraction.
- **Supply chain:** CycloneDX SBOM + osv-scanner SCA, **blocking** (ADR-0025); 1 documented exception.
- **Performance:** k6 baseline p95 ~22 ms, ~1,644 req/s (ADR-0027, manual).
- **Runtime:** GraalVM Native Image — ~0.12 s startup, ~74 MiB RSS (ADR-0030→0032).

## Residual debt & candidate gaps for the next cycle

The lowest dimensions are the honestly-surfaced ones — strong candidates for board #6 (ADR-0023: the
board is the single source of progress; this snapshot records state, it does not schedule work):

- **Performance (8.7):** the profiles and comparator exist (GAP-BO) but stay **manual by design**
  (ADR-0027 — CI runners are too noisy for a stable machine-dependent baseline), so there is still no
  automated per-PR regression gate; the reference is a single environment.
- **Circular-dependency (8.3):** class↔class cycles within a single package are still not detected
  (analysis is import-based, package granularity).
- **Security (9.0):** the concrete IDOR / header / rate-limit residuals are closed (GAP-BK/BL); what
  remains is lower-leverage — HS256 symmetric JWT (no asymmetric rotation) and no automated DAST/pentest.
- **Microservices (9.4):** the remaining cap is the **unified DB schema with cross-context FKs** —
  Database-per-context / published-language snapshot is ADR-0038's deferred Option 4, an `[E]` decision
  with no current demand.
- **GraalVM (9.1):** the error-path serialization is fixed (GAP-BM); native all-route smoke is still a
  probed subset rather than the full route surface.
