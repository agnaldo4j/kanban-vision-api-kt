# Baseline de Performance — Julho 2026 (GAP-AR, ADR-0027)

Primeira medição de performance efficiency do projeto (única característica ISO/IEC 25010
sem medição até então). Números medidos **localmente contra o docker compose** — comparações
futuras devem usar o mesmo ambiente e parâmetros.

## Ambiente

| Item | Valor |
|---|---|
| Hardware | Apple M2 Pro, 12 cores, 32 GB RAM |
| SO | macOS 27.0 · Docker 29.3.1 (Desktop) |
| Stack | `docker compose` completo (app + PostgreSQL 16 + Tempo + Prometheus + Grafana), OTel agent 2.29.0 ativo |
| App | fat JAR, `eclipse-temurin:25-jre`, LOG_FORMAT=json |
| Ferramenta | k6 v2.1.0, script `load/simulation-journey.js`, perfil `baseline` |
| Data | 2026-07-05 |

## Carga

- **Jornada por iteração** (cada iteração = 1 cliente distinto via `X-Forwarded-For` único,
  para medir o servidor e não o rate limiter de 100 req/min/cliente): emitir token não conta;
  1× create simulation → 5× run day → days → snapshot → cfd → list (≈10 requests).
- **Perfil `baseline`**: ramp 0→5 VUs (30s) → 5→20 (1m) → 20 constantes (2m) → 20→0 (30s). Total 4m.

## Resultados

| Métrica | Valor |
|---|---|
| Requests | 394.681 (**1.644 req/s**) |
| Jornadas completas | 39.468 (164/s) |
| Falhas HTTP | **0,00%** |
| p95 geral | **22,09 ms** (avg 8,79 ms · p90 17,94 ms · max 297 ms) |

### Latência por endpoint (p95 / mediana / avg)

| Endpoint | p95 | med | avg | Threshold k6 |
|---|---|---|---|---|
| `POST /api/v1/simulations` (create) | **13,83 ms** | 5,99 ms | 6,61 ms | < 300 ms ✅ |
| `POST .../run` (run_day) | **21,53 ms** | 10,45 ms | 10,92 ms | < 500 ms ✅ |
| `GET .../days/{d}/snapshot` | **7,49 ms** | 2,84 ms | 3,32 ms | < 300 ms ✅ |
| `GET .../cfd` | **7,70 ms** | 2,91 ms | 3,42 ms | < 300 ms ✅ |
| `GET /api/v1/simulations?organizationId=` (list) | **31,95 ms** | 16,18 ms | 16,55 ms | < 300 ms ✅ |

Observação: `list` é o endpoint mais caro e cresce com o volume acumulado (39k simulações
criadas durante o teste) — primeiro candidato a paginação se a latência subir em medições futuras.

## Como reproduzir

```bash
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose up --build -d
docker compose exec -T postgres psql -U kanban -d kanbanvision -c \
  "INSERT INTO organizations (id, name) VALUES ('11111111-1111-4111-8111-111111111111', 'k6-load-org') ON CONFLICT (id) DO NOTHING;"
k6 run -e PROFILE=baseline load/simulation-journey.js
```

## Contexto da medição (bugs encontrados no caminho)

Esta primeira medição exigiu consertar o runtime do fat JAR, que estava quebrado em produção
local sem que nenhum gate detectasse (o CI constrói a imagem mas nunca a executa):
OTel agent 2.14.0 incompatível com logback 1.5.37; `embeddedServer` programático não lia o
`application.conf`; Flyway sem plugins por `META-INF/services` não mesclado (KTOR-8987);
logback mudo (logback 1.5.x removeu `<if>`/Janino); Koin sem binding de `MeterRegistry`.
Correções no PR do GAP-AR — os detalhes vivem nos comentários de cada fix.

> Política (ADR-0027): thresholds do k6 são sinal executável, não gate de PR. Alterar
> threshold exige nova medição documentada aqui.
