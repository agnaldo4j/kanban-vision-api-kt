# Quality Scorecard — July 2026 (re-score after GAP-BD..BI)

Full re-score on `main` @ `f157674`, after the GAP-BD..BI cycle (PRs #261–#266) closed the four
dimensions that had been below 9.0. Each score was re-assessed against the current codebase with
the corresponding `/skill` rubric and the live CI signals. This pass also **adds four previously
untracked dimensions** (Security, Performance efficiency, GraalVM, Circular-dependency control) —
skills that exist in `.claude/skills/` but were absent from the scorecard — taking it from 18 to
**22 skills**. This file is the in-repo, immutable snapshot (ADR-0023) and the source for the wiki
`Quality-Analysis` page.

## Overall — 9.12 / 10

**Method (reproducible):** the overall is the **simple arithmetic mean of the dimension scores** —
recomputable from the table (`200.7 / 22 = 9.12`). The previously-published *9.4* was an editorial
figure, not a mean.

Two things move the headline this cycle, in opposite directions:
- The GAP-BD..BI improvements raise the **18-skill** transparent mean **9.16 → 9.30**.
- **Adding the four untracked dimensions** (avg 8.33) lowers the **22-skill** mean to **9.12** —
  the scorecard's blind spots (Circular-dependency 7.8, Performance 8.0, Security 8.5) were
  flattering the average by omission. This is the scorecard becoming honest, not a regression.

> Overlap note: **GraalVM** overlaps Infra + Performance, and **Circular-dependency control**
> overlaps Clean Architecture + Refactoring (both reviewers flagged shared evidence). Excluding
> those two overlapping rows, the **20-skill** mean is **9.20** — a useful reference for readers
> who prefer non-overlapping dimensions.

## 22-skill scorecard

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
| 19 | owasp | **Security** | — | **8.5** | new |
| 20 | load-testing | **Performance efficiency** | — | **8.0** | new |
| 21 | graalvm | **GraalVM / Native runtime** | — | **9.0** | new |
| 22 | circular-dependency-control | **Circular-dependency control** | — | **7.8** | new |

**Mean = 200.7 / 22 = 9.12.** Seven dimensions rose; eleven held; four added.

## What moved, and why (evidence)

- **XP/Kanban 8.7 → 9.4** — the "manual trust, not machine-enforced" cap is gone: `main` branch
  protection is now enabled (3 required status checks, `enforce_admins`, linear history;
  `approvals=0` is a deliberate solo-repo choice). GAP-BD fixed policy drift, added CODEOWNERS and
  documented squash-only + revert-on-red; GAP-BH/BI added machine flow signals.
- **SOLID 8.9 → 9.4** — both named debts cleared: GAP-BF removed the dead Board/Card/Step
  ports+adapters (zero references remain), GAP-BE made the `Decision` write-path `when` exhaustive
  with compile-anchored safety-net tests.
- **Microservices/Modular 8.4 → 8.8** — the named cap (no context-boundary fitness function) is
  closed by GAP-BG's `ContextBoundaryTest`; GAP-BF removed dead cross-context ports. Still below
  9.0 by design — see residual debt.
- **Evolutionary Change 8.8 → 9.2** — the flow-feedback layer now exists (GAP-BI); PR-size is
  machine-measured (GAP-BH); revert-on-red documented (GAP-BD). Held below 9.3: single baseline,
  refreshed manually.
- **DDD 9.6 / Refactoring 9.2 / DoD 9.2** — ports only for aggregate roots (GAP-BF); −605 lines of
  dead code removed (GAP-BF); PR-template/CI gate alignment + CODEOWNERS (GAP-BD).

### New dimensions added (were untracked)
- **Security 8.5** — defense-in-depth, CI-enforced: JWT Bearer, all `/api/v1` under
  `authenticate("jwt-auth")`, Exposed DSL (no raw SQL), fail-closed StatusPages, blocking
  osv-scanner SCA + CycloneDX SBOM (ADR-0025), Detekt ForbiddenImport SAST, `guard-security.sh`
  hook. Below 9 by concrete residuals (see candidate gaps).
- **Performance efficiency 8.0** — reproducible k6 baseline (p95 ~22 ms, ~1,644 req/s, 0% failures;
  ADR-0027) + four comparative runtime baselines; GraalVM native (~0.12 s startup). Capped: single
  point-in-time baseline, manual (not a PR gate), no stress/soak/spike profiles.
- **GraalVM / Native runtime 9.0** — Native Image is the production artifact (app + migration
  binaries, ADR-0030→0032), disciplined reachability metadata, measured ~9× startup / −79–94% mem.
  Overlaps Infra + Performance (evidence shared). Native-path debt remains.
- **Circular-dependency control 7.8** — Gradle/layer/context cycles structurally prevented
  (Konsist directional rules + `ContextBoundaryTest` + DIP ports). A dedicated whole-graph
  package-cycle detector now exists (`PackageCycleTest`, GAP-BN), which surfaced and fixed a real
  intra-module `plugins ↔ routes` cycle (shared helpers extracted to a neutral `support` package).
  The organization/simulation relationship is **one-way `simulation → organization` (a DAG, not a
  cycle)** — the `↔` framing was stale. Residual cap: class↔class cycles within a single package are
  not analysed (import-based, package granularity). Overlaps Clean Architecture + Refactoring.

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
- **Performance:** k6 baseline p95 ~22 ms, ~1,644 req/s (ADR-0027, manual).
- **Process:** `main` branch protection on (3 blocking checks, enforce_admins, linear history);
  CI jobs `quality` · `supply-chain` · `pr-size` (non-blocking) · `flow-metrics` (non-blocking) ·
  `build`.
- **Runtime:** GraalVM Native Image — ~0.12 s startup, ~74 MiB RSS (ADR-0030→0032).

## Residual debt & candidate gaps for the next cycle

The lowest dimensions are now the honestly-surfaced ones — strong candidates for GitHub Project #6
(ADR-0023: the board is the single source of progress; this snapshot records state, it does not
schedule work):

- **Security (8.5) — highest-value candidates:** JWT `validate{}` verifies only the audience, not
  an `organizationId` claim, and use cases have **no caller-vs-resource ownership check** →
  IDOR / cross-tenant risk. Swagger UI (`/swagger`, `/api.json`) is mounted **unconditionally**
  (not gated by `ENABLE_SWAGGER`). No HSTS header. Rate limit keyed on a spoofable first
  `X-Forwarded-For`; no stricter `/auth/*` limit.
- **Circular-dependency (7.8):** ✅ addressed by GAP-BN — Konsist `PackageCycleTest` added (whole
  graph); the `plugins ↔ routes` cycle it found was fixed; organization/simulation confirmed one-way.
- **Performance (8.0):** no automated regression signal (single baseline); add stress/soak/spike
  profiles.
- **GraalVM (9.0):** `DomainErrorResponse` serialization fails on the native **error path**
  (reachability-metadata gap — previously noted, still uncarded); native all-route smoke is manual.
- **Microservices (8.8):** intentional `[E]` structural debt — the two contexts share one `domain`
  module (Scenario holds a concrete `Board`; one unified schema). Raising it needs an ADR-approved
  extraction, not a quick fix.
- **Doc hygiene:** stale references to the removed `JdbcBoardRepository` in
  `docs/politicas-explicitas.md` and `.claude/rules/architecture.md`.
