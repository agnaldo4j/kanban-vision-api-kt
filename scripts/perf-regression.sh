#!/usr/bin/env bash
# Sinal de regressão de performance (ADR-0027 / GAP-BO).
#
# Compara dois summaries do k6 (--summary-export) — uma REFERÊNCIA conhecida-boa e um run
# ATUAL — e falha (exit 1) se alguma métrica-chave regrediu além da tolerância. NÃO é gate de
# PR: rode manualmente, sempre no MESMO ambiente (o baseline é máquina-dependente, ADR-0027).
#
# Uso:
#   k6 run -e PROFILE=baseline --summary-export=atual.json load/simulation-journey.js
#   scripts/perf-regression.sh referencia.json atual.json
#
# Gere a referência salvando um --summary-export de um run conhecido-bom no mesmo ambiente
# (ver docs/quality/performance-baseline-2026-07.md e a skill /load-testing).

set -euo pipefail

# ── Tolerâncias (documentadas; override por env para ambientes ruidosos) ──────
# Defaults calibrados para runs LOCAIS (ADR-0027). O sinal agendado de CI (ADR-0039)
# passa valores largos por env (runner compartilhado é ruidoso) sem mudar o uso local.
THROUGHPUT_DROP_PCT=${THROUGHPUT_DROP_PCT:-10}   # regressão se req/s cair mais que isto (%)
P95_RISE_PCT=${P95_RISE_PCT:-20}                 # regressão se p95 de um endpoint subir mais que isto (%)
P99_RISE_PCT=${P99_RISE_PCT:-25}                 # regressão se p99 (cauda) subir mais que isto (%) — mais largo que p95: a cauda é mais ruidosa
ERROR_RISE_ABS=${ERROR_RISE_ABS:-0.01}           # regressão se a taxa de erro subir mais que isto (absoluto)

# Só endpoints com threshold no journey (baseThresholds) — o k6 só materializa o submétrico
# tagueado no --summary-export quando há threshold para a tag. `days` foi coberto no GAP-CR:
# ganhou threshold (classe GET-read p(95)<300, base em docs/quality/performance-baseline-2026-07-days.md,
# ADR-0027). Uma submetric ausente de um lado é pulada graciosamente abaixo (não erra).
ENDPOINTS=(create run_day days snapshot cfd list)

if [[ $# -ne 2 ]]; then
  echo "uso: $0 <referencia.json> <atual.json>" >&2
  exit 2
fi
REF="$1"
CUR="$2"
for f in "$REF" "$CUR"; do
  [[ -f "$f" ]] || { echo "arquivo não encontrado: $f" >&2; exit 2; }
done

command -v jq >/dev/null || { echo "jq é necessário" >&2; exit 2; }

# jq helpers — retornam "null" se a métrica não existir no summary.
metric() { jq -r "$2" "$1"; }

regressions=0
printf '%-28s %12s %12s %9s  %s\n' "métrica" "referência" "atual" "Δ%" "veredito"
printf '%s\n' "-------------------------------------------------------------------------------"

# ── Throughput (req/s) — regressão se cair ────────────────────────────────────
ref_tp=$(metric "$REF" '.metrics.http_reqs.rate // empty')
cur_tp=$(metric "$CUR" '.metrics.http_reqs.rate // empty')
if [[ -n "$ref_tp" && -n "$cur_tp" ]]; then
  delta=$(jq -rn --argjson r "$ref_tp" --argjson c "$cur_tp" '(($c-$r)/$r*100)|.*100|round/100')
  verdict="OK"
  if jq -en --argjson r "$ref_tp" --argjson c "$cur_tp" --argjson t "$THROUGHPUT_DROP_PCT" '($c < $r*(1-$t/100))' >/dev/null; then
    verdict="REGRESSÃO"; regressions=$((regressions+1))
  fi
  printf '%-28s %12.2f %12.2f %8s%%  %s\n' "throughput (req/s)" "$ref_tp" "$cur_tp" "$delta" "$verdict"
fi

# ── Error rate — regressão se subir ───────────────────────────────────────────
ref_er=$(metric "$REF" '.metrics.http_req_failed.value // empty')
cur_er=$(metric "$CUR" '.metrics.http_req_failed.value // empty')
if [[ -n "$ref_er" && -n "$cur_er" ]]; then
  verdict="OK"
  if jq -en --argjson r "$ref_er" --argjson c "$cur_er" --argjson t "$ERROR_RISE_ABS" '($c > $r + $t)' >/dev/null; then
    verdict="REGRESSÃO"; regressions=$((regressions+1))
  fi
  printf '%-28s %12.4f %12.4f %9s  %s\n' "error rate" "$ref_er" "$cur_er" "-" "$verdict"
fi

# ── p95 por-endpoint — regressão se subir ─────────────────────────────────────
for ep in "${ENDPOINTS[@]}"; do
  q=".metrics[\"http_req_duration{endpoint:$ep}\"][\"p(95)\"] // empty"
  ref_p=$(metric "$REF" "$q")
  cur_p=$(metric "$CUR" "$q")
  [[ -n "$ref_p" && -n "$cur_p" ]] || continue
  delta=$(jq -rn --argjson r "$ref_p" --argjson c "$cur_p" '(($c-$r)/$r*100)|.*100|round/100')
  verdict="OK"
  if jq -en --argjson r "$ref_p" --argjson c "$cur_p" --argjson t "$P95_RISE_PCT" '($c > $r*(1+$t/100))' >/dev/null; then
    verdict="REGRESSÃO"; regressions=$((regressions+1))
  fi
  printf '%-28s %12.2f %12.2f %8s%%  %s\n' "p95 $ep (ms)" "$ref_p" "$cur_p" "$delta" "$verdict"
done

# ── p99 por-endpoint (cauda / tail-latency, GAP-DE) — regressão se subir ───────
# A submetric p(99) só existe se o run usou summaryTrendStats com p(99) (load/simulation-journey.js).
# Ausente de um lado (ex.: referência de CI antiga, pré-p99) → pulada graciosamente, sem erro —
# o p99 entra no sinal quando a referência for re-bootstrapada (ADR-0039).
for ep in "${ENDPOINTS[@]}"; do
  q=".metrics[\"http_req_duration{endpoint:$ep}\"][\"p(99)\"] // empty"
  ref_p=$(metric "$REF" "$q")
  cur_p=$(metric "$CUR" "$q")
  [[ -n "$ref_p" && -n "$cur_p" ]] || continue
  delta=$(jq -rn --argjson r "$ref_p" --argjson c "$cur_p" '(($c-$r)/$r*100)|.*100|round/100')
  verdict="OK"
  if jq -en --argjson r "$ref_p" --argjson c "$cur_p" --argjson t "$P99_RISE_PCT" '($c > $r*(1+$t/100))' >/dev/null; then
    verdict="REGRESSÃO"; regressions=$((regressions+1))
  fi
  printf '%-28s %12.2f %12.2f %8s%%  %s\n' "p99 $ep (ms)" "$ref_p" "$cur_p" "$delta" "$verdict"
done

printf '%s\n' "-------------------------------------------------------------------------------"
if [[ "$regressions" -gt 0 ]]; then
  echo "SINAL: $regressions regressão(ões) além da tolerância (throughput -${THROUGHPUT_DROP_PCT}%, p95 +${P95_RISE_PCT}%, p99 +${P99_RISE_PCT}%, erro +${ERROR_RISE_ABS})." >&2
  exit 1
fi
echo "SINAL: sem regressão além da tolerância."
