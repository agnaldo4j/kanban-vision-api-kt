#!/usr/bin/env python3
#
# assert-k8s-observability-invariants.py — semantic invariants of the IN-CLUSTER (k8s)
# observability configs that the schema linters (amtool/promtool) cannot express (GAP-DB).
#
# Sibling of assert-observability-invariants.py, but for `k8s/alertmanager.yml` — the
# k8s-specific copy whose inhibit key legitimately DIVERGES from compose (GAP-DC / review #333 P2).
#
# Background: `amtool check-config` accepts `equal: ['namespace']` and `equal: ['namespace','pod']`
# alike — both are syntactically valid label lists. But under the kubelet cAdvisor the container
# series carry `namespace`/`pod`/`container` (there is NO `name` label, and `instance` is the NODE,
# shared by every pod on it). So the critical→warning inhibit rule MUST be scoped by BOTH
# `namespace` and `pod`: dropping `pod` (or reverting to the compose `['instance','name']`) makes a
# critical container alert inhibit warnings for EVERY pod on the node — schema-valid, so amtool ships
# it silently. This script encodes that INTENT so the regression fails CI instead.
#
# NOTE: this is the k8s counterpart, NOT a retarget — observability/alertmanager.yml keeps
# `['instance','name']` (correct for the compose dedicated cAdvisor) and is guarded by the sibling
# script. The two configs legitimately differ (GAP-DB: single-sourcing them is infeasible).
#
# Gate wired into the `config-lint` job: exit 0 = holds; exit 1 = violation; exit 2 = PyYAML missing.

import sys
from pathlib import Path

try:
    import yaml
except ModuleNotFoundError:
    print("error: PyYAML not installed — run `pip install pyyaml`", file=sys.stderr)
    sys.exit(2)

ALERTMANAGER = Path(__file__).resolve().parent.parent / "k8s" / "alertmanager.yml"

# In k8s the critical→warning inhibition MUST be scoped by both `namespace` and `pod`: they scope
# app alerts (pod SD) and container alerts (kubelet cAdvisor) to the SAME pod. Reverting to the
# compose `['instance','name']` (no `name` in k8s; `instance` = node) breaks per-pod scoping.
REQUIRED_EQUAL = {"namespace", "pod"}


def matchers_mention(rule, key, needle):
    """True if any `<key>` matcher of the rule contains `needle`.

    Tolerates both the `*_matchers` list form (e.g. ['severity="critical"']) and the legacy
    `*_match` map form (e.g. {severity: critical}).
    """
    matchers = rule.get(f"{key}_matchers")
    if isinstance(matchers, list):
        return any(needle in str(m) for m in matchers)
    legacy = rule.get(f"{key}_match")
    if isinstance(legacy, dict):
        return any(needle in f'{k}="{v}"' for k, v in legacy.items())
    return False


def main():
    doc = yaml.safe_load(ALERTMANAGER.read_text())
    inhibit_rules = (doc or {}).get("inhibit_rules", []) or []

    crit_to_warn = [
        r
        for r in inhibit_rules
        if matchers_mention(r, "source", 'severity="critical"')
        and matchers_mention(r, "target", 'severity="warning"')
    ]

    if not crit_to_warn:
        print(
            "k8s observability invariant VIOLATED: no critical→warning inhibit_rule found in "
            f"{ALERTMANAGER.name}. The rule that silences warnings during a critical incident is "
            "missing.",
            file=sys.stderr,
        )
        sys.exit(1)

    violations = []
    for rule in crit_to_warn:
        equal = set(rule.get("equal", []) or [])
        missing = REQUIRED_EQUAL - equal
        if missing:
            violations.append(
                f"  critical→warning inhibit_rule: `equal` is {sorted(equal)} but MUST "
                f"contain {sorted(REQUIRED_EQUAL)} — missing {sorted(missing)}. Under the kubelet "
                "cAdvisor `instance` is the NODE and there is no `name` label, so without "
                "`namespace`+`pod` a critical alert inhibits warnings of UNRELATED pods on the same "
                "node (review #333 P2). Fix k8s/alertmanager.yml, not this guard."
            )

    if violations:
        print(
            f"k8s observability invariant VIOLATED ({len(violations)}):", file=sys.stderr
        )
        for v in violations:
            print(v, file=sys.stderr)
        sys.exit(1)

    print(
        "k8s observability invariants hold: critical→warning inhibition scoped by "
        f"{sorted(REQUIRED_EQUAL)} in {ALERTMANAGER.name}."
    )


if __name__ == "__main__":
    main()
