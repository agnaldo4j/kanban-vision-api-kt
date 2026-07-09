#!/usr/bin/env bash
#
# flow-metrics.sh — first engineering-flow signal (GAP-BI).
#
# Computes the project's own delivery-flow metrics from merged-PR history:
#   PR cycle time (open->merge), lead time (first-commit->merge), delivery
#   cadence (PRs/week), PR-size distribution, and a board WIP snapshot.
#
# It is an executable, NON-BLOCKING signal — never a PR gate. Re-run it each
# cycle and paste the output into docs/quality/flow-<yyyy-mm>.md.
#
# Usage:  scripts/flow-metrics.sh [WINDOW]
#         WINDOW = number of most-recently-MERGED PRs to sample (default 40).
#
# Requirements: gh (authenticated), python3.

set -euo pipefail

WINDOW="${1:-40}"
OWNER="${OWNER:-agnaldo4j}"
REPO="${REPO:-kanban-vision-api-kt}"
PROJECT_NUMBER="${PROJECT_NUMBER:-6}"

command -v gh >/dev/null       || { echo "error: gh not found" >&2; exit 1; }
command -v python3 >/dev/null  || { echo "error: python3 not found" >&2; exit 1; }

# The GitHub PR API only orders by CREATED_AT/UPDATED_AT (not mergedAt), so we
# over-fetch by CREATED_AT and let python re-sort by mergedAt before slicing to
# WINDOW — otherwise a PR created early but merged late would be dropped from
# the advertised "last N merged PRs" window (max page size is 100).
BUFFER=$(( WINDOW + 40 )); [ "$BUFFER" -gt 100 ] && BUFFER=100

# --- Merged-PR history (cycle time, lead time, size, cadence) ---------------
prs_tsv="$(gh api graphql -f query='
query($owner:String!, $name:String!, $first:Int!) {
  repository(owner:$owner, name:$name) {
    pullRequests(states:MERGED, first:$first, orderBy:{field:CREATED_AT, direction:DESC}) {
      nodes {
        number createdAt mergedAt additions deletions
        commits(first:1) { nodes { commit { committedDate } } }
      }
    }
  }
}' -F owner="$OWNER" -F name="$REPO" -F first="$BUFFER" \
  --jq '.data.repository.pullRequests.nodes[]
        | [.number, .createdAt, .mergedAt, (.additions + .deletions),
           (.commits.nodes[0].commit.committedDate // .createdAt)] | @tsv')"

# --- Board WIP snapshot (Doing / Todo counts) -------------------------------
# Distinguish "board unavailable" from a genuine zero — a failed lookup must not
# masquerade as a WIP-limit-respected signal.
if wip_json="$(gh project item-list "$PROJECT_NUMBER" --owner "$OWNER" \
      --format json --limit 200 2>/dev/null)"; then
  wip_ok=1
else
  wip_ok=0; wip_json='{"items":[]}'
fi

PRS_TSV="$prs_tsv" WIP_JSON="$wip_json" WIP_OK="$wip_ok" \
WINDOW="$WINDOW" PROJECT_NUMBER="$PROJECT_NUMBER" python3 - <<'PY'
import json, os, datetime

def pct(p, xs):
    xs = sorted(xs)
    if not xs:
        return 0.0
    k = (len(xs) - 1) * p / 100
    f = int(k); c = min(f + 1, len(xs) - 1)
    return xs[f] + (xs[c] - xs[f]) * (k - f)

def ts(s):
    return datetime.datetime.fromisoformat(s.replace("Z", "+00:00"))

window = int(os.environ["WINDOW"])
rows = [l.split("\t") for l in os.environ["PRS_TSV"].splitlines() if l.strip()]
# Re-sort by merge time (desc) and keep the true "last N merged PRs".
rows.sort(key=lambda r: ts(r[2]), reverse=True)
rows = rows[:window]

cycle, lead, sizes, merges = [], [], [], []
for num, created, merged, size, first_commit in rows:
    cycle.append((ts(merged) - ts(created)).total_seconds() / 3600)
    lead.append((ts(merged) - ts(first_commit)).total_seconds() / 3600)
    sizes.append(int(size))
    merges.append(ts(merged))

n = len(rows)
first_pr, last_pr = rows[-1][0], rows[0][0]
span_days = (max(merges) - min(merges)).total_seconds() / 86400 if n > 1 else 0.0
cadence = f"{n / (span_days / 7):.1f} PRs/week" if span_days > 0 else "n/a"

print(f"# Flow metrics — window: last {n} merged PRs (#{last_pr}..#{first_pr})\n")
print("| Metric | p50 | p85 | max |")
print("|---|---|---|---|")
print(f"| PR cycle time (open→merge, h)   | {pct(50,cycle):.1f} | {pct(85,cycle):.1f} | {max(cycle):.1f} |")
print(f"| Lead time (1st commit→merge, h) | {pct(50,lead):.1f} | {pct(85,lead):.1f} | {max(lead):.1f} |")
print(f"| PR size (changed lines)         | {pct(50,sizes):.0f} | {pct(85,sizes):.0f} | {max(sizes):.0f} |")
print()
over = sum(1 for s in sizes if s > 400)
print(f"- Delivery cadence: **{cadence}** ({n} PRs over {span_days:.1f} days)")
print(f"- PRs over the 400-line heuristic: **{over}/{n}**")
if os.environ["WIP_OK"] == "1":
    items = json.loads(os.environ["WIP_JSON"]).get("items", [])
    doing = sum(1 for i in items if i.get("status") == "Doing")
    todo = sum(1 for i in items if i.get("status") == "Todo")
    print(f"- WIP snapshot (board #{os.environ['PROJECT_NUMBER']}): "
          f"Doing=**{doing}** (limit 1), Todo=**{todo}**")
else:
    print(f"- WIP snapshot (board #{os.environ['PROJECT_NUMBER']}): "
          f"**unavailable** (board query failed — not a zero-WIP signal)")
PY
