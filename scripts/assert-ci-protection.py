#!/usr/bin/env python3
#
# assert-ci-protection.py — regression guard for the CI fault-tolerance invariant (GAP-CD).
#
# Background: in #288 the GitHub comment API returned 500 and a report-publishing step
# failed a *mandatory* check while every real gate was green — the job status came to mean
# the opposite of the truth, and a transient outage blocked correct code. GAP-CC protected
# the peter-evans comment steps; GAP-CD closes the residual gap (EnricoMi, madrapps,
# upload-artifact, Codecov) and commits this guard so the fix cannot silently regress.
#
# The invariant, one biconditional — no step-name list to rot:
#
#     a step has `continue-on-error: true`  <=>  its `uses:` is a report-publishing action
#
#   ->  every reporting step is protected  (catches the upload-artifact #7 added tomorrow)
#   <-  no non-reporting step is protected (nobody "fixes" a red testAll with continue-on-error;
#       no real gate or setup step is ever silenced)
#
# Real gates (testAll, PITest, osv-scanner, docker build, smoke) and setup steps are `run:`
# or non-reporting actions -> they must NEVER carry continue-on-error, and a defect of ours
# must fail the job. This script is itself a gate: it is `run:`, not a reporting action, so
# it wires in without continue-on-error, consistent with its own rule.
#
# Scope: ci.yml ONLY. load-test.yml also uploads an artifact, but it is a manual workflow
# with no branch-protected check — the reason for the rule (don't fail a mandatory check)
# does not apply there.
#
# Usage:   python3 scripts/assert-ci-protection.py
# Exit 0:  invariant holds (prints "<n> protected / <m> gate-side" for visibility).
# Exit 1:  one or more violations, listing job + step name + which side broke.
#
# Requirements: python3 + PyYAML (preinstalled on ubuntu-latest; `pip install pyyaml` if absent).
# Parses the YAML AST — never grep: the ci.yml header mentions `continue-on-error` in prose.

import sys
from pathlib import Path

try:
    import yaml
except ModuleNotFoundError:
    print("error: PyYAML not installed — run `pip install pyyaml`", file=sys.stderr)
    sys.exit(2)

WORKFLOW = Path(__file__).resolve().parent.parent / ".github" / "workflows" / "ci.yml"

# Report-publishing actions, matched by the prefix before `@` (version-agnostic: a Dependabot
# bump of the tag never breaks the match). A step whose `uses:` starts with one of these is a
# reporting step and MUST be protected; anything else MUST NOT be.
REPORTING_ACTIONS = (
    "actions/upload-artifact",
    "peter-evans/find-comment",
    "peter-evans/create-or-update-comment",
    "EnricoMi/publish-unit-test-result-action",
    "madrapps/jacoco-report",
    "codecov/codecov-action",
)


def action_ref(step):
    """The `uses:` value with its @version stripped, or None for a `run:` step."""
    uses = step.get("uses")
    if not isinstance(uses, str):
        return None
    return uses.split("@", 1)[0]


def is_reporting(step):
    ref = action_ref(step)
    return ref is not None and ref.startswith(REPORTING_ACTIONS)


def is_protected(step):
    return bool(step.get("continue-on-error"))


def main():
    doc = yaml.safe_load(WORKFLOW.read_text())
    jobs = doc.get("jobs", {})

    violations = []
    protected_count = 0
    gate_side_count = 0

    for job_name, job in jobs.items():
        for step in job.get("steps", []):
            reporting = is_reporting(step)
            protected = is_protected(step)
            label = step.get("name") or action_ref(step) or "<unnamed>"

            if protected:
                protected_count += 1
            else:
                gate_side_count += 1

            if protected and not reporting:
                violations.append(
                    f"  [{job_name}] '{label}': has continue-on-error but is NOT a "
                    f"report-publishing action — a gate/setup step must never be silenced (<- side)"
                )
            elif reporting and not protected:
                violations.append(
                    f"  [{job_name}] '{label}': is a report-publishing action but LACKS "
                    f"continue-on-error — it can fail a mandatory check on an API error (-> side)"
                )

    if violations:
        print(f"CI protection invariant VIOLATED ({len(violations)}):", file=sys.stderr)
        for v in violations:
            print(v, file=sys.stderr)
        print(
            "\nRule: continue-on-error: true  <=>  uses: is a report-publishing action "
            "(GAP-CD). Fix the workflow, not this guard.",
            file=sys.stderr,
        )
        sys.exit(1)

    print(
        f"CI protection invariant holds: {protected_count} protected (report layer) / "
        f"{gate_side_count} gate-side (gates + setup)."
    )


if __name__ == "__main__":
    main()
