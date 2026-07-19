# PR harness `workflow_run` trigger — post-merge verification (GAP-CW)

**Date**: 2026-07-19 · **Follow-up of**: GAP-CT (#313, `workflow_run` trigger) + GAP-CX (#314, bug hunting + inline comments)
**Subject**: `.github/workflows/pr-review.yml` · **Type**: advisory, non-blocking (never a merge gate)

> The `workflow_run` trigger can only be exercised **after** it lands on the default branch — `workflow_run`
> always runs the workflow definition from `main`. This doc turns that bootstrap caveat into evidence: the
> four acceptance criteria, confirmed against real Actions runs, plus one **defect found** during verification.

## Acceptance criteria

| # | Criterion | Status | Evidence |
|---|---|:-:|---|
| 1 | Harness does **not** run in parallel with CI | ✅ | For `eb7f667` (push, #313 merge): CI run `29706879581` completed `22:52:27Z`; the pr-review `workflow_run` `29706932630` was created `22:52:29Z` — **2 s after** CI finished, never alongside it. |
| 2 | Fires only **after** the `CI` workflow completes | ✅ | pr-review runs are all `event=workflow_run` (`workflows: ["CI"]`, `types: [completed]`); each is created at a CI completion timestamp. |
| 3 | Posts to the **correct PR** (number derived from head SHA) | ⚠️ | Resolution works — run `29707063995` logged `Resolved PR #314 — head atual bate com o RUN_SHA 93b369f…`. **But no comment was posted** (see Defect below); a controlled run is needed to confirm an actual post. |
| 4 | Push to `main` does **not** post (cosmetic `skipped` run) | ✅ | Push runs `29707138331` (`5f3b647`, #314 merge) and `29706932630` (`eb7f667`, #313 merge): `workflow_run · completed/skipped` — the job `if:` (`workflow_run.event == 'pull_request'`) filters them out without allocating a runner. |
| + | Stale-head guard (GAP-CT) works | ✅ | On #314, older-commit CIs completed after the PR head had advanced; the resolve step matched no PR (`.head.sha == RUN_SHA` false) and skipped — the P2 fix from #313 in action. |

## Defect found during verification — harness errors and posts nothing (masked green)

On run `29707063995` (`workflow_run` over #314's CI) the harness resolved the right PR, obtained the OIDC app
token, then the Claude execution returned:

```
"type": "result", "subtype": "success", "is_error": true,
"duration_ms": 286, "num_turns": 1, "total_cost_usd": 0, "permission_denials_count": 0
##[error]Claude result reported subtype success with is_error:true (run did not complete successfully)
```

- `286 ms` + `$0` + `num_turns: 1` = the model **failed on the first call** — not a review failure.
- `continue-on-error: true` (correct for an advisory job) masked the step as green, so the failure was
  **invisible** until the logs were read — the same "silently green while broken" class as GAP-CC/#288.

**Leading hypothesis**: operational — Anthropic account credit/quota, or default-model access — not a
prompt/rubric bug (which would run far longer than 286 ms). Circumstantial: Copilot also hit its quota the
same day; the harness ran several times earlier (#308/#310 + local dogfooding). GAP-CS (#308) posted
successfully at `20:17Z`, so this is a regression/environmental change since then, not a structural bug.

## Outcome

_(Filled in from the controlled GAP-CW test run — see PR that carries this doc.)_

- Controlled run id: _pending_
- `is_error` root cause: _pending_
- Fix applied (if code): _pending_ — otherwise flagged as operational (account-owner action on `ANTHROPIC_API_KEY`).
- Visibility hardening (make a masked harness failure visible without becoming a gate): _decided per diagnosis_.
