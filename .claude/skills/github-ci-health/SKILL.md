---
name: github-ci-health
description: >
  Diagnostique falhas de CI deste projeto separando defeito NOSSO de indisponibilidade do GitHub.
  Use este skill sempre que alguém disser "o build quebrou", quando um job ficar vermelho, antes de
  "consertar" workflow, e ao decidir entre re-rodar, esperar ou corrigir código. Cobre a triagem por
  githubstatus.com, a anatomia dos jobs do ci.yml (quais são gate REAL e quais são cosméticos),
  assinaturas de falha conhecidas e anti-diagnósticos já refutados.
argument-hint: "[número do PR ou run, ex.: 288 (opcional)]"
allowed-tools: Read, Grep, Glob, Bash
---

# GitHub & CI Health — é o GitHub ou somos nós?

> **A regra que economiza a sessão:** antes de ler log, antes de abrir workflow, antes de qualquer
> hipótese — **cheque o status do GitHub**. Em 2026-07-16 o diagnóstico manual de um "build quebrado"
> custou ~30 min; o incidente estava publicado o tempo todo em `githubstatus.com` como
> **"[major] Degraded REST API Availability"**.

## 1. Triagem de 30 segundos (SEMPRE primeiro)

```bash
# Resumo + incidentes ativos + os componentes que nos afetam
curl -s https://www.githubstatus.com/api/v2/status.json | jq -r '.status | "GitHub: \(.indicator) — \(.description)"'
curl -s https://www.githubstatus.com/api/v2/incidents/unresolved.json | jq -r '.incidents[] | "[\(.impact)] \(.name) (\(.status))"'
curl -s https://www.githubstatus.com/api/v2/components.json | jq -r '.components[] | select(.name|IN("Actions","API Requests","Webhooks","Git Operations","Pull Requests","Packages")) | "  \(.name): \(.status)"'
```

| Componente | O que quebra aqui quando degrada |
|---|---|
| **API Requests** | `find-comment`, `gh api`, comentários de PR, `gh run view`. Desde o GAP-CC a camada de comentário não reprova mais o job — mas ainda **degrada o relatório** (duplicado, ou *"unavailable"*) |
| **Actions** | runners, execução dos jobs |
| **Packages** | push/pull do `ghcr.io` (job `build`) |
| **Webhooks** | o run nem dispara no push |
| **Pull Requests** / **Git Operations** | merge, checkout |

`indicator`: `none` → `minor` → `major` → `critical`. **Qualquer coisa ≠ `none` com `API Requests`
degradado ⇒ suspeite de infra ANTES de suspeitar do código.**

> ⚠️ O `status.json` pode dizer `Actions: operational` e mesmo assim os jobs falharem — porque quem
> está degradado é a **API**, que os passos de comentário consomem. Olhe **componente por componente**,
> não só o indicador global.

## 2. Método: diagnostique por PASSO, nunca por job

Job vermelho não diz nada. **O passo diz tudo.**

```bash
gh pr checks <PR>                                    # visão geral
gh run view <RUN_ID> --json jobs --jq '.jobs[] | "\(.name): \(.conclusion)"'
# O QUE IMPORTA — qual passo caiu:
gh api "repos/agnaldo4j/kanban-vision-api-kt/actions/jobs/<JOB_ID>" \
  --jq '.steps[] | "\(.conclusion // .status)\t\(.name)"'
```

Leia a lista inteira. As perguntas que resolvem:

1. **O gate real passou?** Se `Run quality gates`, `Scan SBOM (osv-scanner)` ou `Smoke test` estão
   `success` e só um passo de relatório caiu ⇒ **o código está bom**.
2. **O mesmo tipo de passo passou em outro lugar?** Se `Find existing Detekt comment` passou e
   `Find existing PITest comment` falhou **no mesmo job**, mesma action, segundos depois ⇒ é a API,
   não config.
3. **O passo seguinte funcionou?** `Post ... report on PR` com `success` depois de o `Find existing`
   falhar ⇒ o problema é pontual na leitura, não na escrita.

## 3. Anatomia do CI deste projeto

| Check | Gate REAL? | Passos que de fato reprovam |
|---|---|---|
| **Quality Gates** | ✅ | `Run quality gates` (Detekt/KtLint/JaCoCo/testes/Konsist) · `Run PITest mutation testing` |
| **Supply Chain (SBOM + SCA)** | ✅ | `Scan SBOM for known vulnerabilities (osv-scanner)` — ADR-0025 |
| **Build & Push Image** | ✅ | build da imagem nativa + **smoke test** (readiness/liveness/401) |
| **Test Results** | ✅ | publicação do resultado dos testes |
| **PR Size Soft-Gate** | ❌ **nunca** | *"warn only, never fail the build"* — promessa agora **honrada em código** (GAP-CC), não só no comentário |
| **Flow Metrics** | ❌ **nunca** | não-bloqueante por design (GAP-BI) |

**Corolário:** `PR Size Soft-Gate` ou `Flow Metrics` vermelhos são, **por definição do próprio
workflow**, infraestrutura — eles não têm o direito de reprovar. Se estão vermelhos, comece pelo §1.

✅ **Desde o GAP-CC, a camada de comentário é tolerante a falha.** Os 14 passos `find-comment` /
`create-or-update-comment` levam `continue-on-error: true`, e o `gh api` do `pr-size` degrada para
*"PR size unavailable for this run."*. **Um job vermelho agora implica gate real vermelho** — não
presuma mais que é a API de comentários. Efeito colateral esperado durante uma queda do GitHub: o
`find` não acha o comentário anterior, então o relatório aparece **duplicado** em vez de editado.
Isso é a degradação escolhida, não um bug.

