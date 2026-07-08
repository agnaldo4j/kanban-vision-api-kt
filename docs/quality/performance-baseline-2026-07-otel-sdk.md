# Baseline de Performance — Julho 2026 · Traces sem javaagent (GAP-AZ, ADR-0031)

Medição comparativa da remoção do OTel Java Agent: traces migraram para OTel SDK +
instrumentação de biblioteca em build time (ADR-0031). As DUAS imagens rodam Oracle GraalVM
JDK 25 (Fase 1, ADR-0030) e foram medidas **na mesma sessão, com banco ZERADO antes de cada
rodada** (`docker compose down -v`) — o endpoint `list` cresce com o volume acumulado e
contamina qualquer comparação sem reset (lição desta medição).

## Ambiente

| Item | Valor |
|---|---|
| Hardware | Apple M2 Pro, 12 cores, 32 GB RAM |
| SO | macOS 27.0 · Docker 29.6.1 (Desktop) |
| Stack | `docker compose` completo, banco zerado antes de cada rodada |
| App B (controle) | fat JAR + OTel javaagent 2.29.0 no ENTRYPOINT (commit da Fase 1) |
| App A (GAP-AZ) | fat JAR com instrumentação de biblioteca 2.29.0-alpha (ktor-3.0, jdbc, logback-mdc) + SDK autoconfigure 1.63.0 — **sem javaagent** |
| Ferramenta | k6 v2.1.0, `load/simulation-journey.js`, perfil `baseline` |
| Data | 2026-07-07 |

## Resultados comparativos

| Métrica | Com javaagent | Sem javaagent (SDK) | Δ |
|---|---|---|---|
| Requests | 331.521 (1.381,0 req/s) | 365.861 (**1.524,3 req/s**) | **+10,4%** |
| Falhas HTTP | 0,00% | **0,00%** | = |
| p95 geral | 25,99 ms | **24,10 ms** | −7,3% |
| Startup (log `Application started`) | 1,97 s | 1,825 s | −0,15 s |
| Memória pós-smoke | 365 MiB | **277 MiB** | **−24%** |
| Memória pós-baseline (pico) | 738,8 MiB | **354,9 MiB** | **−52%** |

### Latência por endpoint (p95, agente → SDK)

| Endpoint | Agente p95 | SDK p95 | Δ |
|---|---|---|---|
| create | 16,12 ms | **13,84 ms** | −14,1% |
| run_day | 25,97 ms | **22,15 ms** | −14,7% |
| snapshot | 8,85 ms | **7,34 ms** | −17,1% |
| cfd | 8,99 ms | **7,34 ms** | −18,4% |
| list | 35,80 ms | 35,75 ms | ≈ |

## Paridade de sinais verificada (Confirmation da ADR-0031)

Num único trace da jornada (Tempo, compose local): span HTTP server (scope
`io.opentelemetry.ktor-3.0`) + span manual `simulation.run_day` (scope `kanban-vision-api`)
+ spans JDBC `INSERT/SELECT` (scope `io.opentelemetry.jdbc`) — todos aninhados; `trace_id`
e `span_id` presentes nos logs JSON e resolvendo para o trace correto no Tempo.

## Leitura dos resultados

- **Remover o agente melhorou tudo**: +10,4% de throughput, p95 menor em todos os endpoints
  de negócio e **metade da memória sob pico** — o custo real do agente estava na memória
  (instrumentação de bytecode + estado interno), não no startup (~0,15s).
- Correção de bug embutida: os campos `traceId`/`spanId` (camelCase) dos logs JSON **nunca
  foram populados** — as chaves reais do MDC OTel são `trace_id`/`span_id` (snake_case);
  a correlação log↔trace só passou a funcionar de fato nesta entrega.
- **Pitfall descoberto**: rodar o fat JAR novo COM o javaagent quebra o boot
  (`DuplicatePluginException: OpenTelemetry` — o agente e o `KtorServerTelemetry` colidem).
  Não existe modo híbrido: agente OU biblioteca.
- **Protocolo de medição corrigido**: sempre `docker compose down -v` antes de cada rodada —
  o acúmulo de simulações penaliza o `list` e distorce o p95 geral entre rodadas.

## Como reproduzir

```bash
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose down -v   # zera o banco
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose up --build -d
docker compose exec -T postgres psql -U kanban -d kanbanvision -c \
  "INSERT INTO organizations (id, name) VALUES ('11111111-1111-4111-8111-111111111111', 'k6-load-org') ON CONFLICT (id) DO NOTHING;"
k6 run load/simulation-journey.js                     # smoke (100% checks)
k6 run -e PROFILE=baseline load/simulation-journey.js # medição oficial
# Controle: buildar a revisão anterior via git worktree (o Dockerfile antigo com o código
# novo NÃO sobe — DuplicatePluginException) e repetir com down -v antes.
```

> Política (ADR-0027): thresholds do k6 são sinal executável, não gate de PR. Snapshot
> imutável; nova medição = novo arquivo. Baselines anteriores: `performance-baseline-2026-07.md`
> (Temurin+agente), `performance-baseline-2026-07-graalvm.md` (Fase 1).
