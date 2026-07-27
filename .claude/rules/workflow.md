# Workflow — Kanban Board Protocol + Gap Execution

> Full policies: `docs/politicas-explicitas.md`
> GitHub Project #6: https://github.com/users/agnaldo4j/projects/6
> Skill: `/xp-kanban` → section "Board Protocol"

## Session Start — mandatory before any code

> ⚠️ **`--limit` alto e `.status` como STRING — as duas coisas, sempre.** Sem `--limit` o `gh` trunca em
> **30** itens (o board passou de 130) e sem `.status.name` → `.status` o `jq` **aborta**
> (`Cannot index string with string "name"`). Qualquer um dos dois faz a checagem devolver vazio, que este
> protocolo lê como "Doing vazio" → puxa item novo com outro em andamento, **quebrando o WIP=1 que o guard
> existe para proteger**. Medido em 2026-07-27: o snippet anterior tinha os dois defeitos ao mesmo tempo.

```bash
# 1. Check board — is there an item in Doing?
gh project item-list 6 --owner agnaldo4j --limit 500 --format json | \
  jq '.items[] | select(.status == "Doing") | {title: .title, id: .id}'

# 2a. If Doing has item → continue that item
# 2b. If Doing is empty → pull the FIRST item from the top of Todo
gh project item-list 6 --owner agnaldo4j --limit 500 --format json | \
  jq '[.items[] | select(.status == "Todo")] | first | {title: .title, id: .id}'

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
2. **Colheita de lições, aplicada — SÓ após implementação real.** O agente colhe+aplica **apenas quando o
   PR mergeado toca código de produção** (`*/src/main/**`). PR de **processo/doc/skill/ADR/test-only** →
   **fechamento-só** (guard anti-loop: uma melhoria nunca dispara outra — o loop termina em 1 nível). Numa
   implementação real, ele lê a revisão (comentários **inline**, não o resumo), destila as lições
   **duráveis/generalizáveis** e as **aplica** como emenda de skill/regra/rubric + registro em
   `docs/quality/lessons-learned.md`, abrindo um PR de processo `[N]` pronto — não uma lista de tarefas.

Fallback manual (se precisar fazer à mão o passo 1):

> ⚠️ Antes de apagar, confirme que a remota **não recebeu push depois do merge** — um commit pushado após
> o squash merge não entra no PR, não avisa, e o `push --delete` o deixa órfão. Guard medido (e por que as
> alternativas óbvias falham): §1.2 do `.claude/agents/post-merge-harvester.md`.

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