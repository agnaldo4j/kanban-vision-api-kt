---
status: accepted
date: 2026-07-19
decision-makers: "@agnaldo4j"
---

# ADR-0039 — Sinal agendado de regressão de performance no CI

## Context and Problem Statement

O ADR-0027 adotou k6 com execução **manual** (`workflow_dispatch`), **nunca gate de PR**, e um baseline
de latência/throughput medido **localmente** e versionado em `docs/quality/`. O comparador
`scripts/perf-regression.sh` (GAP-BO) já diffa dois summaries do k6 e sinaliza regressão além de
tolerância. A própria seção *Consequences* do ADR-0027 registra o ponto fraco: **"sem gate automático,
regressão só aparece quando alguém roda"** — e a mitigação proposta (rodar antes de release/tag) depende
de disciplina humana. Resultado prático: uma regressão de performance pode entrar em `main` e ficar
invisível por semanas.

A restrição dura do ADR-0027 é dupla: (a) **nunca um gate de PR** ("gate flaky é pior que nenhum gate")
e (b) o baseline é **máquina-dependente**, então "comparações sempre no mesmo ambiente" — um número de
runner compartilhado de CI não pode ser tratado como o baseline oficial. A pergunta: **como adicionar um
sinal automatizado de regressão sem violar (a) nem (b)?**

## Decision Drivers

- Detectar regressões grosseiras sem depender de alguém lembrar de rodar (o gap do ADR-0027).
- Preservar o ADR-0027: **nunca** bloquear PR; baseline oficial continua local/versionado.
- Respeitar "mesmo ambiente": um número de CI só se compara com outro número de CI, nunca com o baseline local.
- Não corroer a confiança no CI: nada de sinal flaky — a tolerância precisa absorver o ruído de runner compartilhado.

## Considered Options

1. Status quo — só o workflow manual do ADR-0027 + política de rodar antes de release.
2. **Sinal agendado (cron) que compara a run de CI contra uma referência medida no CI (`load/ci-reference-summary.json`), com tolerância larga, não-bloqueante, reportando no job summary.**
3. Gate de performance por PR (bloqueante).
4. Sinal agendado auto-referente (compara a run atual com a run agendada anterior, sem arquivo commitado).

## Decision Outcome

**Escolhida: Opção 2.** Um novo workflow **agendado** (cron semanal + `workflow_dispatch`) roda o perfil
`baseline` do k6 contra o docker compose no runner e compara o summary atual com uma **referência medida
no CI** e commitada em `load/ci-reference-summary.json`, via `scripts/perf-regression.sh` com
**tolerâncias largas** (throughput −25% / p95 +50% / erro +0.02) para absorver o ruído de runner
compartilhado. O resultado vai para o **job summary**; uma regressão além da tolerância larga deixa a run
**vermelha** (tripwire visível), mas — sendo scheduled-only, sem trigger em PR — **nunca bloqueia um
merge**. Isso satisfaz os dois invariantes do ADR-0027 (nunca gate de PR; comparação mesmo-ambiente
CI-vs-CI) e fecha o buraco que ele mesmo nomeou. A referência de CI é um **tripwire grosseiro** ("a
performance caiu do penhasco?"), explicitamente **não** o baseline autoritativo — este permanece o número
local em `docs/quality/performance-baseline-2026-07.md`, inalterado.

A referência não pode ser medida fora do CI (é máquina-dependente do runner), então o workflow **degrada
graciosamente**: enquanto `load/ci-reference-summary.json` não existir, ele apenas reporta os números
atuais. O bootstrap (disparar o workflow, revisar o artifact, commitar a referência) é uma etapa humana
pós-merge, revisável por diff — o mesmo controle que o ADR-0027 exige para mudanças de threshold.

### Confirmation

O gate é o próprio workflow `.github/workflows/perf-regression.yml`: ele **só** dispara em `schedule` e
`workflow_dispatch` (nunca `pull_request`/`push`), logo é estruturalmente incapaz de bloquear um PR — o
invariante "nunca gate de PR" é verificável por inspeção do bloco `on:`. Em regressão além da tolerância
larga, o step do `perf-regression.sh` sai não-zero e a run agendada fica vermelha (sinal). A referência
(`load/ci-reference-summary.json`) e as tolerâncias são versionadas e auditáveis por diff em review.

## Consequences

- Bom: regressões grosseiras passam a ser detectadas automaticamente (semanalmente), sem depender de
  alguém rodar — exatamente a mitigação que o ADR-0027 deixou em aberto.
- Bom: os dois invariantes do ADR-0027 seguem intactos (nunca gate de PR; comparação mesmo-ambiente).
- Ruim: um runner compartilhado é ruidoso — mitigado por tolerâncias largas (só pega quedas grosseiras) e
  por a run agendada nunca bloquear nada.
- Ruim: a referência de CI precisa ser re-commitada quando a performance muda de forma legítima —
  mitigado por ser um diff revisável e por o número oficial continuar sendo o baseline local.
- Ruim: introduz o primeiro `schedule:` do repo e o primeiro uso de `$GITHUB_STEP_SUMMARY` — custo baixo,
  padrão isolado no novo workflow.

## Pros and Cons of the Options

### Opção 1 — status quo (só manual)
- Bom: custo zero; nada muda.
- Ruim: não fecha o gap; regressão continua invisível até alguém rodar.

### Opção 2 — sinal agendado CI-vs-referência-CI (escolhida)
- Bom: automatizado; respeita os invariantes do ADR-0027; referência revisável por diff.
- Ruim: exige uma referência de CI commitada e re-commit em mudanças legítimas; ruído de runner (mitigado por tolerância larga).

### Opção 3 — gate de performance por PR (bloqueante)
- Bom: bloqueia a regressão antes do merge.
- Ruim: **proibido pelo ADR-0027** — runner compartilhado é flaky; gate flaky corrói a confiança no CI.

### Opção 4 — auto-referente (run atual vs run anterior)
- Bom: sem arquivo commitado; sempre mesmo-ambiente.
- Ruim: **mascara drift gradual** (uma regressão lenta nunca dispara, pois cada run vira a nova referência); depende da API de artifacts.

## More Information

- Branch: `feat/gap-cn-perf-regression-signal` · PR: (link após abrir)
- Item no board #6: **GAP-CN [M]** — o plano de implementação vive lá e no PR, não aqui.
- Evolui (não supersede) o **ADR-0027** (baseline k6 manual). Relacionado: ADR-0036 (runtime nativo
  medido, usa `perf-regression.sh`). Skill `/load-testing`; `scripts/perf-regression.sh`;
  `load/simulation-journey.js`.
