#!/usr/bin/env bash
# Claude Code PreToolUse hook — OWASP Top 10 (2025) security scanner.
# Intercepts Write and Edit tool calls on Kotlin files to block critical
# security violations before they are written to disk.
#
# Violations:
#   [A01] Broken Access Control
#   [A04] Cryptographic Failures — hardcoded secrets, weak algorithms
#   [A05] Injection — SQL via string concatenation
#   [A07] Authentication Failures — JWT bypass patterns
#   [A09] Security Logging — sensitive data in logs
#   [A10] Exception Handling — fail open patterns
#
# Exit codes:
#   0          → allow (proceed normally)
#   0 + JSON   → deny (block the tool call and report reason)

INPUT=$(cat)
TOOL=$(echo "$INPUT" | jq -r '.tool_name // ""')

# Only intercept Write and Edit
if [[ "$TOOL" != "Write" && "$TOOL" != "Edit" ]]; then
  exit 0
fi

FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // ""')

# Only scan Kotlin files
if ! echo "$FILE_PATH" | grep -qE '\.kt$'; then
  exit 0
fi

# Extract the content being written (Write) or the new_string being inserted (Edit)
CONTENT=$(echo "$INPUT" | jq -r '.tool_input.content // .tool_input.new_string // ""')

if [ -z "$CONTENT" ]; then
  exit 0
fi

CRITICAL=""   # violations that block (deny)
WARNINGS=""   # violations that warn only (stderr)

# ─────────────────────────────────────────────────────────────────────────────
# A04 — Hardcoded secrets (CRITICAL — deny)
# ─────────────────────────────────────────────────────────────────────────────
if echo "$CONTENT" | grep -qiE \
  '(password|passwd|secret|apikey|api_key|jwtSecret|jwt_secret|bearerToken)\s*(=|:)\s*"[^"]{4,}"'; then
  CRITICAL="$CRITICAL\n[A04] Hardcoded credential detected — use System.getenv() or a secrets manager (never hardcode secrets)"
fi

# ─────────────────────────────────────────────────────────────────────────────
# A04 — Weak cryptographic algorithms (CRITICAL — deny)
# ─────────────────────────────────────────────────────────────────────────────
if echo "$CONTENT" | grep -qiE \
  'MessageDigest\.getInstance\s*\(\s*"(MD5|SHA-1|SHA1|DES|RC4)"'; then
  CRITICAL="$CRITICAL\n[A04] Weak crypto algorithm (MD5/SHA-1/DES/RC4) — use SHA-256+ for hashing, Argon2 for passwords"
fi

# ─────────────────────────────────────────────────────────────────────────────
# A04 — Disabled SSL verification (CRITICAL — deny)
# ─────────────────────────────────────────────────────────────────────────────
if echo "$CONTENT" | grep -qiE \
  'hostnameVerifier\s*=\s*\{[^}]*->\s*true\s*\}|trustAllCerts|ALLOW_ALL_HOSTNAME|InsecureHostnameVerifier'; then
  CRITICAL="$CRITICAL\n[A04] Disabled SSL/TLS verification — never trust all certs; configure proper certificate validation"
fi

# ─────────────────────────────────────────────────────────────────────────────
# A05 — SQL Injection via string concatenation (CRITICAL — deny)
# ─────────────────────────────────────────────────────────────────────────────
if echo "$CONTENT" | grep -qE \
  '(executeQuery|executeUpdate|prepareStatement|exec)\s*\(\s*"[^"]*\$[a-zA-Z]'; then
  CRITICAL="$CRITICAL\n[A05] SQL injection risk — string interpolation in SQL query. Use Exposed DSL or PreparedStatement with ? placeholders"
fi

# ─────────────────────────────────────────────────────────────────────────────
# A05 — Raw SQL exec with interpolation (CRITICAL — deny)
# ─────────────────────────────────────────────────────────────────────────────
if echo "$CONTENT" | grep -qE \
  'exec\s*\(\s*"[^"]*\$[a-zA-Z]'; then
  CRITICAL="$CRITICAL\n[A05] SQL injection via exec() with string interpolation — use Exposed DSL parametrized queries only"
