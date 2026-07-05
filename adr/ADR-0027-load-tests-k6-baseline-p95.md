---
status: accepted
date: 2026-07-05
decision-makers: "@agnaldo4j"
---

# ADR-0027 — Testes de carga com k6 e baseline p95 documentado

## Context and Problem Statement

Performance efficiency é a única característica ISO/IEC 25010 sem nenhuma medição no projeto
(`docs/quality/audit-2026-07.md`, GAP-AR): não existem testes de carga, baseline de latência
nem número de throughput — uma regressão de performance passaria por todos os gates atuais.
A API tem observabilidade madura (Prometheus/Grafana/OTel), mas nunca foi exercitada sob
carga controlada. Qual ferramenta adotar, e como medir sem introduzir um gate flaky no CI?

## Decision Drivers

- Primeiro sinal de performance: baseline reproduzível de p95 e throughput, versionado.
- Números estáveis: runners compartilhados de CI têm latência ruidosa — baseline em
  ambiente controlado (docker compose local, hardware documentado).
- Scripts versionados no repositório, legíveis em code review.
- Aproveitar o stack existente (Grafana/Prometheus) em vez de introduzir ecossistema paralelo.
- Não corroer a confiança no CI: gate de performance flaky é pior que nenhum gate.

## Considered Options

1. k6 (Grafana) — binário Go, scripts JavaScript, thresholds nativos.
2. Gatling — JVM, DSL Java/Kotlin/Scala, relatórios HTML ricos.
3. JMeter — GUI/XML, ecossistema amplo porém pesado para versionar e automatizar.

## Decision Outcome

**Escolhida: Opção 1 — k6 2.0**, com execução **local contra o docker compose** e
workflow de CI **manual** (`workflow_dispatch`), nunca gate de PR. Scripts em `load/`
com `thresholds` de p95 declarados (sinal executável, não bloqueio); baseline de latência
e throughput medido localmente e **documentado em `docs/quality/`** com hardware, versões
e parâmetros de carga. O k6 é binário leve, roda contra o compose sem JVM extra, e sua
integração nativa com o ecossistema Grafana conversa com a observabilidade já existente.

### Confirmation

Scripts versionados em `load/` (revisáveis como código); baseline versionado em
`docs/quality/` — auditável por diff a cada nova medição; workflow `load-test.yml`
(`workflow_dispatch`) executa os mesmos scripts sob demanda. Verificação em review:
mudança de rota/use case relevante deve atualizar o script correspondente; PR que
altere thresholds sem nova medição documentada é rejeitado.

## Consequences

- Bom: performance efficiency sai de "sem medição" para baseline versionado + scripts repetíveis.
- Bom: regressões detectáveis rodando `k6 run` antes/depois de mudanças de risco.
- Ruim: baseline local depende da máquina; mitigação: hardware e parâmetros documentados
  junto do número, comparações sempre no mesmo ambiente.
- Ruim: sem gate automático, regressão só aparece quando alguém roda; mitigação: workflow
  manual barato de disparar e política de rodar antes de release/tag.

## Pros and Cons of the Options

### Opção 1 — k6
- Bom: binário único, scripts JS versionáveis, `thresholds` p95 nativos, imagem docker oficial, ecossistema Grafana (dashboards prontos).
- Ruim: scripts em JavaScript num projeto Kotlin (linguagem extra, porém isolada em `load/`).

### Opção 2 — Gatling
- Bom: DSL Kotlin disponível (coerência de linguagem); relatórios HTML detalhados.
- Ruim: exige JVM/Gradle no loop de carga (mais pesado), sem integração natural com o stack Grafana; DSL acopla o teste de carga ao build.

### Opção 3 — JMeter
- Bom: maduro e amplamente conhecido.
- Ruim: planos XML hostis a diff/review; automação e thresholds menos ergonômicos.

## More Information

- Branch: `feat/adr-0027-load-tests-k6` · PR: https://github.com/agnaldo4j/kanban-vision-api-kt/pull/224
- Item no board #6: [GAP-AR [M] — Testes de carga + baseline p95](https://github.com/users/agnaldo4j/projects/6) (ciclo P6) — plano de implementação vive lá e no PR 2/2.
- Referências: `docs/quality/audit-2026-07.md` · [k6](https://grafana.com/docs/k6/latest/) ·
  [Observability (wiki)](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Observability) ·
  ADR-0023 (política de ADRs).
