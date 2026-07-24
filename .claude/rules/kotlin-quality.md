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
- `LargeClass` threshold: 200 lines. Split test files into focused classes (e.g., `ScenarioCreationRoutesTest`, `ScenarioRunDayRoutesTest`).
- Zero Detekt violations before opening a PR.
- Run `./gradlew ktlintFormat` before committing.

## Kotlin-Specific Pitfalls

- **`@JvmInline value class` + MockK**: `any()` typed matcher (e.g., `any<ScenarioId>()`) may fail at runtime. Use specific values or untyped `any()` for inline value class parameters.
- **`raise()` in `either {}`**: member of `Raise<E>` — available implicitly inside `either {}`. Do NOT import `arrow.core.raise.raise`.
- **Kotlin serialization plugin**: applied without version in `http_api` and `sql_persistence` because the plugin is already on the classpath from `buildSrc`.
- **`/*` inside a KDoc opens a *nested* block comment**: Kotlin supports nested block comments, so a `/*` in doc text — e.g. a backticked glob `` `pkg/sub/**` `` (the `/**`) — starts a comment that never closes → `Unclosed comment` at EOF. Avoid `/*` sequences in doc/KDoc; write `pkg.sub`, not `` `pkg/sub/**` ``. (GAP-BZ/#325 — this, not explicit type args, was the real cause of that build's `Unclosed comment`. Explicit type args on a generic Java method — `commands.evalsha<List<Long>>(…)` — são Kotlin válido; não é uma armadilha.)
- **Unwrapping a guaranteed-`Right` under a pre-guard is a *dead branch* that quietly lowers coverage.** When a domain op returns `Either<E, T>` but the caller has already pre-guarded the exact condition that would make it `Left` (a call site mirroring an aggregate invariant), `op().getOrNull() ?: return` adds an *uncoverable* branch — JaCoCo drops even while above the gate, and no test can kill it. Absorb the impossible `Left` *inside* Arrow with `.onRight { … }`: the caller stays total, behaviour is identical, and there is no dead branch. Pin the assumption ("under a satisfied pre-guard this is never `Left`") as a **totality test**, not just a comment. (GAP-DN/#350 — `SimulationEngine`'s `executeCard`/`block` sites; applied mid-PR on a harness suggestion.)