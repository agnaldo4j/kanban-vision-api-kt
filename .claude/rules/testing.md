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
- **Koin DI in route tests**: every route test must update `single { CreateColumnUseCase(get(), get()) }` and `single { CreateCardUseCase(get(), get(), get()) }` — both require `BoardRepository` since GAP-I.
- **LargeClass threshold**: 200 lines. Split test files when they grow beyond this.

## Coverage

JaCoCo gate: ≥ 95% instruction coverage per module. If coverage drops, write the missing test — never lower the threshold.