⚠️ **Lacuna residual:** dentro de `Quality Gates` seguem sem proteção
`EnricoMi/publish-unit-test-result-action` (publica o check `Test Results`),
`madrapps/jacoco-report` e os `upload-artifact`. São os únicos passos cosméticos que ainda podem
reprovar um check obrigatório.

## 4. Assinaturas de falha conhecidas

| Sintoma no log | Significa | Ação |
|---|---|---|
| `##[error]<!DOCTYPE html>` + `Unicorn! · GitHub` | **HTTP 500** do GitHub (a página do unicórnio) | §1; esperar; **não** mexer no código |
| `invalid character '<' looking for beginning of value` | O `gh`/action recebeu HTML onde esperava JSON — mesma causa | idem |
| `HTTP 503 Service Unavailable` em `gh api .../actions/runs` | API de Actions fora | esperar |
| Vários jobs caindo **no mesmo passo** e **ao mesmo tempo** | Infra, não código | §1 |
| Falha reproduzível **da sua máquina** com `gh api` | O problema não é do runner | §1 |
| Falha em **PRs já mergeados e verdes** | Impossível ser regressão — é infra | §1 |

**Teste decisivo:** reproduza fora do CI. Se falha localmente com token válido, o runner é inocente.

```bash
# o que o find-comment faz por baixo:
gh api "repos/agnaldo4j/kanban-vision-api-kt/issues/<PR>/comments?per_page=100" --jq 'length'
# em PRs ANTIGOS e verdes — se falhar neles também, não é o seu PR:
for P in 285 286 287; do gh api "repos/agnaldo4j/kanban-vision-api-kt/issues/$P/comments" --jq 'length'; done
```

## 5. Anti-diagnósticos — hipóteses já refutadas, não repetir

- ❌ **"As tags `@v7` são inválidas; rebaixe `checkout@v4`, `upload-artifact@v4`, `build-push-action@v6`."**
  **FALSO.** O registro mostra `actions/checkout v7.0.0` e `actions/upload-artifact v7.0.1` como
  releases **mais recentes**, e os passos `Checkout`/`Upload artifact` rodam com `success` nos mesmos
  jobs que falham. Aplicar isso **rebaixaria 3 majors** e quebraria o que funciona. Verifique antes de
  acreditar — inclusive se a sugestão vier do próprio GitHub:
  ```bash
  gh api repos/actions/checkout/releases --jq '.[0].tag_name'
  ```
- ❌ **"Job vermelho ⇒ código quebrado."** Neste repo, o `Supply Chain` já reprovou com o osv-scanner
  aprovando (zero CVEs). Leia os passos.
- ❌ **"Re-rodar resolve."** Só se a causa for transitória. Contra um incidente **em curso**, o re-run
  reincide — já aconteceu. Cheque o §1 **antes** de gastar um re-run.

## 6. Decisão: re-rodar, esperar ou corrigir

```
Gate real falhou (testAll/osv-scanner/smoke)?
  └─ SIM → é NOSSO. Reproduza local: ./gradlew testAll · docker build · promtool check rules
  └─ NÃO → só passo cosmético caiu?
       └─ githubstatus: incidente ativo ou API degradada?
            ├─ SIM → ESPERAR. Não mexer no código, não gastar re-run.
            │        (Monitorar: até incidents/unresolved.json esvaziar)
            └─ NÃO → re-rodar:  gh run rerun <RUN_ID> --failed
                     ⚠️ só funciona com o run COMPLETO ("workflow is already running" se houver job em curso)
```

**Nunca** altere `ci.yml` para contornar indisponibilidade transitória: é tratar sintoma e deixa
dívida permanente num arquivo que governa todos os PRs. A tolerância estrutural já existe (GAP-CC) —
se um job vermelho sobreviveu a ela, é gate real, e gate real se corrige no código, não no workflow.

## 7. Pitfalls

- `gh run rerun --failed` **exige o run completo**; com um job `pending` dá
  *"This workflow is already running"*.
- **Branch protection** (3 checks obrigatórios + `enforce_admins`, sem approvals — repo solo): um 500
  na camada de comentário já não bloqueia merge (GAP-CC), mas uma queda de **Actions** ou dos passos
  da lacuna residual ainda bloqueia. É desconforto esperado, não motivo para afrouxar proteção.
- `gh run view --log-failed` só serve com o run **terminado** (*"still in progress"*).
- O status page tem **latência**: um 500 já acontecendo pode ainda não estar publicado. Ausência de
  incidente **não** prova que é culpa nossa — use o §4 (reproduzir fora do CI).
- `## PR Size Report` dizendo *"unavailable"* **não é bug**: é o `pr-size` admitindo que o `gh api` não
  respondeu (ou voltou vazio, como no #288) em vez de fabricar um `✅` a partir de contagem ausente.

## 8. Referências

- **Status**: https://www.githubstatus.com · API: `/api/v2/{status,summary,components,incidents/unresolved}.json`
- Workflow: `.github/workflows/ci.yml` · política: `docs/politicas-explicitas.md` §6
- Cards relacionados: **GAP-CC** (tolerância a falha da camada de comentário — entregue) · GAP-BH
  (pr-size) · GAP-BI (flow-metrics)
