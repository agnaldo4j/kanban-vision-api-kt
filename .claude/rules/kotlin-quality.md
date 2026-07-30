---
paths:
  - "**/*.kt"
  - "**/*.gradle.kts"
---

# Kotlin Quality Pipeline

All quality tools run via `./gradlew testAll`. **Never edit** `detekt.yml`, `.editorconfig`, `build.gradle.kts`, `gradle.properties`, or the convention plugin to bypass violations — fix the code.

## Tools

| Tool | Config | Key thresholds |
|---|---|---|
| Detekt 2.0.0-alpha.5 | `config/detekt/detekt.yml` — `warningsAsErrors = true` | Cyclomatic complexity 10, max line 140, max functions/class 15, max lines/class 200 |
| KtLint 1.5.0 | Kotlin official style | `./gradlew ktlintFormat` auto-fixes |
| JaCoCo | 98% minimum instruction coverage per module (ADR-0029) | Build fails if not met |

## Rules

- `@Suppress` only with a comment justifying why — no justification = PR rejected.
- **Comments are for two cases only: genuinely complex code, and a deliberate choice of performance over
  clean code.** Everything else is expressed by **screaming architecture and clean code** — the name, the
  type, the structure. A comment that restates a name, narrates history, or repeats what a test enforces is
  not neutral: it rots, drifts out of sync, and the next reader trusts it over the code.

  **When a comment carries real information, the fix is not to keep it — it is to make the CODE say it:**

  | Instead of commenting | Do this |
  |---|---|
  | *"the `copy(step = step.id)` re-stamps the card with the step that holds it"* | extract `Step.cardsStampedWithOwningStep()` — the name says it |
  | *"`steps` arrives already ordered, do not re-sort"* | rename the parameter to `stepsInExecutionOrder` |
  | *"BLOCKED because it is the only state inert to the engine"* | name the constant `QUARANTINE_CARD_STATE` |
  | *"the reason is generic so it does not leak existence"* | write the test: `…then the reason leaks no identifier` |

  The last row is the general escape: **a constraint worth a comment is usually worth a test**, and the test
  cannot rot. In #387 the same instruction was written *both* as a KDoc line and as a Konsist fitness
  function — the function is executable, the comment was decoration.

  Measured on this repo (#390), across five production files touched in one session: **from 129 comment
  lines to one.** The single survivor is the one true performance trade — `Board.itemCount()` not delegating
  to `allCards().size`, which would allocate a copy per card just to count. Everything else became a name
  (`cardsStampedWithOwningStep`, `stepsInExecutionOrder`, `QUARANTINE_CARD_STATE`,
  `CROSS_FIELD_NEUTRAL_ABILITY`, `withIdentitiesOwnedByRow`) or a test. **No test changed expectation** —
  which is the proof that the prose was not carrying behaviour.

  Two survivors were cut on a second pass after the maintainer rejected the rationale: comments explaining a
  less-clean form chosen for a **coverage-tool** constraint (`when` arm ordered first for JaCoCo, `onRight`
  instead of `getOrNull`). Tool constraints are **not** the performance exception — what protects those
  shapes is the gate itself, not prose beside them.
- `LargeClass` threshold: 200 lines — of the class **body**, not of the file: blanks and comments do not count. In #388 a **240-line** test file sat comfortably under the limit (~174 body lines excluding blanks/comments), so `wc -l` alarms in the wrong direction. **Confirm by running Detekt on the source set the file belongs to**, in either direction:
  ```bash
  ./gradlew :<module>:detektMain --rerun-tasks   # produção
  ./gradlew :<module>:detektTest --rerun-tasks   # testes
  ./gradlew :<module>:detekt     --rerun-tasks   # ambos, quando em dúvida
  ```
  ⚠️ **Pick the task that matches the file, or the check is a false green.** `detektTest` analyses only the *test* source set: run it against a production class near the threshold and it passes without ever looking at that class — the same "green because nothing was examined" failure this rule exists to prevent. `--rerun-tasks` matters too: without it an UP-TO-DATE task reports the previous run. (Codex P2 on #389, on the very amendment that introduced the command.)
  Split test files into focused classes (e.g., `ScenarioCreationRoutesTest`, `ScenarioRunDayRoutesTest`) — see `.claude/rules/testing.md` for the fixture caveat.
- Zero Detekt violations before opening a PR.
- Run `./gradlew ktlintFormat` before committing.

## Kotlin-Specific Pitfalls

- **`@JvmInline value class` + MockK**: `any()` typed matcher (e.g., `any<ScenarioId>()`) may fail at runtime. Use specific values or untyped `any()` for inline value class parameters.
- **`raise()` in `either {}`**: member of `Raise<E>` — available implicitly inside `either {}`. Do NOT import `arrow.core.raise.raise`.
- **Kotlin serialization plugin**: applied without version in `http_api` and `sql_persistence` because the plugin is already on the classpath from `buildSrc`.
- **`/*` inside a KDoc opens a *nested* block comment**: Kotlin supports nested block comments, so a `/*` in doc text — e.g. a backticked glob `` `pkg/sub/**` `` (the `/**`) — starts a comment that never closes → `Unclosed comment` at EOF. Avoid `/*` sequences in doc/KDoc; write `pkg.sub`, not `` `pkg/sub/**` ``. (GAP-BZ/#325 — this, not explicit type args, was the real cause of that build's `Unclosed comment`. Explicit type args on a generic Java method — `commands.evalsha<List<Long>>(…)` — são Kotlin válido; não é uma armadilha.)
- **Unwrapping a guaranteed-`Right` under a pre-guard is a *dead branch* that quietly lowers coverage.** When a domain op returns `Either<E, T>` but the caller has already pre-guarded the exact condition that would make it `Left` (a call site mirroring an aggregate invariant), `op().getOrNull() ?: return` adds an *uncoverable* branch — JaCoCo drops even while above the gate, and no test can kill it. Absorb the impossible `Left` *inside* Arrow with `.onRight { … }`: the caller stays total, behaviour is identical, and there is no dead branch. Pin the assumption ("under a satisfied pre-guard this is never `Left`") as a **totality test**, not just a comment. (GAP-DN/#350 — `SimulationEngine`'s `executeCard`/`block` sites; applied mid-PR on a harness suggestion.)

  > ⚠️ **Expect a reviewer to suggest the exact opposite — and answer with the measurement, not with this
  > paragraph.** On #381 the harness filed a P3 on `applyUnblock` asking for `unblock().getOrNull()?.let { }`,
  > so the `Movement` would depend on the success instead of running beside it. The *form* argument is
  > sound (if guard and rule ever diverge, a `Movement(UNBLOCKED)` is recorded with no state change → a
  > `CardUnblocked` event → the metric lies), but applying it here reintroduces this very pitfall — in the
  > same file where the rule was born, on a suggestion from the same reviewer that taught it. Measured
  > twice (author in-PR, then independently in the post-merge harvest), with `applyBlock` as the untouched
  > control:
  >
  > | `applyUnblock` form | BRANCH | INSTRUCTION |
  > |---|---|---|
  > | `.onRight { }` (current) | **0 missed** / 4 covered | 0 missed / 50 |
  > | `getOrNull()?.let { }` (suggested) | **1 missed** / 5 covered | **2 missed** / 56 |
  >
  > The dead branch is **inherent to conditioning on the `Either`**, not to the syntax chosen: `?.let`,
  > `?: return null`, `fold` and `map`+unwrap all create an edge whose false path is unreachable while the
  > pre-guard stands. So neither obey nor dismiss — **measure, then answer with the numbers**, and offer the
  > maintainer the opposite trade explicitly (accept an uncoverable branch now in exchange for the structural
  > guarantee) rather than deciding it silently.
  >
  > **The real resolution is to dissolve the dilemma, not to relocate it:** inside a HOF
  > (`mutateCardAt(current, cardId, guard, op)` — **GAP-EA [N]**, already in the #6 Todo) the branch stops
  > being dead, because the HOF can be tested directly with an `op` that genuinely returns `Left`. The
  > structure the reviewer wants then exists **once, actually exercised**, instead of three dead branches
  > spread across `applyMove`/`applyBlock`/`applyUnblock`.
- **An arm with an EMPTY BODY placed LAST in an exhaustive `when` over a sealed type is scored a *partial*.** Same family as the dead branch above, different mechanism — and the trigger is the **empty body**, not the `is` check. Measured on Kotlin 2.4.10 under JaCoCo 0.8.13/0.8.14 (branch counters read from `org.jacoco.core`):

  | last arm | result |
  |---|---|
  | `is D.Unknown -> Unit` (empty body) | **PARTIAL** — `covered=1 missed=1` |
  | `is D.Add -> out += "a"` (non-empty) | FULL — its false path is *equally* unreachable, yet 0 missed |
  | `is D.Unknown -> out.size` (non-empty no-op) | FULL |
  | `when` over an **enum**, inert arm last | FULL |

  Why: an empty body lets the compiler **invert the jump** (`ifne → end`), so the fall-through lands on `NoWhenBranchMatchedException`. JaCoCo's `KotlinWhenFilter` only recognises that throw as a jump *target*, so the inverted shape escapes the filter. The enum case is safe for a different reason (`tableswitch` + a filtered `default → throw`).

  **The fix is "no empty-body arm may be last", not "order the inert variant first".** Putting the inert arm first works only while there is exactly **one** — with two (`Unknown` + a deprecated variant, plausible in the queued `Movement`/`ExecutionResult`/`DecisionRequest` conversions), the second still ends up last with an empty body and the partial returns, with the author having followed the rule. Either keep a non-empty-body arm last, or give the trailing inert arm a body that emits bytecode. Leave an in-code comment saying the order is deliberate, or the next reader "tidies" the catch-all back to the end and silently reintroduces the partial.

  Note this only bites when converting an **enum → sum type**: the enum `when` shows no partial, so the partial appears only after the refactor. **Attribute partials by measuring base-vs-head**, never by assuming — the repo carries pre-existing partials, and only the delta is yours. The same base-vs-head discipline applies to **PITest survivors**, and it cuts both ways: on #383 measuring `surrogateServiceClass` on `main` (1 survivor) showed the PR's version had **2** — the extra one was a redundant `?:` the author had introduced, invisible while the module stayed above its gate. Run the module's `pitest` on the base SHA before blaming (or absolving) your diff. (GAP-DS/#366 — `SimulationEngine` + `RunDayUseCase`; mechanism corrected in #369 after the harness compiled and measured each shape, refuting the "last `is` check" explanation first recorded here.)

- **A module's mutation score can fall several points because of a TEST that stopped running — not because of your diff. Adding one heavy test class squeezes the module's PITest time budget.** On #385 `sql_persistence` went **75% → 72%** (gate 65) with a 4-file diff. Measured base-vs-head, mutant by mutant, in **two** runs (535/741 both times, so not flakiness):

  | Evidence | Reading |
  |---|---|
  | per-file counts on the 4 touched files **identical**, except `LegacyDecode.kt` 15 → **16 killed** | the diff *added* a killed mutant |
  | the 22 lost mutants live in `DatabaseFactory.kt` (15) + `SimulationStateSerializer.kt` (7) — **untouched** | the loss is elsewhere |
  | `TIMED_OUT` **19 → 22**; the slow test `…flyway migrates from the custom path` fell from **20 kills to 2** | the killer test no longer finishes |

  The mutants did not become reachable-and-unkilled — **the test that killed them stopped completing**. One more Embedded PostgreSQL class in the module (each boots a fresh DB + Flyway) is enough to push an already-slow test past PITest's per-mutant timeout. **Diagnostic order:** (1) per-file base-vs-head; (2) if the losses are in files you did not touch, read `TIMED_OUT` and the per-test kill counts before writing "coverage regression" anywhere.

  ⚠️ **Do not "fix" the score yourself.** Merging test classes to please a tool *is* the tail wagging the dog, so that one is out. Raising `timeoutConstInMillis` is not forbidden by the rule at the top of this file (that rule bans editing `build.gradle.kts` **to bypass violations**, and letting a killer test finish would be the opposite) — but it is the **maintainer's** call, never a unilateral tune. **Measure, record, propose.**

  **And when it was finally measured, the remedy turned out to be worse than the disease.** Ran on #391 (2026-07-30), `--rerun-tasks` on both sides, block absent vs `timeoutConstInMillis.set(30_000)`:

  | per-mutant budget | score | KILLED | SURVIVED | TIMED_OUT |
  |---|---|---|---|---|
  | 4s (PITest default — what the module runs today) | **553/744 = 74.3%** | 534 | 94 | 19 |
  | 30s | **544/744 = 73.1%** | 525 | 103 | 19 |

  Raising it **costs 1.21 pp**. `TIMED_OUT` does not move at all, and all 9 lost mutants sit in a single untouched file (`DatabaseFactory.kt`), migrating `KILLED → SURVIVED`. The mechanism is the reverse of the intuition: those mutants **were killed by the clock, not by an assertion** — they make the code slower, the test blew the 4s budget and failed, and a failing test is a kill. Give it 30s and the slow path completes, the assertions still pass, and the mutant survives. **A short budget manufactures false kills**, so a longer one reads as a regression while actually measuring more honestly. Config reverted: slower runs, no gain. The residue is a real (unfixed) finding — 9 `DatabaseFactory` mutants have no assertion that catches them, only a stopwatch.

  ⚠️ **`--rerun-tasks` is mandatory here, and its absence is invisible.** Editing `timeoutConstInMillis` does **not** invalidate the `pitest` task: it comes back `UP-TO-DATE` and you read the *previous* report. The first attempt on #391 did exactly that and produced a delta of **zero, identical down to the individual mutant** — which is what gave it away, since a real re-run varies. Same family as the `detektMain`/`detektTest` false green above. Confirm with `grep 'pitest UP-TO-DATE'` on the build log before believing any before/after.

  (#385 diagnosed the mechanism; overstatement corrected by Codex P2 on #386; hypothesis measured and **refuted** on #391. Note the module read 74.3% on #391, not the 72% of #385 — the baseline moves, so re-measure instead of quoting these numbers.)

- **Deduplicar código DERRUBA o mutation score do módulo sem perder detecção — é efeito de denominador, e a defesa é aritmética por arquivo.** No #387 `usecases` foi de **63,5% → 62,1%** (gate 55) extraindo um guard que estava copiado em 5 use cases. Nada piorou: cada call site perdeu os **2 mutantes do `ensure`** (que estavam **KILLED**, logo saem do numerador *e* do denominador) e o arquivo novo trouxe 9, sendo 5 killed — o resto é andaime de `suspend` (`throwOnFailure`, `NullReturnVals`, `SwitchMutator`), o mesmo perfil que o `Timed.kt` pré-existente já carrega. O guard consolidado ficou **mais** exercitado que qualquer cópia: 5 testes de use case + 3 diretos.

  A leitura ingênua ("a nota caiu, o PR piorou") inverte o sinal de um refactor bom e desincentiva deduplicação. **Antes de aceitar ou negar a queda, faça a conta por arquivo:** quantos mutantes saíram e em que status estavam, quantos entraram e quantos morreram. E compare o perfil do arquivo novo com um **análogo pré-existente do módulo** — é o que separa "andaime do idioma" de "código não testado". Contraste com o mecanismo do #385 logo acima: lá a perda estava em arquivo **não tocado** (teste que parou de completar); aqui está exatamente nos arquivos tocados, e é aritmética esperada. (#387.)
