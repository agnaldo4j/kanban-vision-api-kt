# Workflow — Kanban Board Protocol + Gap Execution

> Full policies: `docs/politicas-explicitas.md`
> GitHub Project #6: https://github.com/users/agnaldo4j/projects/6
> Skill: `/xp-kanban` → section "Board Protocol"

## Session Start — mandatory before any code

```bash
# 1. Check board — is there an item in Doing?
gh project item-list 6 --owner agnaldo4j --format json | \
  jq '.items[] | select(.status.name == "Doing") | {title: .title, id: .id}'

# 2a. If Doing has item → continue that item
# 2b. If Doing is empty → pull the FIRST item from the top of Todo
gh project item-list 6 --owner agnaldo4j --format json | \
  jq '[.items[] | select(.status.name == "Todo")] | first | {title: .title, id: .id}'

# 3. Move item to Doing
gh api graphql -f query='mutation { updateProjectV2ItemFieldValue(input: {
  projectId: "PVT_kwHNWUfOAUhH_w" itemId: "<ID>"
  fieldId: "PVTSSF_lAHNWUfOAUhH_84P7ZSQ"
  value: { singleSelectOptionId: "75426285" }}) { projectV2Item { id } }}'

# 4. Create branch from updated main
git checkout main && git pull origin main && git checkout -b feat/gap-X-slug
```

## After PR Merge — invoke the `post-merge-harvester` agent

**Quando o usuário avisar que mergeou um PR, dispare o agente `post-merge-harvester`**
(`.claude/agents/post-merge-harvester.md`) via a Agent tool. Ele faz as duas metades do pós-merge:
1. **Limpeza** — sincroniza a main, apaga a branch, e move o card do #6 para **Done** (⚠️ um `[E]` cujo ADR
   mergeou mas a implementação não **fica em Doing**).
2. **Colheita de lições, aplicada** — lê a revisão daquele PR (os comentários **inline**, não só o resumo),
   destila as lições **duráveis/generalizáveis** e as **aplica** como emenda de skill/regra/rubric +
   registro em `docs/quality/lessons-learned.md`, abrindo um PR de processo `[N]` pronto — não uma lista de
   tarefas. É o loop que impede repetir os mesmos erros a cada ciclo.

Fallback manual (se precisar fazer à mão o passo 1):
```bash
git checkout main && git pull origin main
git branch -d feat/gap-X-slug
git push origin --delete feat/gap-X-slug 2>/dev/null || true
gh api graphql -f query='mutation { updateProjectV2ItemFieldValue(input: {
  projectId: "PVT_kwHNWUfOAUhH_w" itemId: "<ID>"
  fieldId: "PVTSSF_lAHNWUfOAUhH_84P7ZSQ"
  value: { singleSelectOptionId: "ca259842" }}) { projectV2Item { id } }}'
```
Board #6 é a ÚNICA fonte de progresso (ADR-0023); nunca registre progresso em ADRs (imutáveis).

**WIP limit: 1** — never more than one item in Doing.

## GitHub Project IDs

| Resource | ID |
|---|---|
| Project | `PVT_kwHNWUfOAUhH_w` |
| Status Field | `PVTSSF_lAHNWUfOAUhH_84P7ZSQ` |
| Backlog | `8dfbb2d5` |
| Todo | `0fab6fb9` |
| Doing | `75426285` |
| Done | `ca259842` |

## Gap Execution Protocol

**Gap type classification:**

| Type | Meaning | Action |
|------|---------|--------|
| `[N]` Normative | Adds/improves without breaking contracts | Execute directly. 1 gap per session. |
| `[M]` Medium | Adds a new concept or infra artefact | 1 design session + 1 focused PR. |
| `[E]` Structural | Changes contracts, layers or system identity | ADR approved before any code. |

**J-Curve Safety limits — never violate:**

| Measure | Limit |
|---------|-------|
| JaCoCo coverage | ≥ 98% per module |
| Detekt violations | 0 (`warningsAsErrors: true`) |
| KtLint | 0 errors |
| `./gradlew testAll` | Green before opening PR |
| PR size | ≤ 400 changed lines |

**Execution order:** the board #6 Todo column IS the execution order (top = next).
Never duplicate ordering or progress in this file or in ADRs (ADR-0023).