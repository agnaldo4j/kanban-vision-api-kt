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

## After PR Merge — mandatory closure

```bash
git checkout main && git pull origin main
git branch -d feat/gap-X-slug
git push origin --delete feat/gap-X-slug 2>/dev/null || true

gh api graphql -f query='mutation { updateProjectV2ItemFieldValue(input: {
  projectId: "PVT_kwHNWUfOAUhH_w" itemId: "<ID>"
  fieldId: "PVTSSF_lAHNWUfOAUhH_84P7ZSQ"
  value: { singleSelectOptionId: "ca259842" }}) { projectV2Item { id } }}'

# Mark gap [x] in ADR-0004 + update memory/project_adr_progress.md + close session
```

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
| JaCoCo coverage | ≥ 95% per module |
| Detekt violations | 0 (`warningsAsErrors: true`) |
| KtLint | 0 errors |
| `./gradlew testAll` | Green before opening PR |
| PR size | ≤ 400 changed lines |

**Execution order:**
```
P1 Hardening:   GAP-B → GAP-C → GAP-A                              ✅ done
P2 Operations:  GAP-F → GAP-D → GAP-E → GAP-G → GAP-V → GAP-U     ✅ done
P3 Domain:      GAP-W → GAP-O → GAP-P → GAP-Q → GAP-S → GAP-I ✅ → GAP-J → GAP-H → GAP-K
P4 Excellence:  GAP-T → GAP-X → GAP-N → GAP-L → GAP-R → GAP-M
```

> **GAP-J re-escopo (2026-03-23):** Analytics API — `GET /simulations` paginado,
> `GET /simulations/{id}/days` série temporal, `GET /simulations/{id}/cfd` dados CFD.
> Derivado de *The Principles of Product Development Flow* (Reinertsen) via vault Obsidian.
>
> **GAP-X novo (2026-03-23):** `ServiceClass` incompleto — adicionar `DATE_DRIVEN` e
> `INTANGIBLE`. Burrows (Kanban From the Inside) define 4 classes de serviço; o domínio
> atual implementa apenas EXPEDITE e STANDARD.