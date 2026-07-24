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
- **LargeClass threshold**: 200 lines. Split test files when they grow beyond this.

## Coverage

JaCoCo gate: ≥ 98% instruction coverage per module (ADR-0029). If coverage drops, write the missing test — never lower the threshold.