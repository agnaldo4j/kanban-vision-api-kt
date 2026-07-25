#!/usr/bin/env bash
# review-exemption.sh — um PR pode ser mergeado SEM parecer do harness?
#
# Decide por ALLOWLIST: isenta só o PR em que TODO arquivo alterado é doc/processo puro.
# Qualquer outra coisa — produção, gates de CI, build, infra, hooks, fitness functions — exige
# dispatch manual de `/pr-review` antes do merge. O caminho não-reconhecido cai no lado seguro.
#
#   uso:  scripts/review-exemption.sh <numero-do-pr> [owner/repo]
#   saída: EXEMPT | REVIEW-REQUIRED (+ os arquivos que exigem) | INDETERMINATE
#   exit:  0 = isento · 1 = exige revisão · 2 = indeterminado (falha de API — trate como exige)
#
# Por que existe (não é paranoia abstrata — cada linha veio de um defeito real):
#  · #364  — implementação mergeada com ZERO revisão e nada vermelho (harness falhou em silêncio).
#  · #367  — a 1ª versão desta regra excluía por `*/src/main/**`, isentando Dockerfile/k8s/workflows.
#  · #367  — `gh … | grep -v` engole exit≠0: token expirado devolvia stdout vazio ⇒ "isento" (GAP-CC/#288).
#  · #367  — allowlist por diretório isentava `.claude/hooks/guard-security.sh` e o módulo test-only
#            `architecture/` (as fitness functions), onde apagar um teste deixa todos os gates verdes.
set -euo pipefail

PR="${1:?uso: review-exemption.sh <numero-do-pr> [owner/repo]}"
REPO="${2:-agnaldo4j/kanban-vision-api-kt}"

# Allowlist por TIPO, nunca por diretório. `.claude/**` e `adr/**` NÃO estão aqui de propósito:
#  · `.claude/**` — tudo ali dirige como o trabalho é feito e revisado. Enumerar "só os subdiretórios
#    perigosos" já falhou duas vezes: `agents|rules|skills/pr-review` deixava passar `CLAUDE.md` e
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

# `pulls/<n>/files`, não `pr view --json files`: pagina além de 100 arquivos e expõe `previous_filename`
# — sem ele um `git mv http_api/src/main/X.kt docs/X.kt` apareceria só com o caminho novo (doc-only).
if ! files=$(gh api "repos/$REPO/pulls/$PR/files" --paginate \
      --jq '.[] | .filename, (.previous_filename // empty)' 2>/dev/null); then
    echo "INDETERMINATE: gh api falhou (token/rate limit/PR inexistente) — trate como REVIEW-REQUIRED" >&2
    exit 2
fi

if [ -z "$files" ]; then
    echo "INDETERMINATE: lista de arquivos vazia — trate como REVIEW-REQUIRED" >&2
    exit 2
fi

not_allowed=$(printf '%s\n' "$files" | grep -vE "$ALLOW" || true)
gate_bearing=$(printf '%s\n' "$files" | grep -E "$GATE" || true)
risky=$(printf '%s\n%s\n' "$not_allowed" "$gate_bearing" | grep -v '^[[:space:]]*$' | sort -u || true)

if [ -z "$risky" ]; then
    echo "EXEMPT: doc/processo puro ($(printf '%s\n' "$files" | wc -l | tr -d ' ') arquivos) — merge sem parecer é aceitável; registre isso no PR"
    exit 0
fi

echo "REVIEW-REQUIRED: os arquivos abaixo chegam em produção ou nos gates —"
printf '%s\n' "$risky" | sed 's/^/  /'
exit 1
