#!/usr/bin/env bash
# Claude Code PreToolUse hook — intercepts git commit, auto-formats Kotlin first.
# Receives JSON on stdin: { "tool_name": "Bash", "tool_input": { "command": "..." } }

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // ""')

# Only intercept git commit calls
if ! echo "$COMMAND" | grep -qE '^git commit'; then
  exit 0
fi

cd "$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0

# Check if there are any staged Kotlin files
STAGED_KT=$(git diff --cached --name-only --diff-filter=ACMR | grep '\.kt$' || true)

if [ -z "$STAGED_KT" ]; then
  exit 0
fi

echo "🔍 Kotlin files staged — running ktlintFormat..." >&2
if ! ./gradlew ktlintFormat --quiet 2>&1 >&2; then
  printf '{"hookSpecificOutput":{"permissionDecision":"deny","permissionDecisionReason":"ktlintFormat failed — fix formatting errors and recommit"}}'
  exit 0
fi

# Check only the originally staged Kotlin files for reformatting (ignore pre-existing unstaged changes)
REFORMATTED=$(echo "$STAGED_KT" | tr ' ' '\n' | while read -r f; do
  git diff --name-only -- "$f" 2>/dev/null
done | sort -u | grep '\.kt$' || true)

if [ -n "$REFORMATTED" ]; then
  echo "" >&2
  echo "⚠️  ktlintFormat reformatted the following files:" >&2
  echo "$REFORMATTED" | sed 's/^/   /' >&2
  echo "" >&2
  echo "Re-stage the reformatted files and commit again:" >&2
  echo "   git add $REFORMATTED" >&2
  echo "" >&2
  # Emit deny decision as JSON to stdout
  printf '{"hookSpecificOutput":{"permissionDecision":"deny","permissionDecisionReason":"ktlintFormat reformatted files — re-stage and recommit"}}'
  exit 0
fi

exit 0