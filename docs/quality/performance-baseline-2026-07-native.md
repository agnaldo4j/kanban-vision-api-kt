# Baseline de Performance — Julho 2026 · Native Image em produção (GAP-BB, ADR-0032)

Medição comparativa da troca do artefato de produção: fat JAR sobre GraalVM JDK (JIT,
Fase 1) → **binário Native Image (AOT)**. As DUAS imagens foram medidas em containers na
mesma sessão, com **banco zerado antes de cada rodada** (`docker compose down -v`) e a mesma
VM do Docker (15,6 GB — reconfigurada nesta sessão; números absolutos não comparam com
baselines anteriores, apenas coluna a coluna aqui — política ADR-0027).

## Ambiente

| Item | Valor |
|---|---|
| Hardware | Apple M2 Pro, 12 cores, 32 GB RAM · Docker Desktop VM 15,6 GB |
| SO | macOS 27.0 · Docker 29.6.1 |
| Stack | `docker compose` completo, banco zerado antes de cada rodada |
| App B (controle) | fat JAR sobre `container-registry.oracle.com/graalvm/jdk:25` (JIT, Fase 1/ADR-0030) |
| App A (GAP-BB) | binário `native-image` (app + binário de migração) sobre Oracle Linux 9 slim |
| Ferramenta | k6 v2.1.0, `load/simulation-journey.js`, perfil `baseline` |
| Data | 2026-07-08 |

## Resultados comparativos

| Métrica | JIT (fat JAR) | Native Image | Δ |
|---|---|---|---|
| Requests | 595.601 (2.481,3 req/s) | 553.361 (2.305,3 req/s) | −7,1% |
| Falhas HTTP | 0,00% | **0,00%** | = |
| p95 geral | 15,83 ms | 16,73 ms | +5,7% |
| Startup (log `Application started`) | 1,077 s | **0,120 s** | **−89% (9x)** |
| Memória pós-boot | 354,8 MiB | **73,6 MiB** | **−79%** |
| Memória pós-smoke | 422,4 MiB | **81,7 MiB** | **−81%** |
| Memória pós-baseline | 640,7 MiB | **41,5 MiB** | **−94%** |
| Tamanho da imagem | 820 MB | **326 MB** | **−60%** |

### Latência por endpoint (p95, JIT → Native)

| Endpoint | JIT p95 | Native p95 | Δ |
|---|---|---|---|
| create | 9,43 ms | 10,29 ms | +9,1% |
| run_day | 14,36 ms | 15,73 ms | +9,5% |
| snapshot | 4,95 ms | 6,04 ms | +22,0% |
| cfd | 5,09 ms | 6,18 ms | +21,4% |
| list | 27,21 ms | 25,88 ms | −4,9% |

## Leitura dos resultados

- **O trade-off previsto pela ADR-0030/0032 se confirmou nos dois sentidos**: o JIT mantém
  ~7% mais throughput de pico; o binário nativo entrega **startup 9x mais rápido, uma fração
  da memória (−79% a −94%) e imagem 60% menor** — exatamente os ganhos que motivaram a Fase 2
  (K8s: probes, HPA, densidade, cold start).
- Zero falhas nas duas rodadas (1,1 milhão de requests somados); jornada k6 100% nos dois.
- **Migrations comprovadas em banco virgem** nos dois caminhos nativos: startup do app e
  binário dedicado `kanban-vision-migrate` (Job k8s, ADR-0013) — via `FLYWAY_LOCATIONS=
  filesystem:` (o ClassPathScanner do Flyway não lê resources do binário).
- Bug latente corrigido de passagem: `MIGRATION_POOL_SIZE=1` estourava timeout — o Flyway 12
  usa duas conexões simultâneas; o Job k8s falharia também na JVM.
- **Limitação conhecida (card de follow-up)**: no binário nativo o event loop retém o
  contexto OTel entre requests — o `KtorServerTelemetry` suprime novos spans SERVER e
  encadeia requests num mesmo trace sob carga. Spans JDBC, manuais e a correlação
  log↔trace funcionam; produção k8s tem traces desligados por default (`OTEL_TRACES_
  EXPORTER=none`). Investigação upstream registrada no board.
- Build da imagem exige ~7 GB de RAM (native-image): CI ubuntu-latest (16 GB) comporta;
  Docker Desktop local precisa de VM ≥ ~10 GB.

## Como reproduzir

```bash
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose down -v   # zera o banco
docker build -t kanban-vision-api:local .                               # compila os 2 binários
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose up -d --no-build
docker compose exec -T postgres psql -U kanban -d kanbanvision -c \
  "INSERT INTO organizations (id, name) VALUES ('11111111-1111-4111-8111-111111111111', 'k6-load-org') ON CONFLICT (id) DO NOTHING;"
k6 run load/simulation-journey.js                     # smoke (100% checks)
k6 run -e PROFILE=baseline load/simulation-journey.js # medição oficial
# Controle JIT: buildar a revisão anterior via git worktree e repetir com down -v antes.
```

> Política (ADR-0027): snapshot imutável; nova medição = novo arquivo. Baselines anteriores:
> `performance-baseline-2026-07.md`, `performance-baseline-2026-07-graalvm.md`,
> `performance-baseline-2026-07-otel-sdk.md`.
