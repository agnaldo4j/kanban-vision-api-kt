# Quality Analysis — Dimensions Below 9.0 (July 2026)

> **Immutable snapshot** (ADR-0023): future analyses are new files `docs/quality/audit-YYYY-MM-*.md`, never edits of this one.
> **Reference:** main `d7657c3` (2026-07-08), GraalVM roadmap complete (PRs up to #250); documentation fully in English (PRs up to #259).
> **Scope:** the four dimensions of the Wiki [Quality-Analysis](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Quality-Analysis) 18-skill scorecard that currently score **below 9.0**. Grounded in a code+process audit, not a self-assessment. Findings are turned into candidate gaps on [board #6](https://github.com/users/agnaldo4j/projects/6) — the single source of progress (ADR-0023); this file never records progress.

## The four dimensions and their root cause

| Dimension | Score | Root cause of the cap | State |
|---|---|---|---|
| **Microservices / Modular Monolith** | 8.4 | Bounded contexts share one `domain` module with **direct cross-context references**, and there is **no fitness function enforcing the documented context boundaries** — Konsist enforces *layers*, not *contexts*. | 🟡 |
| **XP / Kanban** | 8.7 | The process is **manual trust, not machine-enforced**: branch protection on `main` is effectively off. | 🟡 |
| **Evolutionary Change** | 8.8 | No **flow-feedback layer** (no lead-time / WIP-aging / CFD); the ≤400-line J-Curve limit is honor-system. | 🟡 |
| **SOLID** | 8.9 | Small, localized debt: unused abstractions and a couple of non-exhaustive sealed dispatches. | 🟢 |

> The rest of the scorecard (DDD 9.5, Clean Architecture 9.5, Testing & Observability 9.7, Kotlin Quality 9.7, ADRs 9.6, …) is strong and machine-enforced. **DIP/ISP are exemplary and Konsist-guarded; there are no god-classes, no `LargeClass`/`TooManyFunctions` violations, and no unsafe casts.** The items below are refinements, not defects.

---

## 1. Microservices / Modular Monolith — 8.4 (lowest)

**Note on the score:** *"Context Map defined; extraction candidates identified but not yet realized."* Accurate and slightly generous.

**Present (positive):** a genuine, well-documented context map (`docs/context-map.md`, three established BCs + two future candidates); four acyclic Gradle layer-modules (`domain`, `usecases`, `sql_persistence`, `http_api`) plus a test-only `architecture/` module; ports-and-adapters seams (repository interfaces + `SimulationEnginePort`) that make Branch-by-Abstraction extraction feasible; four Konsist fitness functions.

**Findings:**

1. **Contexts share one `domain` module as a coarse Shared Kernel, with direct cross-context aggregate references** — not ID refs or an ACL. `Scenario.kt:13` holds `val board: Board` (concrete type), and `SimulationEngine` imports six kanban types (`Board`, `Card`, `CardState`, `ServiceClass`, `Step`, `Worker`). The coupling is healthily one-directional (kanban never imports simulation), but it is a compile-level dependency across contexts. `BoardRef`/`StepRef`/`ScenarioRef` already exist in `Refs.kt` as the intended decoupling seam but are **unused for that purpose**.
2. **No fitness function enforces the context boundaries.** Konsist enforces the *layer* rule (domain purity, ports placement, "usecases must not import `com.kanbanvision.persistence`") but nothing forbids `model/kanban` ↔ `model/simulation` coupling. The documented context map can erode silently.
3. **Single unified DB schema with cross-context FKs** (`V1__initial_schema.sql`: `simulations.organization_id → organizations(id)`, steps/cards → boards). No Database-per-Service seam. This is intentional monolith debt, not planned for change.
4. **Analytics / Forecasting / Policy are logical/aspirational** — no code seams. Documented as deferred extraction candidates.
5. **Documentation inconsistency:** ADR-0021 states the shared kernel is `ServiceClass` + `WorkItem`, but **`WorkItem` does not exist** in the codebase (the unit of work is `Card`); `docs/context-map.md` says the shared kernel is the whole `domain/` module.

**Affordable path (chosen):** enforce the documented boundary (a Konsist context-boundary rule) rather than restructure. Replacing `Scenario.board: Board` with `BoardRef`, per-BC Gradle modules, and DB-per-context schema splitting are **structural [E] work** that overlaps intentional deferred debt (see the July audit) and are **not** planned here.

## 2. XP / Kanban — 8.7

**Note on the score:** *"WIP limit 1 enforced; PR discipline present but quality-first merges need strengthening."*

**Present (positive, and *evidenced* in git history):** small, focused PRs (typical sizes #256 +5/-3, #253 +1/-1, #247 +21); every commit subject references a gap and/or ADR; the `[E]` → "ADR before code" discipline is visible as `PR 1/2` (ADR) then `PR 2/2` (implementation) splits; squash-merge after green CI; a rich CI (`quality` + `supply-chain` + `build`-with-smoke-test) with PR-comment bots.

**Findings (the process is manual trust, not enforced):**

1. **Branch protection on `main` is effectively off** — `required_status_checks=null` (CI is *not* a required check; a red PR *can* be merged), `required_approving_review_count=0` (no review required, though `politicas §9` claims "manual and human-reviewed"), `required_linear_history=false`. Only `allow_force_pushes=false` is set correctly. This is the direct cause of the "quality-first merges need strengthening" note.
2. **Explicit-policy drift** — `.github/pull_request_template.md:24,27` say JaCoCo ≥95% while the real gate is **98%** (ci.yml, `workflow.md`, `politicas §6`, ADR-0029). The template is also still Portuguese while `politicas` was moved to English.
3. **No CODEOWNERS** — the "collective ownership / human-reviewed" claim is unformalized.
4. **Merge method is undocumented/unenforced** — history mixes merge-commits (#179–181) and squash (recent).

## 3. Evolutionary Change — 8.8

**Note on the score:** *"Incremental workflow in place; explicit policies could strengthen approach."*

**Present (positive):** the `[N]/[M]/[E]` taxonomy; J-Curve safety limits; 1-gap-per-session; ADR-before-structural — all documented *and* demonstrated in history.

**Findings:**

1. **No flow-feedback layer.** The `xp-kanban` skill's management practices (cadences, CFD, lead-time histogram) and the evolutionary-change "PR reviewed in <48h" signal have no corresponding metric anywhere in the repo. This is the "shallow vs mature" line the skill itself draws.
2. **The ≤400-line J-Curve limit is honor-system** — `politicas §6` labels it "Heuristic"; nothing measures it (e.g. #246 +426/-27 and #250 +331/-42 breached it, unflagged).
3. **No revert-on-red / rollback procedure** — `politicas` covers "regression → new card" but not how to back out a merge that broke `main`.

## 4. SOLID — 8.9

**Note on the score:** *"SRP/DIP applied but room for refinement across modules."* DIP and ISP are strong and Konsist-enforced; the "room" is small and localized.

**Findings:**

1. **Dead ports+adapters** (medium): `BoardRepository`/`CardRepository`/`StepRepository` + `JdbcBoardRepository`/`JdbcCardRepository`/`JdbcStepRepository` have **zero production consumers** (not in `AppModule`, not injected) — referenced only by `CoreRepositoriesIntegrationTest`/`RepositoriesErrorHandlingTest`. Unused abstractions carried for tests only.
2. **Non-exhaustive `else` whens over the `Decision` sealed type** (low, OCP safety): `SimulationSerializerSnapshotMappings.kt:15-27` (encode `else -> error(...)`) and `SimulationDtos.kt:311-318` (`DecisionRequest.toDomain()` string-keyed `else`). A new `Decision` variant fails at runtime, not compile time.
3. **`OpenApi.kt:13` `@Suppress("LongMethod")`** (low): the one genuine length-debt suppression — a long OpenAPI config method that could be decomposed. (`SimulationRoutes.kt`, 319 lines, mixes OpenAPI spec DSL with handlers — a candidate split, but within Detekt thresholds.)

**Clean, do-not-over-report:** DIP is exemplary and machine-enforced (single composition root, zero leakage); ISP ports are small and role-specific; domain purity is enforced; no unsafe casts; the `SimulationEngine` logic concentration is within thresholds (watch-only if simulation rules grow).

---

## Candidate gaps (ordered on board #6 — leverage × cheapness)

Execution order and progress live on [board #6](https://github.com/users/agnaldo4j/projects/6), one gap per session (WIP 1). This list is the audit output, not a progress record.

| Gap | Type | Dimension(s) | Scope (one focused PR each) |
|---|---|---|---|
| **GAP-BD** — explicit-policy consistency | `[N]` | XP/Kanban, Evolutionary | PR template ≥95%→≥98% (+ EN); add CODEOWNERS; document merge method = squash + revert-on-red procedure in `politicas-explicitas.md`. |
| **GAP-BE** — SOLID: exhaustive sealed dispatch | `[N]` | SOLID | Convert the two `else`-terminated `Decision` whens to compiler-exhaustive `when`. |
| **GAP-BF** — resolve dead ports/adapters | `[M]` (decision) | SOLID, Modular | Wire Board/Card/Step ports behind a real Board-Management use case+route, **or** remove the unused port+adapter+tests. |
| **GAP-BG** — context-boundary fitness function | `[M]` | Modular Monolith | New Konsist rule enforcing the documented context map (allowed one-way `simulation→kanban`, forbid the reverse and new cross-context coupling). |
| **GAP-BH** — CI PR-size soft-gate | `[M]` | Evolutionary | Non-blocking label/warn when a PR exceeds 400 changed lines (excluding generated reachability metadata). |
| **GAP-BI** — flow-metrics cadence | `[M]` | Evolutionary, XP/Kanban | First flow signal: `docs/quality/flow-*.md` snapshot (PR cycle time / lead time / WIP-aging) + a per-cycle refresh policy. |

## Owner action item (not a gap — repo Settings)

**Enable branch protection on `main`** — the single biggest lever for XP/Kanban (8.7) and Evolutionary Change (8.8); it converts "green-before-merge / all-work-via-PR" from manual trust to enforcement:

- Required status checks (strict): `Quality Gates`, `Supply Chain (SBOM + SCA)`, `Build & Push Image`.
- Require a PR before merging; ≥1 approving review.
- Require linear history (pairs with the squash policy in GAP-BD).
- Keep `allow_force_pushes=false`; enable `enforce_admins` to bind admins too.

> Method note: this audit uses named findings with evidence rather than 0–10 scores, consistent with `audit-2026-07.md` (ISO 25010). The 8.x figures are the Wiki 18-skill scorecard values this analysis was scoped against.
