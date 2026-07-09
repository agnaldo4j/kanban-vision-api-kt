# Explicit Policies & Contributor Guide — Kanban Vision API

> **Kanban principle:** invisible policies create unpredictable behaviour. Explicit, visible
> policies let any team member — human or AI agent — make consistent decisions without asking
> every time.

This document has two jobs:

1. **Learn from the project** — a guided tour of what this codebase demonstrates and how to study it.
2. **Contribute to the project** — the explicit policies that govern how work flows, from pulling a
   card to merging a PR.

It is the **canonical source of truth** for how work happens here, referenced from `CLAUDE.md` and
`README.md`. Any change goes through a PR with an explicit rationale (see §11).

> The deep technical reference lives in the [project wiki](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki);
> decisions live in [`adr/`](../adr); progress lives on [GitHub Project #6](https://github.com/users/agnaldo4j/projects/6).
> This file is the *process and orientation* layer that ties them together.

---

## 1. Start here — orient yourself

If you are new, read in this order. Each step builds the mental model for the next.

1. **[README](../README.md)** — one-screen overview, tech stack, quick start.
2. **[Wiki → Architecture](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Architecture)** — the C4 view, the five modules, the Dependency Rule.
3. **Run it locally** — `GRAFANA_ADMIN_PASSWORD=admin docker compose up --build`, open Swagger at `http://localhost:8080/swagger`, and drive one simulation end to end. For a fast JVM loop see the [Development Guide](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Development-Guide).
4. **Read one vertical slice** — follow a single request through the layers: a route in `http_api/…/routes/SimulationRoutes.kt` → a use case in `usecases/…/simulation/` → the domain aggregate/service in `domain/…/model/` → a repository in `sql_persistence/…/repositories/`.
5. **This document** — the policies below, so your first PR fits the flow.

### The mental model in one paragraph

A **Kanban flow simulator** exposed as a versioned REST API. The core is a **pure domain** (`Board` and `Simulation` aggregates, a deterministic `SimulationEngine` service) with **zero framework dependencies**. Application logic is **CQS use cases** returning `Either<DomainError, T>`. Adapters (Ktor HTTP, JDBC/Exposed persistence) sit at the edges. Everything the architecture promises is **enforced by tests** (Konsist fitness functions) and **verified in CI** (coverage, mutation, static analysis, supply-chain scanning). Production ships as a **GraalVM Native Image**.

---

## 2. What this project demonstrates (and how to study it)

This is a reference implementation for a broad set of engineering practices. Each row below is a
capability you can learn here, with a pointer to where to look. This is the "evaluate all the
qualities" map — use it to decide what to study or where to contribute.

| Quality | What to learn | Where to look |
|---|---|---|
| **Clean / Hexagonal Architecture** | Strict Dependency Rule, ports & adapters, layer boundaries that don't erode | `architecture/` fitness tests · [Wiki → Architecture](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Architecture) |
| **Domain-Driven Design** | Rich aggregates with invariants, value objects, a pure domain service, sealed domain errors/events, ubiquitous language | `domain/src/main/…/model/` · [Wiki → Architecture Domain](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Architecture-Domain) · `/ddd` skill |
| **Functional + OO Kotlin** | `Either`/`Raise`, immutability, pure functions, sealed hierarchies over null-guards, deterministic engine pipeline | Arrow-kt usage across `usecases/` and `domain/` · ADR-0018 · `/fp-oo-kotlin` skill |
| **Testing rigour** | A real test pyramid: pure unit, property-based (Kotest), integration (embedded Postgres), route (`testApplication`), contract (Pact) | `*/src/test/` · [Wiki → Development Guide](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Development-Guide) |
| **Mutation testing** | Using PITest to measure *test effectiveness*, not just coverage | `*/build.gradle.kts` PITest config · [Wiki → Quality Analysis](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Quality-Analysis) |
| **Architecture fitness functions** | Encoding architecture rules as executable Konsist tests that fail CI | `architecture/src/test/` · [Wiki → Fitness Functions](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Architecture-Fitness-Functions) · ADR-0026 |
| **Observability** | Metrics (Micrometer/Prometheus), traces (OTel SDK, **no javaagent**), structured logs with trace correlation, health checks | `http_api/…/plugins/Telemetry.kt`, `Metrics.kt` · [Wiki → Observability](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Observability) · ADR-0031 |
| **Supply-chain security** | CycloneDX SBOM + osv-scanner SCA as a blocking gate, with a disciplined exception policy | `osv-scanner.toml` · [Wiki → Security Supply Chain](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Security-Supply-Chain) · ADR-0025 |
| **GraalVM Native Image** | AOT compilation, reachability metadata, the JIT↔AOT trade-off, a native migration binary | `Dockerfile` · [Wiki → GraalVM](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/GraalVM) · ADR-0030/0032 |
| **The JVM toolchain** | One-JDK model (Java 25), a Gradle convention plugin, configuration cache | `buildSrc/` · [Wiki → JVM](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/JVM) · ADR-0024 |
| **Performance engineering** | A k6 load journey, an immutable-baseline policy, runtime comparisons | `load/` · `docs/quality/` · [Wiki → Performance Load Testing](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Performance-Load-Testing) · ADR-0027 |
| **Decision records & evolutionary change** | MADR ADRs, immutability, one-gap-per-session, J-curve safety | [`adr/`](../adr) · ADR-0023 · `/evolutionary-change` skill |

> **How to read a decision:** every non-trivial choice has an ADR in `adr/` (MADR format). Start
> from the ADR to understand *why*, then follow its *Confirmation* section to the gate that keeps
> the decision true. ADRs are immutable — a changed decision is a new ADR that supersedes the old.

---

## 3. The board — step criteria

Progress is tracked only on [GitHub Project #6](https://github.com/users/agnaldo4j/projects/6) (ADR-0023).

### Backlog
**Entry:** anything identified as a gap, improvement or task, regardless of priority.
**Exit to Todo** requires all of:
- [ ] Scope defined (1–2 clear sentences)
- [ ] Type classified: `[N]` normative, `[M]` medium, `[E]` structural (see §6)
- [ ] If `[E]`: an ADR with status `accepted` already exists
- [ ] Prerequisite items completed

### Todo
**WIP:** no fixed limit — but only the item **at the top** is next to pull.
**Order:** items are prioritised top-to-bottom. Top = highest priority; the order *is* the plan.

### Doing
**WIP limit: 1** — never more than one item in Doing at a time.
**Entry:** pulled from the top of Todo at the start of a session.
**Exit to Done:** PR merged to `main` + branch deleted + local `main` updated.
**Blocked:** if an item stalls, document the blocker on the card and move it back to Todo — never leave it parked in Doing.

### Done
**Entry:** PR merged + branch deleted + local `main` updated.
**Immutable:** Done items never move back. A regression becomes a *new* card.

---

## 4. Pull policy — how to start work

```
START OF A WORK SESSION:
  1. gh project item-list 6 --owner agnaldo4j   → is there an item in Doing?
  2. If Doing has an item      → continue that item (do not start a new one)
  3. If Doing is empty         → pull the FIRST item from the top of Todo
  4. Move the item to Doing    (update GitHub Project via gh api graphql)
  5. Branch from an updated main: git checkout -b feat/gap-X-slug
  6. Re-read CLAUDE.md + the target files before writing any code
```

**Golden rule:** never pick an item because it looks easier or more interesting. Always the **top of
Todo** — the priority was already decided. (Board and field IDs are in `.claude/rules/workflow.md`.)

---

## 5. Closing policy — how to finish work

```
AFTER THE PR IS MERGED:
  1. Confirm the merge:  gh pr view <N> --json state,mergedAt   → state=MERGED, mergedAt not null
  2. git checkout main && git pull origin main
  3. git branch -d feat/gap-X-slug
  4. git push origin --delete feat/gap-X-slug   (if the remote branch still exists)
  5. Move the card Doing → Done (via gh api graphql)
```

> **Never delete the remote branch of a PR that is not confirmed merged** — deleting the branch of
> an *open* PR closes it without merging. Always verify `state=MERGED` and a non-null `mergedAt`
> first; the merge commit lands on `main`, so a `git pull` that stays "up to date" is a warning sign.

Board #6 is the **only** source of truth for progress (ADR-0023). Never record progress in ADRs —
they are immutable once accepted.

---

## 6. Quality gates (J-curve safety limits)

These are **non-negotiable** and enforced automatically. No PR merges if any is violated.

| Policy | Limit | Enforced by |
|---|---|---|
| JaCoCo instruction coverage | **≥ 98%** per module (ADR-0029) | CI — blocks merge |
| Detekt violations | 0 (`warningsAsErrors = true`) | CI — blocks merge |
| KtLint formatting | 0 errors | CI — blocks merge |
| `./gradlew testAll` | green | CI — blocks merge |
| PITest mutation score | `domain` **≥ 78** · `usecases` **≥ 55** · `sql_persistence` **≥ 65** · `http_api` **≥ 45** | CI (`domain`+`usecases` mandatory) |
| Architecture fitness functions (Konsist) | all green (ADR-0026) | CI — via `testAll` |
| Supply-chain SCA (osv-scanner over the SBOM) | 0 untreated CVEs; exceptions only with a `reason` in `osv-scanner.toml` (ADR-0025) | CI — `supply-chain` job blocks the image |
| PR size | ≤ 400 changed lines | Heuristic — split if larger |
| WIP (items in Doing) | max 1 | Pull policy |
| Gaps per session | max 1 | Session protocol |

**Absolute rule:** never edit `detekt.yml`, `.editorconfig`, `build.gradle.kts`, `gradle.properties`
or the convention plugin to bypass a violation. **Fix the code.** If a threshold genuinely needs to
change, that is a decision — open an ADR (e.g. ADR-0029 raised coverage from 97% to 98%).

---

## 7. ADR policy

> Source: [ADR-0023](../adr/ADR-0023-politica-adrs-imutabilidade-madr.md) — immutability + MADR 4.0.

| Gap type | Policy |
|---|---|
| `[N]` Normative | Execute directly. No ADR required. |
| `[M]` Medium | 1 design session + 1 focused PR. ADR recommended if it introduces a new concept. |
| `[E]` Structural | ADR with status `accepted` **before any code**. No ADR ⇒ it stays in Backlog. |

**Form & lifecycle (ADR-0023):**
- **Immutable** — an accepted ADR is never edited; a changed decision is a *new* ADR that supersedes it (the old one only gains a `superseded by ADR-XXXX` line).
- **One decision per ADR**, ~1 page, only architecturally significant choices.
- **MADR 4.0 template**, with a *Confirmation* section pointing at the gate that verifies the decision.
- **Layer separation** — decisions in `adr/` · planning on Project #6 · measurement in CI + `docs/quality/`. An ADR links to these; it never contains checklists, scores, or execution order.

---

## 8. Architecture policy

| Policy | Rule |
|---|---|
| Dependency Rule | `http_api → usecases → domain`; `sql_persistence → domain/usecases`; `http_api → sql_persistence` for DI wiring only. Imports point inward. |
| Domain purity | Zero framework imports in `domain/` (Ktor, Koin, Exposed, JDBC, serialization, logging, …). |
| Ports location | Repository interfaces live in `usecases/repositories/` — **never** in `domain/`. |
| Adapter isolation | Concrete `Jdbc*`/`Exposed*` repositories may be imported only in `di/AppModule.kt` (Konsist `ConventionsTest`, ADR-0028). Detekt `ForbiddenImport` covers security imports (`ObjectInputStream`, `MessageDigest`). |
| CQS | Each use case takes exactly one `Command` (mutates) or `Query` (reads) and exposes `execute(...): Either<DomainError, T>` — never loose primitives. |
| Typed errors | Errors are `Either<DomainError, T>` — no exceptions for control flow. |
| Aggregate Root | Use cases don't enforce invariants directly — they delegate to the aggregate (`Board`, `Simulation`). |
| Board hydration | `JdbcBoardRepository.findById()` returns a `Board` with `steps = emptyList()`. Use cases must hydrate (`board.copy(steps = …)`) before calling `addStep`/`addCard`. |

All of the first four are **enforced by Konsist fitness functions** in `architecture/` — a violation
fails CI, not just review. See [Wiki → Fitness Functions](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Architecture-Fitness-Functions).

---

## 9. Branch policy

| Convention | Pattern |
|---|---|
| Gap branch | `feat/gap-X-slug` (e.g. `feat/gap-j-pagination`) |
| ADR branch | `feat/adr-NNNN-slug` |
| Fix branch | `fix/short-description` |
| Docs branch | `docs/short-description` |
| Base | Always from an updated `main` (`git checkout main && git pull origin main`) |
| Lifetime | Delete immediately after a **confirmed** merge (see §5) |
| Direct push to `main` | **NEVER** — all work goes through a PR |
| Force push to `main` | **NEVER** |

Merges are always **manual and human-reviewed** — including Dependabot PRs. When a Dependabot PR
needs a human fix, push the commit onto its branch directly and do **not** comment `@dependabot
rebase` afterwards (it discards the commit).

---

## 10. Session & communication policy

| Situation | Policy |
|---|---|
| Context | Re-read `CLAUDE.md` + the target files at the start of every session |
| Scope | Implement only the planned gap — no "while we're here" changes |
| File spread | If a PR touches > 5 files across distinct layers, stop and split |
| Definition of Done | Verify the DoD before marking anything complete (`/definition-of-done` skill) |
| Architecture change | Open an ADR *before* implementing — never surprise reviewers in the PR |
| Regression found | Create a card immediately — never ignore it |
| Technical blocker | Document it on the card and move it back to Todo — never park it in Doing |
| Policy disagreement | Change this file via PR — policy changes by consensus, not silently |

---

## 11. How to contribute — a checklist

1. **Pick work from the top of Todo** (§4) — or open an issue/card first if you're proposing something new. Structural (`[E]`) work needs an accepted ADR before code (§7).
2. **Branch** from an updated `main` (§9).
3. **Build the mental model** — read the relevant vertical slice (§1) before writing code.
4. **Write the code and its tests together** — match the surrounding style; new behaviour needs unit + (where it applies) property/integration/route tests. Keep the domain pure.
5. **Run the gates locally** before pushing:
   ```bash
   ./gradlew ktlintFormat        # auto-fix formatting
   ./gradlew testAll             # Detekt + KtLint + JaCoCo 98% + tests + Konsist
   ./gradlew pitestAll           # mutation gates (slower; CI runs domain+usecases)
   ```
6. **Open a PR** ≤ 400 lines with a clear rationale. CI runs `quality`, `supply-chain`, and `build` (with an image smoke test). Address review comments; keep threads resolved.
7. **After a confirmed merge**, close out (§5): delete the branch and move the card to Done.

> Quality thresholds and config files are never edited to pass a gate (§6). If a gate is genuinely
> wrong for a change, that's a conversation — raise it in the PR or an ADR, don't work around it.

---

## References

- Wiki: [Home](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki) · [Development Guide](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Development-Guide) · [Quality Analysis](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Quality-Analysis)
- Skills: [`xp-kanban`](../.claude/skills/xp-kanban/SKILL.md) · [`evolutionary-change`](../.claude/skills/evolutionary-change/SKILL.md) · [`definition-of-done`](../.claude/skills/definition-of-done/SKILL.md)
- [GitHub Project #6](https://github.com/users/agnaldo4j/projects/6) · [ADR index](../adr) · `CLAUDE.md` (Kanban Board Protocol)
