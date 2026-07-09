# Quality Scorecard — July 2026 (re-score after GAP-BD..BI)

Full re-score of the 18-skill Quality-Analysis scorecard on `main` @ `f157674`, after the
GAP-BD..BI cycle (PRs #261–#266) closed the four dimensions that had been below 9.0. Each score
was re-assessed against the current codebase with the corresponding `/skill` rubric and the live
CI signals. This file is the in-repo, immutable snapshot (ADR-0023) and the source for the wiki
`Quality-Analysis` page.

## Overall — 9.30 / 10

**Method (new, reproducible):** the overall is the **simple arithmetic mean of the 18 dimension
scores** — recomputable by anyone from the table below. The previously-published *9.4* was an
editorial figure, not the mean (the mean of the prior scores was **9.16**). Adopting the
transparent mean, the improvements move the headline **9.16 → 9.30**.

## 18-skill scorecard

| # | Skill | Dimension | Prev | New | Δ |
|---|---|---|---|---|---|
| 1 | ddd | Domain modeling | 9.5 | **9.6** | +0.1 |
| 2 | clean-architecture | Layering | 9.5 | 9.5 | — |
| 3 | screaming-architecture | Package intent | 9.0 | 9.0 | — |
| 4 | solid-principles | SOLID | 8.9 | **9.4** | +0.5 |
| 5 | fp-oo-kotlin | Functional Kotlin | 9.5 | 9.5 | — |
| 6 | refactoring | Code health | 9.0 | **9.2** | +0.2 |
| 7 | testing-and-observability | Test quality | 9.7 | 9.7 | — |
| 8 | kotlin-quality-pipeline | CI gates | 9.7 | 9.7 | — |
| 9 | openapi-quality | API docs | 9.0 | 9.0 | — |
| 10 | db-migrations | Schema | 9.1 | 9.1 | — |
| 11 | microservices-modular-monolith | Boundaries | 8.4 | **8.8** | +0.4 |
| 12 | local-and-production-environment | Infra | 9.3 | 9.3 | — |
| 13 | opentelemetry | Observability | 9.2 | 9.2 | — |
| 14 | evolutionary-change | Change mgmt | 8.8 | **9.2** | +0.4 |
| 15 | adr | Decision records | 9.6 | 9.6 | — |
| 16 | c4-model | Architecture docs | 9.0 | 9.0 | — |
| 17 | xp-kanban | Engineering practices | 8.7 | **9.4** | +0.7 |
| 18 | definition-of-done | DoD | 9.0 | **9.2** | +0.2 |

**Mean = 167.4 / 18 = 9.30.** Seven dimensions rose; eleven held with no regression.

## What moved, and why (evidence)

- **XP/Kanban 8.7 → 9.4** — the "manual trust, not machine-enforced" cap is gone: `main` branch
  protection is now enabled (3 required status checks, `enforce_admins`, linear history;
  `approvals=0` is a deliberate solo-repo choice). GAP-BD fixed policy drift, added CODEOWNERS and
  documented squash-only + revert-on-red; GAP-BH/BI added machine flow signals.
- **SOLID 8.9 → 9.4** — both named debts cleared: GAP-BF removed the dead Board/Card/Step
  ports+adapters (zero references remain), GAP-BE made the `Decision` write-path `when` exhaustive
  with compile-anchored safety-net tests.
- **Microservices/Modular 8.4 → 8.8** — the named cap (no context-boundary fitness function) is
  closed by GAP-BG's `ContextBoundaryTest` (enforces one-way `simulation → kanban`); GAP-BF removed
  dead cross-context ports. **Still below 9.0** by design — see residual debt.
- **Evolutionary Change 8.8 → 9.2** — the flow-feedback layer now exists (GAP-BI:
  `scripts/flow-metrics.sh` + `flow-2026-07.md` + non-blocking `flow-metrics` CI job + Grafana
  panel); PR-size is machine-measured (GAP-BH `pr-size`); revert-on-red documented (GAP-BD). Held
  below 9.3: a single baseline point, refreshed manually.
- **DDD 9.6 / Refactoring 9.2 / DoD 9.2** — repository ports now exist only for aggregate roots
  (GAP-BF); −605 lines of dead code removed (GAP-BF); PR-template/CI gate alignment + CODEOWNERS
  (GAP-BD).

Gap → dimension map: BD → XP/Kanban + Evolutionary + DoD · BE → SOLID · BF → SOLID + Modular +
Refactoring + DDD · BG → Modular · BH → Evolutionary · BI → Evolutionary + XP/Kanban.

## Key metrics (current)

- **Tests:** 488 across the four product modules + Konsist; pyramid = unit · Kotest property ·
  embedded-Postgres integration · `testApplication` route · Pact contract.
- **Coverage:** JaCoCo gate **≥ 98%/module** (ADR-0029); measured overall ~99%.
- **Mutation (PITest, STRONGER):** domain 82 (gate 78) · usecases 60 (55) · sql_persistence 72
  (65) · http_api 51 (45).
- **Static analysis:** Detekt 2.0.0-alpha.5 `warningsAsErrors`, **0 violations** · KtLint 1.5.0, 0.
- **Architecture fitness:** Konsist **14/14** fitness functions (Hexagonal, Ports placement,
  Conventions, Domain purity, Context boundary).
- **Supply chain:** CycloneDX SBOM + osv-scanner SCA, **blocking** (ADR-0025); 1 documented exception.
- **Process:** `main` branch protection on (3 blocking checks, enforce_admins, linear history);
  CI jobs `quality` · `supply-chain` · `pr-size` (non-blocking) · `flow-metrics` (non-blocking) ·
  `build`.
- **Runtime (prior baselines):** GraalVM Native Image — ~0.12 s startup, ~74 MiB RSS (ADR-0030→0032).

## Residual debt & candidate gaps for the next cycle

The only dimension still below 9.0 is **Microservices/Modular (8.8)**: the two bounded contexts
share one `domain` module as a coarse shared kernel with compile-level cross-context references
(`Scenario` holds a concrete `Board`; `SimulationEngine` imports kanban types; one unified DB
schema with cross-context FKs). This is **intentional, deferred `[E]` structural debt** — raising
it requires an ADR-approved extraction (context module split / ACL), a genuine candidate for a
future cycle, not a quick fix.

Other watch-items surfaced by the review (candidates, not executed here):
- Primitive-obsession on identity — entity IDs are raw `String` rather than typed `@JvmInline`
  value objects (`BoardId`/`CardId`) — DDD.
- `OpenApi.kt:13` `@Suppress("LongMethod")` lacks the justifying comment the quality rule requires.
- No automated dead-code detector to prevent port/adapter dead-code recurrence — Refactoring.
- Stale doc references to the removed `JdbcBoardRepository` in `docs/politicas-explicitas.md` and
  `.claude/rules/architecture.md` "Known Pitfalls" — doc hygiene.
- k8s manifests not validated by a CI gate (kubeconform); no image-layer vuln scan (SCA is
  SBOM-based only) — Infra.

> These candidates belong on GitHub Project #6 as future gaps (ADR-0023: the board is the single
> source of progress). This snapshot records the state; it does not schedule the work.
