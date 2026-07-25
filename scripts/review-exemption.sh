#!/usr/bin/env bash
# review-exemption.sh — um PR pode ser mergeado SEM parecer do harness?
#
# Decide por ALLOWLIST: isenta só o PR em que TODO arquivo alterado é doc/processo puro.
# Qualquer outra coisa — produção, gates de CI, build, infra, hooks, fitness functions — exige
# dispatch manual de `/pr-review` antes do merge. O caminho não-reconhecido cai no lado seguro.
#
#   uso:  scripts/review-exemption.sh <numero-do-pr> [owner/repo]
#         scripts/review-exemption.sh --paths -        # classifica caminhos vindos do stdin (offline)
#
#   saída: EXEMPT | REVIEW-REQUIRED (+ os arquivos que exigem) | INDETERMINATE
#   exit:  0 = isento
#          1 = exige revisão
#          2 = indeterminado (falha de API / lista vazia — trate como exige)
#         64 = erro de USO (argumento faltando ou inválido) — distinto de 1 de propósito, senão
#              um chamador que decide por exit code lê "há arquivos arriscados" onde houve typo.
#
# Por que existe (não é paranoia abstrata — cada linha veio de um defeito real):
#  · #364  — implementação mergeada com ZERO revisão e nada vermelho (harness falhou em silêncio).
#  · #367  — a 1ª versão desta regra excluía por `*/src/main/**`, isentando Dockerfile/k8s/workflows.
#  · #367  — `gh … | grep -v` engole exit≠0: token expirado devolvia stdout vazio ⇒ "isento" (GAP-CC/#288).
#  · #367  — allowlist por diretório isentava `.claude/hooks/guard-security.sh` e o módulo test-only
#            `architecture/` (as fitness functions), onde apagar um teste deixa todos os gates verdes.
#  · #368  — enumerar subdiretórios de `.claude/` vazava por indireção (`workflow.md` → `/xp-kanban`).
set -euo pipefail

usage() {
    echo "uso: review-exemption.sh <numero-do-pr> [owner/repo]" >&2
    echo "     review-exemption.sh --paths -   (lê caminhos do stdin)" >&2
    exit 64
}

# Allowlist por TIPO, nunca por diretório. `.claude/**` e `adr/**` NÃO estão aqui de propósito:
#  · `.claude/**` — tudo ali dirige como o trabalho é feito e revisado. Enumerar "só os subdiretórios
#    perigosos" já falhou duas vezes: `agents|rules|skills/pr-review` deixava passar `CLAUDE.md`,
#    `skills/github-ci-health` (que documenta o próprio sintoma de gate vazio) e `skills/xp-kanban` —
#    para o qual a regra *protegida* `rules/workflow.md` delega o Board Protocol, um bypass por indireção.
#    Skill nova entra protegida por padrão em vez de depender de alguém lembrar de adicioná-la.
#  · `adr/**` — ADR aceita é imutável e é o gate de toda mudança `[E]` (ADR-0023).
# Os módulos de produto cujo `src/test` é isento vêm ENUMERADOS — `architecture/` fica fora porque é
# test-only: apagar uma fitness function dali não derruba gate nenhum.
# ⚠️ Limitação conhecida e aceita: um PR test-only que ESVAZIA asserções passa como isento (as linhas
#    seguem executadas, então o JaCoCo não cai). Quem revisa um PR de teste deve olhar remoção de
#    asserção, não só cobertura.
ALLOW='^(docs/.*\.md$|training/.*\.md$|[^/]+\.md$|(domain-common|domain-kanban|domain-simulation|usecases|sql_persistence|http_api)/src/test/)'

# Arquivos que CASAM a allowlist acima mas definem o gate — nunca isentos. Avaliado DEPOIS da allowlist
# e unido ao conjunto de risco. Sem isto o guard autoriza a própria remoção.
#  · CLAUDE.md                    — o entry point que carrega regras e aponta as skills
#  · docs/politicas-explicitas.md — critérios de step, quality gates, regras de ADR, branch naming
GATE='^(CLAUDE\.md$|docs/politicas-explicitas\.md$)'