fi

# ─────────────────────────────────────────────────────────────────────────────
# A07 — JWT bypass / dev mode with insecure default (WARNING)
# ─────────────────────────────────────────────────────────────────────────────
if echo "$CONTENT" | grep -qiE \
  'JWT_DEV_MODE.*\?:\s*true|skipWhen\s*\{.*true\s*\}'; then
  WARNINGS="$WARNINGS\n[A07] Potential JWT auth bypass — JWT_DEV_MODE default must be false; skipWhen must not return true unconditionally"
fi

# ─────────────────────────────────────────────────────────────────────────────
# A08 — Java ObjectInputStream (CRITICAL — deny)
# ─────────────────────────────────────────────────────────────────────────────
if echo "$CONTENT" | grep -qE \
  'import java\.io\.ObjectInputStream|ObjectInputStream\s*\('; then
  CRITICAL="$CRITICAL\n[A08] Java ObjectInputStream enables RCE via deserialization — use kotlinx.serialization instead"
fi

# ─────────────────────────────────────────────────────────────────────────────
# A09 — Sensitive data in logs (WARNING)
# ─────────────────────────────────────────────────────────────────────────────
if echo "$CONTENT" | grep -qiE \
  'log(ger)?\.(info|debug|warn|error|trace)\s*\([^)]*\b(password|passwd|token|secret|cpf|credit.?card|bearer)\b'; then
  WARNINGS="$WARNINGS\n[A09] Possible sensitive data in logs — mask or omit credentials, PII, and tokens from log messages"
fi

# ─────────────────────────────────────────────────────────────────────────────
# A10 — Fail open (CRITICAL — deny)
# ─────────────────────────────────────────────────────────────────────────────
if echo "$CONTENT" | grep -qE \
  'catch\s*\([^)]*\)\s*\{[^}]*(return true|return@[a-z]+ true)[^}]*\}'; then
  CRITICAL="$CRITICAL\n[A10] Fail open detected — exception must not grant access (return true). Always fail closed: deny on error"
fi

# ─────────────────────────────────────────────────────────────────────────────
# A10 — Stack trace exposed to client (WARNING)
# ─────────────────────────────────────────────────────────────────────────────
if echo "$CONTENT" | grep -qiE \
  'respond\s*\([^)]*stackTrace'; then
  WARNINGS="$WARNINGS\n[A10] Stack trace in HTTP response — never expose internal details to client. Use correlationId instead"
fi

# ─────────────────────────────────────────────────────────────────────────────
# Output
# ─────────────────────────────────────────────────────────────────────────────
HAS_CRITICAL=false
HAS_WARNINGS=false

[ -n "$CRITICAL" ] && HAS_CRITICAL=true
[ -n "$WARNINGS" ] && HAS_WARNINGS=true

if $HAS_WARNINGS || $HAS_CRITICAL; then
  echo "" >&2
  echo "🔒 OWASP Security Scan — $(basename "$FILE_PATH")" >&2
  echo "───────────────────────────────────────────────────────" >&2
fi

if $HAS_CRITICAL; then
  echo -e "$CRITICAL" | sed 's/^/  ❌ /' >&2
fi

if $HAS_WARNINGS; then
  echo -e "$WARNINGS" | sed 's/^/  ⚠️  /' >&2
fi

if $HAS_CRITICAL || $HAS_WARNINGS; then
  echo "" >&2
  echo "  Reference: /owasp  |  Rules: .claude/rules/security.md" >&2
  echo "───────────────────────────────────────────────────────" >&2
  echo "" >&2
fi

if $HAS_CRITICAL; then
  REASON=$(echo -e "$CRITICAL" | head -1 | sed 's/^\s*//')
  printf '{"hookSpecificOutput":{"permissionDecision":"deny","permissionDecisionReason":"OWASP Security violation blocked: %s — fix before writing. See /owasp for guidelines."}}' "$REASON"
  exit 0
fi

exit 0