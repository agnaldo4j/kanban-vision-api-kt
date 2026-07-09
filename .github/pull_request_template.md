## ADR

> Fill in if this PR implements an architectural decision:
> Decision documented in: `adr/ADR-NNNN-slug.md` _(or N/A)_

---

## Context

> 1–3 sentences on the problem or gap that motivates this PR.

---

## What changes

-
-

---

## Quality Checklist

### Pipeline
- [ ] `./gradlew testAll` green locally (Detekt + KtLint + tests + JaCoCo ≥ 98%)
- [ ] Zero Detekt violations (`warningsAsErrors = true`)
- [ ] Zero KtLint errors (`./gradlew ktlintFormat` run before commit)
- [ ] JaCoCo coverage ≥ 98% on every affected module
- [ ] PR ≤ 400 changed lines
- [ ] Squash-merge only; `main` is branch-protected (required checks + linear history)

### Architecture
- [ ] Dependency Rule respected: `domain ← usecases ← http_api / sql_persistence`
- [ ] Zero framework imports in `domain/` or `usecases/` (except Arrow-kt)
- [ ] Repository interfaces defined in `usecases/repositories/`, implementations in `sql_persistence/`
- [ ] Domain errors modelled as `Either<DomainError, T>` — no unhandled exceptions

### Security & Data
- [ ] No secrets, tokens or PII in code or logs
- [ ] `JWT_DEV_MODE=true` absent from production code

### Observability
- [ ] `requestId` propagated in the MDC on new routes (if applicable, or N/A)
- [ ] New domain errors mapped in `StatusPages` (if applicable, or N/A)
- [ ] Existing metrics not broken (`/metrics` responds without error)

### Traceability
- [ ] Branch follows the convention: `feat/gap-X-slug`, `fix/...`, `docs/...`
- [ ] Commits with descriptive messages and a reference to the gap/ticket
- [ ] If an ADR applies: status updated to `accepted` and the `PR` field filled in

### ADR-only checklist (fill in if this PR implements an ADR)
- [ ] Implementation plan with every task marked `[x]`
- [ ] All DoD items confirmed or marked N/A with justification
- [ ] C4 diagrams updated if a new module, route or use case was added

### Bug Fix (fill in only if this PR is a `fix/`)
- [ ] Minimal reproduction of the bug documented in the PR body
- [ ] Regression test added that would fail without the fix: _(test name)_
- [ ] Root cause identified

---

## Test Plan

- [ ] Unit tests: _(describe the scenarios — happy path + error)_
- [ ] Integration tests: _(describe the boundaries tested)_
- [ ] CI pipeline green on the PR

---

> For complex features, run the full `/definition-of-done` checklist before marking as Done.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