# classify <lista-de-caminhos-em-stdin> — o núcleo puro, sem rede. É o que o teste table-driven
# exercita: uma tabela caminho → esperado pega buraco de allowlist mecanicamente, que foi como os
# vazamentos do #368 apareceram.
classify() {
    local paths="$1" not_allowed gate_bearing
    not_allowed=$(printf '%s\n' "$paths" | grep -vE "$ALLOW" || true)
    gate_bearing=$(printf '%s\n' "$paths" | grep -E "$GATE" || true)
    printf '%s\n%s\n' "$not_allowed" "$gate_bearing" | grep -v '^[[:space:]]*$' | sort -u || true
}

report() {
    local paths="$1" count="$2" risky
    risky=$(classify "$paths")
    if [ -z "$risky" ]; then
        echo "EXEMPT: doc/processo puro ($count arquivos) — merge sem parecer é aceitável; registre isso no PR"
        exit 0
    fi
    echo "REVIEW-REQUIRED: os arquivos abaixo chegam em produção ou nos gates —"
    printf '%s\n' "$risky" | sed 's/^/  /'
    exit 1
}

# --- modo offline: caminhos por stdin (testável sem rede) --------------------------------------
if [ "${1:-}" = "--paths" ]; then
    [ "${2:-}" = "-" ] || usage
    paths=$(cat)
    [ -n "$paths" ] || { echo "INDETERMINATE: nenhum caminho no stdin" >&2; exit 2; }
    report "$paths" "$(printf '%s\n' "$paths" | wc -l | tr -d ' ')"
fi

# --- modo normal: consulta o PR ----------------------------------------------------------------
PR="${1:-}"
[ -n "$PR" ] || usage
# Validação de entrada: `$PR` é interpolado na URL da API (§2.5 — injeção por input não confiável).
[[ "$PR" =~ ^[0-9]+$ ]] || { echo "erro: numero de PR invalido: '$PR'" >&2; usage; }
# Deriva o repo do clone; o default fixo só entra se `gh` não souber dizer (evita apontar para o
# repositório errado ao rodar num fork).
REPO="${2:-$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo agnaldo4j/kanban-vision-api-kt)}"

# `pulls/<n>/files`, não `pr view --json files`: pagina além de 100 arquivos e expõe `previous_filename`
# — sem ele um `git mv http_api/src/main/X.kt docs/X.kt` apareceria só com o caminho novo (doc-only).
# O stderr do `gh` é CAPTURADO e ecoado: sem isso o INDETERMINATE não diz se foi 404, token ou rate
# limit, e alguém precisa reexecutar à mão só para descobrir — mesmo argumento do "distinguir vazio
# de falhou" que este guard defende.
err_file=$(mktemp "${TMPDIR:-/tmp}/review-exemption-err.XXXXXX")
trap 'rm -f "$err_file"' EXIT
if ! files=$(gh api "repos/$REPO/pulls/$PR/files" --paginate \
      --jq '.[] | .filename, (.previous_filename // empty)' 2>"$err_file"); then
    echo "INDETERMINATE: gh api falhou — trate como REVIEW-REQUIRED" >&2
    sed 's/^/  gh: /' "$err_file" >&2
    exit 2
fi

if [ -z "$files" ]; then
    echo "INDETERMINATE: lista de arquivos vazia — trate como REVIEW-REQUIRED" >&2
    exit 2
fi

# A contagem exibida usa SÓ `.filename`: `$files` inclui `previous_filename`, então um rename
# mostraria "2 arquivos" para 1 arquivo alterado.
count=$(gh api "repos/$REPO/pulls/$PR/files" --paginate --jq '.[].filename' 2>/dev/null | wc -l | tr -d ' ')
report "$files" "$count"
