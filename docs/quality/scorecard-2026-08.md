# Quality Scorecard — post-ADR-0038 (module extraction complete)

Full re-score on `main` @ `bb190c0`, after the ADR-0038 cycle (GAP-CE..CL, PRs #295–#302) split the
single `:domain` module into three bounded-context modules — `:domain-common`, `:domain-kanban`,
`:domain-simulation` — with the dependency graph `simulation → kanban → common` enforced by a new
fitness function. Each of the 22 dimensions was re-assessed against the current codebase with the
corresponding `/skill` rubric and the live CI signals. This file is the in-repo, immutable snapshot
(ADR-0023) and the source for the wiki `Quality-Analysis` page. It supersedes `scorecard-2026-07.md`
(it does not edit it).

## Overall — 9.22 / 10  (was 9.12)

**Method (reproducible):** the overall is the **simple arithmetic mean of the dimension scores** —
recomputable from the table (`202.9 / 22 = 9.22`).

What moved the headline this cycle is a single structural change realised across eight small PRs:
- **Microservices / Modular Monolith 8.8 → 9.4** — the intentional `[E]` structural debt named in the
  previous scorecard ("the two contexts share one `domain` module … raising it needs an ADR-approved
  extraction, not a quick fix") is **resolved**. The context boundary is now a physical Gradle-module
  boundary, and the `simulation → kanban → common` direction is machine-enforced. **It crosses 9.0 for
  the first time — no scorecard dimension is now below the 8.0 line, and only three sit below 9.0.**
- The extraction rippled honest, evidence-backed gains into **Circular-dependency control (7.8 → 8.3)**,
  **Screaming Architecture (9.0 → 9.3)**, and small bumps to DDD, Clean Architecture, Refactoring, ADR,
  c4-model and Evolutionary Change.

> Overlap note (unchanged): **GraalVM** overlaps Infra + Performance, and **Circular-dependency control**
> overlaps Clean Architecture + Refactoring. Excluding those two overlapping rows, the **20-skill** mean
> is **9.28** (`185.6 / 20`).

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
| 19 | owasp | Security | 8.5 | 8.5 | — |
| 20 | load-testing | Performance efficiency | 8.0 | 8.0 | — |
| 21 | graalvm | GraalVM / Native runtime | 9.0 | 9.0 | — |
| 22 | circular-dependency-control | Circular-dependency control | 7.8 | **8.3** | +0.5 |

**Mean = 202.9 / 22 = 9.22.** Nine dimensions rose; thirteen held; none regressed.

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

### Held (extraction does not touch them)
Security 8.5, Performance efficiency 8.0, GraalVM 9.0, SOLID 9.4, Functional Kotlin 9.5, OpenAPI 9.0,
Schema 9.1, Infra 9.3, Observability 9.2, XP/Kanban 9.4, DoD 9.2, Test quality 9.7, CI gates 9.7 —
no code in these areas changed materially; scores carry forward from `scorecard-2026-07.md`.

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

- **Security (8.5) — highest-value:** JWT `validate{}` verifies only the audience, not an
  `organizationId` claim, and use cases have **no caller-vs-resource ownership check** → IDOR /
  cross-tenant risk. Swagger UI mounted unconditionally (not gated by `ENABLE_SWAGGER`). No HSTS. Rate
  limit keyed on a spoofable first `X-Forwarded-For`; no stricter `/auth/*` limit.
- **Performance (8.0):** single point-in-time k6 baseline, manual (not a PR gate); no stress/soak/spike
  profiles, no automated regression signal.
- **Circular-dependency (8.3):** class↔class cycles within a single package are still not detected
  (analysis is import-based, package granularity).
- **Microservices (9.4):** the remaining cap is the **unified DB schema with cross-context FKs** —
  Database-per-context / published-language snapshot is ADR-0038's deferred Option 4, an `[E]` decision
  with no current demand.
- **GraalVM (9.0):** `DomainErrorResponse` serialization on the native **error path** still relies on
  reachability metadata (previously noted); native all-route smoke is manual.
