---
name: load-testing
description: >
  Execute, evolua e interprete os testes de carga k6 deste projeto (ADR-0027).
  Use este skill ao rodar o baseline de performance, adicionar cenários/checks ao
  script de jornada, medir o impacto de uma mudança de risco, atualizar thresholds
  ou investigar regressão de latência/throughput. Cobre o script load/simulation-journey.js,
  os perfis smoke/baseline, o workflow manual do CI e a política de medição.
argument-hint: "[profile ou tarefa, ex.: baseline, adicionar check (opcional)]"
allowed-tools: Read, Grep, Glob, Bash, Edit
---

# Load Testing — k6 (ADR-0027)

> **Política central**: thresholds de performance são **sinal executável, nunca gate de
> PR** — runner compartilhado dá latência ruidosa e gate flaky corrói a confiança no CI.
> O baseline oficial é medido **localmente** contra o docker compose e versionado em
> `docs/quality/`. Alterar um threshold exige nova medição documentada lá.

---

## 1. Artefatos

| Artefato | Onde |
|---|---|
| Script de jornada | `load/simulation-journey.js` |
| Baseline vigente | `docs/quality/performance-baseline-2026-07.md` (1.644 req/s, 0% falhas, p95 22ms) |
| Workflow manual | `.github/workflows/load-test.yml` (`workflow_dispatch`, perfis smoke/baseline) |
| Decisão | `adr/ADR-0027-load-tests-k6-baseline-p95.md` |
| Wiki | [Performance Load Testing](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Performance-Load-Testing) |

## 2. Como rodar

```bash
# 1. Stack completo com dev auth
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose up --build -d

# 2. Seed da organização (OBRIGATÓRIO: não há rota nem migration de criação)
docker compose exec -T postgres psql -U kanban -d kanbanvision -c \
  "INSERT INTO organizations (id, name) VALUES ('11111111-1111-4111-8111-111111111111', 'k6-load-org') ON CONFLICT (id) DO NOTHING;"

# 3. Rodar
k6 run load/simulation-journey.js                     # smoke (1 VU, 30s — valida script/ambiente)
k6 run -e PROFILE=baseline load/simulation-journey.js # medição oficial (ramp 20 VUs, 4min)
```

Instalação: `brew install k6` (2.x). Validar sintaxe sem servidor: `k6 inspect load/simulation-journey.js`.

## 3. A jornada e suas decisões de design (não regredir)

Cada iteração = **um cliente distinto**: token dev → `POST /api/v1/simulations` →
5× `POST .../run` → `days` → `snapshot` → `cfd` → `list` (~10 requests).

| Decisão | Por quê |
|---|---|
| `X-Forwarded-For` único por iteração | O rate limit (100 req/min) é chaveado pelo primeiro IP do XFF — sem isso o teste mede o LIMITADOR, não o servidor |
| `exec.test.abort()` se o token falhar | Fail fast — métricas de 401 são lixo |
| `simulationId` só parseado em sucesso; falha em run day encerra a iteração | Resposta de erro pode não ser JSON; 404/500 derivados contaminariam o p95 por endpoint |
| PROFILE inválido falha no init | Spread de `undefined` daria erro obscuro |
| Checks de **invariantes de negócio** (days==5, snapshot.day==1, list contém o id criado) | O k6 valida o contrato fim-a-fim sob carga — regras profundas (WIP, Burrows, determinismo por seed) ficam no domain + PITest |

## 4. Interpretando resultados

- `http_req_failed` > 0 → investigar ANTES de olhar latência (falha barata distorce p95 para baixo).
- Thresholds por endpoint via tag (`http_req_duration{endpoint:create}` etc.) — comparar com a tabela do baseline.
- `list` é o endpoint mais caro e cresce com o volume acumulado — primeiro candidato a paginação se subir.
- Score/relatório: `--summary-export=k6-summary.json` para diff programático entre medições.

## 5. Medindo um baseline novo (obrigatório ao mudar threshold)

1. Máquina ociosa (sem builds paralelos), stack recém-subido (`--build` com o código alvo).
2. Smoke primeiro (100% checks); depois `PROFILE=baseline`.
3. Documentar em `docs/quality/performance-baseline-YYYY-MM.md`: hardware, SO/Docker/k6,
   parâmetros de carga, tabela de resultados (throughput, falhas, p95 geral e por endpoint)
   e comparação com o baseline anterior. Seguir o formato do arquivo de 2026-07.
4. Só então ajustar thresholds no script (referenciando a medição no comentário).
5. Comparações sempre no MESMO ambiente — número de runner de CI não substitui baseline local.

## 6. Workflow manual no CI

`Actions → Load Test (manual)`: input `profile`, sobe compose, seeda org, roda k6, publica
`k6-summary.json` como artifact (30 dias). Usar antes de release/tag como smoke de
performance. **Nunca** transformar em gate de PR (ADR-0027).

## 7. Pitfalls conhecidos

- **Organização precisa existir** — `CreateSimulationUseCase` faz `findById` da org; sem seed, todo create falha.
- **Rotas são versionadas**: `/api/v1/...` (ADR-0022); só `/auth/token` e `/health*` ficam fora.
- **Rate limit por XFF** — se os checks caírem para ~0% de sucesso com 429, o XFF por iteração foi removido/quebrado.
- **Stack quebrado ≠ teste ruim**: o primeiro baseline encontrou 5 bugs de runtime do fat JAR
  (PR #225) — se o smoke falhar, verifique `docker logs kanban-vision-app` antes de mexer no script.
- Baseline com compose parado no meio: `docker compose down` e recomeçar — não reaproveitar containers meio-mortos.
