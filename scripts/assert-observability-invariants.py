#!/usr/bin/env python3
#
# assert-observability-invariants.py — semantic invariants of the observability configs
# that the schema linters (amtool/promtool) cannot express (GAP-CY; Codex P2 on PR #319).
#
# Background: `amtool check-config` validates the Alertmanager SCHEMA — it accepts
# `equal: ['instance']` and `equal: ['instance', 'name']` alike, because both are
# syntactically valid label lists. But the critical→warning inhibit rule MUST be scoped by
# BOTH `instance` and `name`: the `cadvisor` job in prometheus.yml has a single target, so
# every container_* series shares `instance="cadvisor:8080"` and only the `name` label
# differentiates containers (see the rationale block in observability/alertmanager.yml).
# Dropping `name` is the exact regression fixed in PR #317 — and it stays schema-valid, so
# the amtool gate alone would ship it silently. This script encodes the INTENT so the
# regression fails CI instead.
#
# It is a gate (a `run:`-style check wired into the `config-lint` job): exit 0 = invariant
# holds; exit 1 = violation, naming the missing label(s). Parses YAML — never grep.
#
# Requirements: python3 + PyYAML (preinstalled on ubuntu-latest; `pip install pyyaml` if absent).

import re
import sys
from pathlib import Path

try:
    import yaml
except ModuleNotFoundError:
    print("error: PyYAML not installed — run `pip install pyyaml`", file=sys.stderr)
    sys.exit(2)

ALERTMANAGER = (
    Path(__file__).resolve().parent.parent / "observability" / "alertmanager.yml"
)

# Alertmanager matcher string: label<op>"value" (op ∈ =, !=, =~, !~).
_MATCHER_RE = re.compile(r'^\s*([a-zA-Z_]\w*)\s*(=~|!~|!=|=)\s*"?([^"]*)"?\s*$')

# The critical→warning inhibition MUST be scoped by both labels. `instance` groups the
# app/fleet series; `name` restricts container pairs to the same container (PR #317).
REQUIRED_EQUAL = {"instance", "name"}


def selects(rule, key, label, value):
    """True iff the rule's `<key>_matchers`/`<key>_match` contains an EXACT equality matcher
    `label="value"` (operator `=`).

    Parses the matcher (label, op, value) and compares each part — a substring test would
    false-match e.g. `app_severity="critical"` for `severity="critical"` (Codex P2, #337).
    Tolerates both the current `*_matchers` list form and the legacy `*_match` map form.
    """
    matchers = rule.get(f"{key}_matchers")
    if isinstance(matchers, list):
        for m in matchers:
            mo = _MATCHER_RE.match(str(m))
            if mo and mo.group(1) == label and mo.group(2) == "=" and mo.group(3) == value:
                return True
    legacy = rule.get(f"{key}_match")
    if isinstance(legacy, dict):
        return legacy.get(label) == value
    return False


def main():
    doc = yaml.safe_load(ALERTMANAGER.read_text())
    inhibit_rules = (doc or {}).get("inhibit_rules", []) or []

    crit_to_warn = [
        r
        for r in inhibit_rules
        if selects(r, "source", "severity", "critical")
        and selects(r, "target", "severity", "warning")
    ]

    if not crit_to_warn:
        print(
            "observability invariant VIOLATED: no critical→warning inhibit_rule found in "
            f"{ALERTMANAGER.name}. The rule that silences warnings during a critical "
            "incident is missing (regression risk — PR #317).",
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
                f"contain {sorted(REQUIRED_EQUAL)} — missing {sorted(missing)}. Without "
                "`name`, a critical alert inhibits warnings of UNRELATED containers "
                "(PR #317). Fix observability/alertmanager.yml, not this guard."
            )

    if violations:
        print(
            f"observability invariant VIOLATED ({len(violations)}):", file=sys.stderr
        )
        for v in violations:
            print(v, file=sys.stderr)
        sys.exit(1)

    print(
        f"observability invariants hold: critical→warning inhibition scoped by "
        f"{sorted(REQUIRED_EQUAL)} in {ALERTMANAGER.name}."
    )


if __name__ == "__main__":
    main()
