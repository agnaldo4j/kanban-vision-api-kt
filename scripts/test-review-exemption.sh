#!/usr/bin/env bash
# test-review-exemption.sh — tabela caminho → esperado para o classificador de isenção de revisão.
#
# Roda OFFLINE (via `review-exemption.sh --paths -`), então serve de gate de CI sem token nem rede.
# Existe porque os dois vazamentos do #368 (`CLAUDE.md`, `.claude/skills/**` fora da lista enumerada)
# foram achados exercitando caminho a caminho — uma tabela pega isso mecanicamente, a leitura do
# regex não pegou nem na primeira nem na segunda rodada de revisão.
#
# Usage:  scripts/test-review-exemption.sh
# Exit 0: todos os casos passam · Exit 1: lista os que falharam.
set -uo pipefail

SCRIPT="$(dirname "$0")/review-exemption.sh"
pass=0
fail=0
failures=""

# check <esperado: EXEMPT|REVIEW> <caminho> [descrição]
check() {
    local expected="$1" path="$2" desc="${3:-}" got rc
    printf '%s\n' "$path" | "$SCRIPT" --paths - >/dev/null 2>&1
    rc=$?
    case "$rc" in
        0) got="EXEMPT" ;;
        1) got="REVIEW" ;;
        *) got="ERRO(exit $rc)" ;;
    esac
    if [ "$got" = "$expected" ]; then
        pass=$((pass + 1))
    else
        fail=$((fail + 1))
        failures="${failures}  ✗ ${path}\n      esperado=${expected} obtido=${got}${desc:+  — $desc}\n"
    fi
}

# --- isento: documentação e registro consultivo ------------------------------------------------
check EXEMPT "README.md"
check EXEMPT "docs/quality/lessons-learned.md"
check EXEMPT "docs/quality/scorecard-2026-10.md"
check EXEMPT "training/algo.md"
check EXEMPT "usecases/src/test/kotlin/com/kanbanvision/usecases/X.kt"
check EXEMPT "http_api/src/test/kotlin/com/kanbanvision/httpapi/Y.kt"

# --- gate-bearing: define o que é revisado/proibido (nunca isento, por mais .md que seja) -------
check REVIEW "CLAUDE.md"                                  "entry point que carrega as regras"
check REVIEW "docs/politicas-explicitas.md"               "quality gates, regras de ADR"
check REVIEW ".claude/agents/pr-harness.md"               "o rubric do harness"
check REVIEW ".claude/agents/post-merge-harvester.md"     "edita o repo e abre PR"
check REVIEW ".claude/rules/security.md"                  "declara o que é proibido"
check REVIEW ".claude/skills/pr-review/SKILL.md"          "a própria decisão de isenção"
check REVIEW ".claude/skills/github-ci-health/SKILL.md"   "vazou no #368 (1a deny-list)"
check REVIEW ".claude/skills/xp-kanban/SKILL.md"          "vazou no #368 — bypass por indireção"
check REVIEW ".claude/skills/ddd/SKILL.md"                "skill nova entra protegida por padrão"
check REVIEW "adr/ADR-0044-erros-tipados.md"              "ADR aceita é imutável e gateia [E]"

# --- produção, build, infra, gates -------------------------------------------------------------
check REVIEW "domain-kanban/src/main/kotlin/com/kanbanvision/domain/model/kanban/Board.kt"
check REVIEW "sql_persistence/src/main/resources/db/migration/V4__x.sql"  "migration Flyway"
check REVIEW ".github/workflows/ci.yml"
check REVIEW ".claude/hooks/guard-security.sh"            "guard de segredo hardcoded"
check REVIEW ".claude/settings.json"
check REVIEW "architecture/src/test/kotlin/com/kanbanvision/architecture/ProjectDependencyGraphTest.kt" \
             "test-only: apagar aqui deixa todos os gates verdes"
check REVIEW "Dockerfile"
check REVIEW "build.gradle.kts"
check REVIEW "buildSrc/src/main/kotlin/kanban.kotlin-common.gradle.kts"
check REVIEW "k8s/03-deployment.yml"
check REVIEW "config/detekt/detekt.yml"
check REVIEW "scripts/review-exemption.sh"                "o próprio guard"
check REVIEW "codecov.yml"
check REVIEW "osv-scanner.toml"
check REVIEW "observability/prometheus-alerts.yml"

# --- um único arquivo arriscado contamina o PR inteiro -----------------------------------------
mixed=$(printf '%s\n%s\n' "docs/quality/lessons-learned.md" "domain-kanban/src/main/kotlin/X.kt")
printf '%s\n' "$mixed" | "$SCRIPT" --paths - >/dev/null 2>&1
if [ $? -eq 1 ]; then pass=$((pass + 1)); else
    fail=$((fail + 1)); failures="${failures}  ✗ doc + src/main juntos devem exigir REVIEW\n"
fi

# --- erro de uso é 64, NUNCA 1 (senão vira "há arquivos arriscados") ---------------------------
"$SCRIPT" >/dev/null 2>&1
[ $? -eq 64 ] && pass=$((pass + 1)) || { fail=$((fail + 1)); failures="${failures}  ✗ sem argumento deve sair 64\n"; }

"$SCRIPT" "nao-e-numero" >/dev/null 2>&1
[ $? -eq 64 ] && pass=$((pass + 1)) || { fail=$((fail + 1)); failures="${failures}  ✗ PR nao-numerico deve sair 64\n"; }

# --- stdin vazio é indeterminado, não isento ---------------------------------------------------
printf '' | "$SCRIPT" --paths - >/dev/null 2>&1
[ $? -eq 2 ] && pass=$((pass + 1)) || { fail=$((fail + 1)); failures="${failures}  ✗ stdin vazio deve sair 2\n"; }

echo "review-exemption: $pass passou, $fail falhou"
if [ "$fail" -gt 0 ]; then
    printf "%b" "$failures"
    exit 1
fi
