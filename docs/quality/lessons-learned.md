# Lessons Learned — the review-to-skills feedback loop

Append-only log of **durable, generalizable** lessons surfaced by a PR/gate — the ones worth changing
the way we work so the same mistake does not recur. It is the durable half of the loop the pr-harness
rubric §6 and the `/pr-review` skill drive: a review surfaces a lesson → it is **recorded here** and, when
it warrants, **applied** to a skill/rule/rubric (or carded on Board #6). Nothing gets lost between "the
harness noticed it" and "the process improved."

**What belongs here:** a recurring miss, a false-negative from a gate/reviewer, a subtle trap that cost
time, a process gap. One entry per lesson. Keep it terse: what happened, the durable rule, where it was
applied. **What does NOT belong:** feature-specific findings (those live in the ADR / the gap's notes),
one-off nits, or lessons without a concrete "apply it here."

**How the loop runs (automated):** when the author tells the session a PR merged, the
**`post-merge-harvester` agent** (`.claude/agents/post-merge-harvester.md`) does the closure AND harvests
this file's lessons — reading the merged PR's *inline* review comments, distilling the durable ones, and
**applying** them as amendments to the target skill/rule/rubric plus a row here, in a ready `[N]`
process-PR. That is the point: lessons become *applied process for the next cycle*, not a task list. The
`/pr-review` skill (step 5) and the pr-harness rubric §6 feed the same file. Feature-specific findings do
NOT belong here — they live in the ADR / the gap's notes.

**Anti-loop guard (why this doesn't spiral):** the harvester harvests **only after a *real implementation***
— a merged PR that touches production source (`*/src/main/**`). A **process/doc/skill/ADR/test-only PR gets
closure only, no harvest**, so an improvement-PR never spawns another improvement-PR (the loop terminates in
one level). This file itself is process — merging it triggers closure only.

| Date | PR(s) | Lesson (the durable rule) | Applied to |
|---|---|---|---|
| 2026-07-21 | #323 #324 #325 | **Read the raw inline review comments, never trust the harness *summary*.** The pr-harness report can say "APPROVE / low severity" while its own inline comments (and Codex) carry P1s — it happened 3× in one cycle, including a *blocking* supply-chain P1. Verify via `gh api .../pulls/N/comments` + the GraphQL `reviewThreads` (`isResolved`), not the prose summary. | `.claude/skills/pr-review` (hard-rule), `feedback_always_run_pr_review` memory |
| 2026-07-21 | #325 | **CI already runs pr-review — read the PR's posted reports; don't re-dispatch the harness by default.** `pr-review.yml` (GAP-CT) posts the `[claude]` report after CI. Manual `/pr-review` is the exception (CI hasn't run yet / immediate feedback). Redundant *and* risky — see next row. | `.claude/skills/pr-review` (default at top) |
| 2026-07-21 | #325 | **The pr-harness subagent runs Bash in the *same* working directory and can `git checkout`.** A manual dispatch switched the branch and reverted files mid-review (the pushed commit was intact — it reviews the remote SHA). If dispatching manually, re-check `git branch --show-current` afterwards. Preferring CI reports avoids this entirely. | `.claude/skills/pr-review` (manual-dispatch caveat) |
| 2026-07-21 | #325 | **Align a transitive dependency family for a CVE via its BOM, not per-module pins.** A per-module Netty pin missed `netty-resolver-dns`/`netty-codec-dns` (pulled at a CVE'd 4.1.x by Lettuce) → the blocking `supply-chain` gate went red. `platform("io.netty:netty-bom:<v>")` aligns the whole family in one line. | `.claude/rules/stack.md` |
| 2026-07-21 | #325 | **Kotlin trap — explicit type args on a generic *Java* method break the lexer.** `commands.evalsha<List<Long>>(…)` parses `<` as comparison → a spurious "Unclosed comment" at EOF. Type via a variable instead: `val f: RedisFuture<List<Long>> = commands.evalsha(…)`. | `.claude/rules/kotlin-quality.md` (pitfalls) |
| 2026-07-21 | #325 | **Kotlin trap — `/*` (e.g. inside a `` `glob/**` ``) within a KDoc opens a *nested* block comment** (Kotlin supports nesting) that never closes → "Unclosed comment". Avoid `/*` sequences in doc text (write `pkg.sub`, not `` `pkg/sub/**` ``). | `.claude/rules/kotlin-quality.md` (pitfalls) |
| 2026-07-21 | GAP-CZ | **A lesson log is not enough — the loop must be *applied and automated*.** Documenting lessons in a file still relies on someone acting on them. Encoded the loop as the `post-merge-harvester` agent (closure + harvest + *apply* to skills/rules as a ready process-PR), triggered whenever the author says a PR merged. | `.claude/agents/post-merge-harvester.md`, `.claude/rules/workflow.md` (After-PR-Merge) |
| 2026-07-21 | #324 | **Verify a library's internals from its source, not from assumption.** Ktor's `DefaultRateLimiter` turned out to be a *fixed-window* counter, not a token bucket — refuting a reviewer P2 and producing ADR-0042. A five-minute read of the jar source settled a claim two reviewers got wrong. | `feedback_full_rescore_verify_code` memory (reinforced) |
