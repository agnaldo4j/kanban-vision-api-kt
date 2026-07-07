# Baseline de Performance — Julho 2026 · Fase 1 GraalVM (GAP-AY, ADR-0030)

Medição comparativa da troca do runtime de produção: `eclipse-temurin:25-jre-alpine` →
**Oracle GraalVM JDK 25 (Graal JIT)**, Fase 1 da ADR-0030. As DUAS imagens foram medidas
**na mesma sessão, no mesmo ambiente e com o mesmo protocolo** — os números absolutos diferem
do baseline de 2026-07-05 (máquina em condição diferente); a comparação válida é a coluna a
coluna desta tabela, não contra o documento anterior (política ADR-0027: mesmo ambiente).

## Ambiente

| Item | Valor |
|---|---|
| Hardware | Apple M2 Pro, 12 cores, 32 GB RAM |
| SO | macOS 27.0 · Docker 29.6.1 (Desktop) |
| Stack | `docker compose` completo (app + PostgreSQL 16 + Tempo + Prometheus + Grafana), OTel agent 2.29.0 ativo |
| App A (controle) | fat JAR, `eclipse-temurin:25-jre-alpine`, LOG_FORMAT=json |
| App B (Fase 1) | fat JAR, `container-registry.oracle.com/graalvm/jdk:25` (Graal JIT), LOG_FORMAT=json — build resolvido pela tag **na sessão de medição**: Oracle GraalVM 25.0.3+9.1 (`java -version`); a tag `:25` flutua por patch |
| Ferramenta | k6 v2.1.0, script `load/simulation-journey.js`, perfil `baseline` |
| Data | 2026-07-07 |

## Carga

Mesma jornada e perfil do baseline 2026-07: cada iteração = 1 cliente distinto
(`X-Forwarded-For` único); 1× create → 5× run day → days → snapshot → cfd → list (≈10 requests).
Perfil `baseline`: ramp 0→5 VUs (30s) → 5→20 (1m) → 20 constantes (2m) → 20→0 (30s). Total 4m.

## Resultados comparativos

| Métrica | Temurin 25 (JRE) | Oracle GraalVM 25 (JIT) | Δ |
|---|---|---|---|
| Requests | 210.471 (876,9 req/s) | 235.231 (**980,0 req/s**) | **+11,8%** |
| Jornadas completas | 21.047 (87,7/s) | 23.523 (98,0/s) | +11,8% |
| Falhas HTTP | 0,00% | **0,00%** | = |
| p95 geral | 60,47 ms | **47,10 ms** | **−22,1%** |
| Startup (log `Application started`) | 1,564 s | 1,684 s | ≈ empate |
| Memória aquecida (pós-smoke) | 371,5 MiB | 379,8 MiB | ≈ empate |
| Memória pós-baseline (pico de carga) | 464,9 MiB | 515,2 MiB | +10,8% |
| Tamanho da imagem | 297 MB | 820 MB | +176% |

### Latência por endpoint (p95, Temurin → GraalVM)

| Endpoint | Temurin p95 | GraalVM p95 | Δ | Threshold k6 |
|---|---|---|---|---|
| `POST /api/v1/simulations` (create) | 22,91 ms | **20,34 ms** | −11,2% | < 300 ms ✅ |
| `POST .../run` (run_day) | 38,01 ms | **33,02 ms** | −13,1% | < 500 ms ✅ |
| `GET .../days/{d}/snapshot` | 11,60 ms | **10,52 ms** | −9,3% | < 300 ms ✅ |
| `GET .../cfd` | 11,72 ms | **10,58 ms** | −9,7% | < 300 ms ✅ |
| `GET /api/v1/simulations?organizationId=` (list) | 103,78 ms | **73,50 ms** | −29,2% | < 300 ms ✅ |

## Leitura dos resultados

- **Graal JIT entrega o ganho esperado da Fase 1**: throughput +11,8% e p95 geral −22%,
  com o maior ganho justamente no endpoint mais caro (`list`, −29%). Zero falhas nas duas rodadas.
- **Startup e memória aquecida são equivalentes** — esperado: os ganhos de startup/memória são
  promessa da Fase 2 (Native Image), não do modo JIT.
- **Trade-offs**: imagem 820 MB vs 297 MB (JDK completo Oracle Linux vs JRE alpine) e +10,8% de
  memória sob pico. Aceitos na Fase 1; a Fase 2 (Native Image) inverte drasticamente ambos.
- **Memória vs limits do k8s (512Mi)**: as medições de memória foram feitas **sem cgroup limit**
  (Docker Desktop, sem `--memory`) — o heap cresce livre e o número não mapeia 1:1 para o pod.
  Sob o limit de 512Mi do Deployment, a JVM auto-dimensiona o heap pelo cgroup
  (`MaxRAMPercentage`), comportamento idêntico nos dois runtimes — o Temurin sem limite também
  chegou a 464,9 MiB. O orçamento de memória do pod é questão pré-existente do rollout, não desta
  troca; monitorar `container_memory_working_set_bytes` no primeiro deploy da Fase 1.
- O healthcheck do compose (`wget` dentro do container) e o smoke da jornada (100% checks)
  passam na base nova; `useradd` uid 1000 preservado para o `securityContext` do k8s.

## Como reproduzir

```bash
JWT_DEV_MODE=true GRAFANA_ADMIN_PASSWORD=admin docker compose up --build -d
docker compose exec -T postgres psql -U kanban -d kanbanvision -c \
  "INSERT INTO organizations (id, name) VALUES ('11111111-1111-4111-8111-111111111111', 'k6-load-org') ON CONFLICT (id) DO NOTHING;"
k6 run -e PROFILE=baseline load/simulation-journey.js
# Controle Temurin: buildar o Dockerfile da revisão anterior (git show <rev>:Dockerfile),
# retaguear como kanban-vision-api:local e recriar só o serviço app — mesma sessão, mesmo stack.
```

> Política (ADR-0027): thresholds do k6 são sinal executável, não gate de PR. Este documento é
> um snapshot imutável; nova medição = novo arquivo. Baseline anterior: `performance-baseline-2026-07.md`.
