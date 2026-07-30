---
paths:
  - "**/test/**/*.kt"
  - "**/*Test.kt"
  - "**/*IntegrationTest.kt"
---

# Testing Conventions

## Structure

- **Domain tests**: pure unit tests, no external dependencies.
- **Use case tests**: MockK to isolate repositories + `kotlinx-coroutines-test` (`runTest`).
- **Persistence tests**: integration with Embedded PostgreSQL (zonky) — never mock the database.
- **Route tests**: `testApplication` (Ktor) + Koin module with mocked repositories + MockK.

## Given-When-Then Pattern

Every test must cover both the happy path and at least one error path:

```kotlin
@Test
fun `execute saves entity and returns its id`() = runTest { ... }      // happy path

@Test
fun `execute returns ValidationError when name is blank`() = runTest { ... }  // error path
```

## Known Pitfalls

- **MockK + `@JvmInline value class`**: `any()` typed matcher may fail. Use specific values or untyped `any()`.
- **`CreateScenarioUseCase` generates its own ID**: use `any()` when mocking `scenarioRepository.saveState(...)` — the ID is generated internally by `Scenario.create()`.
- **`IntegrationTestSetup.closeDataSource()` / `reinitDataSource()`**: use in `@BeforeEach`/`@AfterEach` to force `PersistenceError` paths.
- **Koin DI in route tests**: register the simulation use cases the route under test needs (e.g. `single { CreateSimulationUseCase(get(), get(), get(), get()) }`, `single { RunDayUseCase(get(), get(), get(), get(), get()) }`) with mocked repositories/ports. Both take a `Clock` last (GAP-DK — provide `single<Clock> { Clock.fixed(...) }`); a use case with a domain clock reads `now` from it, so tests bind a fixed `Clock` for determinism.
- **Absorbing a signature-change ripple with a test-only overload**: when a production signature gains a parameter (e.g. `SimulationEngine.runDay` gained `now: Instant`, GAP-DK #353), a *test-only* lower-arity overload that delegates to the new member with a fixed default (`internal fun SimulationEngine.runDay(sim, decisions, seed) = runDay(sim, decisions, seed, Instant.EPOCH)`) keeps dozens of behavior call-sites focused without editing each. Two safety conditions, or it's a footgun: (1) **distinct arity** from the member — with the *same* arity Kotlin gives the production **member precedence**, so a same-name same-arity overload is silently shadowed and **never intercepts** those call-sites (the helper is dead, not recursive), defeating its purpose; a *lower* arity has no matching member, so it resolves to the overload; (2) exercise the **new full-arity member** directly in at least one dedicated test so the injected parameter is actually covered (the overload must not become the only path). Keep such helpers in a `*TestSupport.kt`, never in `src/main`.
- **LargeClass threshold**: 200 lines — of the class *body* as Detekt counts it, **not** of the file. Blank
  lines and comments do not count, so `wc -l` neither condemns nor absolves. Measured in **#388**: a 240-line
  file passed comfortably (≈172 effective body lines against the 200 limit) because this repo comments its
  tests heavily. Consequences in both directions: do not split a class on the strength of `wc -l`, and do not
  file a green build under "got lucky" — settle it by running Detekt **on the source set the file belongs
  to**: `detektTest` for tests, `detektMain` for production, `detekt` for both. Running the wrong one is a
  false green: `detektTest` passes without ever examining a production class (Codex P2 on #389). Always with
  `--rerun-tasks`, or an UP-TO-DATE task replays the previous run. Split test files when Detekt says so. **Splitting a class is not
  a licence to copy its fixture:** the setup helper goes to the package's `*TestSupport.kt` (which already
  exists for exactly this), not into each new class. A duplicated fixture breaks nothing at runtime — it breaks
  the day `Simulation.create`/`Scenario.create` changes signature or the setup needs one more step: there are
  N copies to find, and a divergence between them **fails no test**, it just makes two files believe they test
  the same scenario. In #381 `simulationFrom` was verbatim in **three** classes and two of them had already
  drifted (one carried `agingDays`, the other didn't). When you promote it, leave a comment saying **why it
  lives there**, or the next author re-copies it.
- **A property test written alongside the fix must be run against the code WITHOUT the fix.** A generator that
  never reaches the defective state passes on both sides, and the suite keeps a law that proves nothing — worse
  than no test, because it *looks* like coverage. The risk peaks on **preservation** properties (no-loss,
  round-trip), where the benign cases are the easy ones to generate. Measured in **PR #374 (GAP-DP)**: the first
  generator drew each card's step only from the board's **own** steps, so a card pointing at a sibling step was
  *relocated*, never lost — all four laws passed with the bug present. Only a step id from **outside** the board
  discriminates. Procedure: revert the production fix, run the property test, confirm it **fails**, then
  restore. If it still passes, the generator — not the law — is what needs fixing.
- **When production starts calling a helper, the test that validates it must KEEP its own computation.** Third
  member of the vacuum family above, and the easiest to introduce by accident: a refactor that extracts
  `X.foo()` sweeps the call sites, and a test whose oracle *was* the hand-written computation looks like just
  another call site. Rewrite it and the law degenerates to `f(x) == f(x)` — green, and proving nothing, while
  the diff reads like tidying up. Measured in **#388 (GAP-DQ)**: `Board.itemCount()` replaced
  `scenario.board.steps.sumOf { it.cards.size }` in the DTO mapper, and `DtoMappingPropertyTest` was
  deliberately **left** with the manual sum as its oracle. Rule of thumb: an oracle that shares code with the
  subject is not an oracle. The *other* direction of the same coin — when you keep a second computation of the
  same value **on purpose**, for performance (here `itemCount()` counts in place instead of delegating to
  `allCards().size`, which stamps `copy(step = step.id)` on N cards just to count them) — the two are only
  allowed to coexist while they agree, so bind them with a property (`board.itemCount() == board.allCards().size`)
  and prove the law is not vacuous by sabotaging one side (`+ 1`) and watching it fail. An optimisation without
  a law tying it to the reference implementation is a divergence waiting for its first silent day.
- **A test that CORRUPTS a fixture must assert the corruption actually happened.** Same vacuum as the property
  test above, one level earlier: a legacy/tolerance test that mangles a stored blob (`replace`, `replaceFirst`,
  a hand-edited JSON) and then asserts "it still loads" proves **nothing** if the mangling silently matched
  nothing — it just asserts the happy path, in green. One line pays for itself:
  ```kotlin
  val blanked = blob.replaceFirst(Regex(""""id"\s*:\s*"${Regex.escape(id)}""""), """"id": """"")
  check(blanked != blob) { "the blob must actually change, otherwise the test proves nothing" }
  ```
  **The concrete trap it caught (#383):** `state_json`/`snapshot_json` are **JSONB**, and Postgres stores a
  *parsed* value — what comes back is re-rendered (a space after `:`; key order and duplicate keys are not
  preserved either, per the `jsonb` docs), **not** the bytes `SimulationSerializer.encode` wrote. So matching
  the encoder's literal (`"id":"…"`) against a blob **read back from the DB** fails silently, and only the
  anti-vacuum `check` surfaced it. Generalises to any store that normalises what it holds (JSONB, a formatter,
  a re-serialising ORM): **never assume a round-tripped string is byte-identical to what you wrote** — tolerate
  the spacing (`\s*`) or mutate through a JSON parser, and assert the mutation landed.

## Coverage

JaCoCo gate: ≥ 98% instruction coverage per module (ADR-0029). If coverage drops, write the missing test — never lower the threshold.