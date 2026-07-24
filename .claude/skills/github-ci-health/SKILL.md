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
| **Test Results** | ❌ **nunca** | Publica o check `Test Results` (EnricoMi). Falso em dois sentidos: `action_fail` default `false` (teste quebrado reprova via `./gradlew testAll`, não aqui) **e** não é branch-protected. Não bloqueia merge |
| **PR Size Soft-Gate** | ❌ **nunca** | *"warn only, never fail the build"* — promessa agora **honrada em código** (GAP-CC), não só no comentário |
| **Flow Metrics** | ❌ **nunca** | não-bloqueante por design (GAP-BI) |

**Corolário:** `PR Size Soft-Gate` ou `Flow Metrics` vermelhos são, **por definição do próprio
workflow**, infraestrutura — eles não têm o direito de reprovar. Se estão vermelhos, comece pelo §1.

✅ **A camada de relatório inteira é tolerante a falha (GAP-CC + GAP-CD).** Os 23 passos que publicam
relatório — comentários (`find-comment` / `create-or-update-comment`), check de testes (`EnricoMi`),
cobertura no PR (`madrapps`), `upload-artifact` e Codecov — levam `continue-on-error: true`, e o
`gh api` do `pr-size` degrada para *"PR size unavailable for this run."*. **Um job vermelho agora
implica gate real vermelho** — não presuma mais que é a API de comentários. Efeito colateral esperado
durante uma queda do GitHub: o `find` não acha o comentário anterior, então o relatório aparece
**duplicado** em vez de editado. Isso é a degradação escolhida, não um bug.

✅ **A lacuna residual do GAP-CC foi fechada no GAP-CD.** `EnricoMi`, `madrapps` e os `upload-artifact`
— que antes podiam reprovar um check obrigatório mesmo sem serem gate — agora estão protegidos. A
invariante (**passo protegido ⟺ o `uses:` é action de publicação de relatório**) é verificada em CI
por `scripts/assert-ci-protection.py`, um step-gate do job `quality`: nenhum gate real ou passo de
setup pode ganhar `continue-on-error`, e nenhum passo de relatório pode perdê-lo.

## 4. Assinaturas de falha conhecidas

| Sintoma no log | Significa | Ação |
|---|---|---|
| `##[error]<!DOCTYPE html>` + `Unicorn! · GitHub` | **HTTP 500** do GitHub (a página do unicórnio) | §1; esperar; **não** mexer no código |
| `invalid character '<' looking for beginning of value` | O `gh`/action recebeu HTML onde esperava JSON — mesma causa | idem |
| `HTTP 503 Service Unavailable` em `gh api .../actions/runs` | API de Actions fora | esperar |
| Vários jobs caindo **no mesmo passo** e **ao mesmo tempo** | Infra, não código | §1 |
| Falha reproduzível **da sua máquina** com `gh api` | O problema não é do runner | §1 |
| Falha em **PRs já mergeados e verdes** | Impossível ser regressão — é infra | §1 |
| `Supply Chain` reprova com CVE numa dep que o **PR não tocou** | **Nova CVE divulgada pelo OSV** numa dep transitiva já resolvida — **repo-wide**, bloqueia `main` e todo PR aberto (não é infra do GitHub nem regressão sua) | Corrigir num **`fix/` PR separado** (não dobrar no PR de feature): bump da família via **BOM** (`platform(...)`). O SBOM agregado cobre **só `runtimeClasspath`** (`includeConfigs` no `build.gradle.kts` raiz) — repita o BOM em **cada módulo** que a resolve (`:mod:dependencies --configuration runtimeClasspath`). ⚠️ O `migrationRuntime` (binário de migração) **NÃO entra no SBOM** — é ponto cego (GAP-DA): cheque-o **à mão** (`--configuration migrationRuntime`) e alinhe lá também |
| `PITest Mutation Report` mostra **`0 mutantes` / `0%` ❌** num ou mais módulos (tipicamente `usecases`/`http_api`) | **Run CANCELADA, não regressão de mutação.** O `pitestAll` roda os módulos **em série** e `usecases`/`http_api` são os **últimos**; se um novo push cancela a run em andamento, esses módulos morrem no meio da execução → PITest reporta **0 mutantes** → `0%` → ❌ **falso**. Um bloco de código com 0 mutantes gerados é o tell (uma queda real de cobertura mantém os mutantes, só sobem os SURVIVED) | **Confira o status da run antes de tratar como regressão:** `gh run view <RUN_ID> --json status,conclusion --jq '.conclusion'` → `cancelled`. Reproduza local o módulo suspeito (`./gradlew :usecases:pitest`) — se der contagem de mutações e `≥ gate`, era artefato do cancelamento. Re-rode a run **completa** (§6), não "conserte" o gate |

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
  na camada de relatório já não bloqueia merge (GAP-CC + GAP-CD — a camada inteira é tolerante a
  falha), mas uma queda de **Actions** (os runners não executam os gates reais) ainda bloqueia. É
  desconforto esperado, não motivo para afrouxar proteção.
- `gh run view --log-failed` só serve com o run **terminado** (*"still in progress"*).
- O status page tem **latência**: um 500 já acontecendo pode ainda não estar publicado. Ausência de
  incidente **não** prova que é culpa nossa — use o §4 (reproduzir fora do CI).
- `## PR Size Report` dizendo *"unavailable"* **não é bug**: é o `pr-size` admitindo que o `gh api` não
  respondeu (ou voltou vazio, como no #288) em vez de fabricar um `✅` a partir de contagem ausente.
- **O `osv-scanner-action@v2.3.8` do CI FALHA (exit 1) numa `unused ignore`; o `osv-scanner` 2.4.0 local só
  avisa (exit 0).** Uma exceção obsoleta no `osv-scanner.toml` (o OSV revisou o advisory e ela não casa mais
  com nenhum pacote do SBOM) passa a **bloquear o gate sozinha** — e um scan local recente **não reproduz**.
  Reproduza com o comando EXATO do CI (`osv-scanner --config=osv-scanner.toml --format markdown --output
  osv-report.md -L build/reports/cyclonedx/bom.json`) e observe o **exit code**, não só o texto; **remova a
  exceção obsoleta**. Detalhes na memória `project_osv_scanner_ci_gotchas`.
- **Um comando FOREGROUND sem limite num step pode PENDURAR o job inteiro.** Um `docker run` (não-`-d`),
  `curl` sem `--max-time`, ou qualquer wait que trave espera até o **limite externo do runner** (default 6h)
  — sem report, sem teardown, queimando minutos. Ironia: é justo a regressão que um smoke/probe existe para
  pegar (ex.: o binário nativo travando num init de DB) que dispara isto (GAP-DA/#338 P2). Sempre **limite o
  comando** (`timeout <s> docker run …` → hang vira falha PRONTA/exit 124 que entra no gate; force `docker rm
  -f` a limpeza) **E** ponha **`timeout-minutes:`** no job como rede de segurança (nenhum job do `ci.yml`
  tinha até o #338 — o `build` nativo agora tem `30`).

## 8. Referências

- **Status**: https://www.githubstatus.com · API: `/api/v2/{status,summary,components,incidents/unresolved}.json`
- Workflow: `.github/workflows/ci.yml` · política: `docs/politicas-explicitas.md` §6
- Cards relacionados: **GAP-CC** (tolerância a falha da camada de comentário — entregue) ·
  **GAP-CD** (camada de relatório inteira + guard `scripts/assert-ci-protection.py` — entregue) ·
  GAP-BH (pr-size) · GAP-BI (flow-metrics)
