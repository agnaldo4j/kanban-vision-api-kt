# Performance Baseline — July 2026 · Envelope do pod: threads e working set (GAP-BU, ADR-0037)

Medição que fecha três dúvidas levantadas pela review do PR #285, **todas sob o envelope real de
produção** (`cpus: 1.0` + `mem_limit: 256m`, artefato Serial+PGO de `main`). Mesma sessão, máquina
ociosa, DB limpo (`down -v`) antes de cada arm. Por ADR-0027 os números absolutos **não** comparam com
outros arquivos — só coluna-a-coluna aqui.

**As três hipóteses foram refutadas, e uma delas era um erro deste repositório** — ver
*Working set vs memory.peak*.

## Environment

| Item | Value |
|---|---|
| Hardware | Apple M2 Pro (host 12 cores) · **Docker Desktop VM: NCPU=4, 16.7 GB** |
| OS | macOS 27.0 · Docker 29.6.1 |
| Envelope | `docker-compose.limits.yml` — `cpus: 1.0` + `mem_limit: 256m` (espelha `k8s/03-deployment.yml`) |
| Artefato | Serial GC + PGO (perfil versionado), imagem 296 MB — build de `main` |
| Tool | k6 v2.1.0, `load/simulation-journey.js`, perfil `baseline` |
| Date | 2026-07-16 |

## Effective CPU Count por quota — a evidência que refuta o "artefato"

`java -XshowSettings:system` sob cada `--cpus`:

| `--cpus` | 0.5 | 1.0 | **2.0** | **4.0** |
|---|---|---|---|---|
| `Effective CPU Count` | 1 | 1 | **2** | **4** |

O Netty dimensiona seu event loop por `availableProcessors`. **A 2,0 CPUs o runtime já reporta 2**,
logo o arm de 2 CPUs do baseline anterior (+31% sobre 1 CPU) foi medido **com** o event loop maior.

## Resultados — threads a 1 CPU

| Arm | Config | req/s | p95 geral | `memory.peak` |
|---|---|---|---|---|
| **Controle** | envelope entregue, sem flag | **1965.4** | **31.34 ms** | 93 MiB |
| Treatment | `-XX:ActiveProcessorCount=2` | 1955.7 | 37.47 ms | 89 MiB |

`-XX:ActiveProcessorCount` é suportado pelo SubstrateVM em runtime (GraalVM ≥ 20.3) e sobrescreve
`Runtime.availableProcessors()` — anexado ao ENTRYPOINT via `command:` do compose, **sem rebuild**.

### Verdict (`scripts/perf-regression.sh`, controle → `ActiveProcessorCount=2`)

| Métrica | Δ | Veredito |
|---|---|---|
| throughput | **−0,49%** | OK (ruído) |
| p95 `create` | **+35,8%** | **REGRESSÃO** |
| p95 `run_day` | +17,8% | OK |
| p95 `snapshot` | −17,1% | OK |
| p95 `cfd` | −13,7% | OK |
| p95 `list` | +13,2% | OK |

## Working set vs memory.peak — a correção de método

Amostragem de `memory.current − inactive_file` (a fórmula do `container_memory_working_set_bytes`) a
cada 2s durante os 4 min de carga, **115 amostras**:

| | min | p50 | **máx** |
|---|---|---|---|
| Working set | 33 MiB | 55 MiB | **92 MiB** |

**`memory.peak` é ruidoso; o working set não.** Dois runs da **mesma configuração** deram
`memory.peak` de **93 MiB** e **152 MiB** — porque `memory.peak` inclui **page cache reclaimável**,
que depende do histórico de I/O do container. O working set fica estável em ~92 MiB de máximo.

**Consequências para o sizing** — e a primeira delas corrige este repositório:

- ⚠️ **`performance-baseline-2026-07-native-tuned-1cpu.md` afirma "a folga de memória real é ~1,7×, não
  ~2,8×". Essa afirmação está ERRADA** e é superada por este snapshot: ela dividiu 256Mi pelo
  `memory.peak` de 152 MiB (inflado por page cache) em vez do working set. Pelo número que **de fato**
  governa eviction: `256Mi / 92 MiB` = **2,8×**. **A ADR-0036 estava correta ao declarar ~2,8×.**
- **`requests.memory: 128Mi` está correto** — 2,3× a mediana (55 MiB) e 1,4× o pico (92 MiB) do working
  set. Memória é recurso **incompressível**: pods acima do seu request são despejados primeiro sob
  pressão do nó (QoS Burstable), então o request tem de cobrir o uso real. Cobre.
- **`limits.memory: 256Mi` se sustenta** com 2,8× sobre o pico, `oom_kill 0` e `max 0`.

## Reading the results

Três hipóteses, três refutações:

1. **"O joelho em 1 CPU (1→2 = só +31%) é artefato de thread pool subdimensionado."** ❌ **Falso.** A
   2,0 CPUs o `Effective CPU Count` é **2** — o Netty já tinha as threads. O +31% é escala real,
   sublinear porque a carga é CPU-bound. **A ADR-0036 acertou ao tratar 1 CPU como joelho.** Custo da
   refutação: uma checagem de 2 minutos, contra os ~20 de uma medição.
2. **"O pod entregue está sub-threaded (1 thread de event loop a 1 CPU)."** ❌ **Falso.** Dar-lhe 2
   processadores deixa o throughput **flat** (−0,49%, ruído) e **piora o p95** (`create` +35,8% →
   regressão): mais threads disputando **um** núcleo só custam context switching. **A 1 CPU o limite é
   a quota, não as threads** — e a flag `-XX:ActiveProcessorCount` **não** deve ser adotada.
3. **"A folga de memória é 1,7×."** ❌ **Falso, e o erro era deste repo** — comparação contra a métrica
   errada. Ver *Working set vs memory.peak*.

**A lição transferível: `memory.peak` serve para provar que o limite nunca foi encostado; o working set
é o que dimensiona `requests`.** Os dois respondem perguntas diferentes, e trocá-los produz conclusões
opostas a partir dos mesmos bytes.

## How to reproduce

```bash
export JWT_DEV_MODE=true TRUSTED_PROXY_COUNT=1 GRAFANA_ADMIN_PASSWORD=admin
docker build -t kanban-vision-api:local .        # Serial + PGO (perfil versionado em main)
docker compose down -v
docker compose -f docker-compose.yml -f docker-compose.limits.yml up -d --no-build
docker compose exec -T postgres psql -U kanban -d kanbanvision -c \
  "INSERT INTO organizations (id, name) VALUES ('11111111-1111-4111-8111-111111111111','k6-load-org') ON CONFLICT (id) DO NOTHING;"
k6 run load/simulation-journey.js                # smoke: 100% checks

# Working set amostrado DURANTE a carga — o número que dimensiona requests.memory.
# (Nenhum baseline anterior fez isto: todos amostravam RSS post-hoc.)
( while docker inspect -f '{{.State.Running}}' kanban-vision-app 2>/dev/null | grep -q true; do
    docker exec kanban-vision-app sh -c \
      'cur=$(cat /sys/fs/cgroup/memory.current); inact=$(grep "^inactive_file " /sys/fs/cgroup/memory.stat | cut -d" " -f2); echo $((cur-inact))'
    sleep 2
  done ) > ws.txt &
k6 run -e PROFILE=baseline --summary-export=controle.json load/simulation-journey.js
sort -n ws.txt | tail -1        # working set máximo

# Threads: flag de runtime anexada ao ENTRYPOINT via `command:` — sem rebuild.
#   services: {app: {command: ["-XX:ActiveProcessorCount=2"]}}
scripts/perf-regression.sh controle.json apc2.json

# Effective CPU Count por quota:
docker run --rm --cpus=2.0 --entrypoint java \
  container-registry.oracle.com/graalvm/native-image:25 -XshowSettings:system -version 2>&1 | grep 'Effective CPU'
```

> Policy (ADR-0027): snapshot imutável; nova medição = novo arquivo. Anteriores desta série:
> `performance-baseline-2026-07-native-tuned.md` (envelope 0,5 CPU, curva de CPU) e
> `performance-baseline-2026-07-native-tuned-1cpu.md` (envelope 1 CPU — **cuja afirmação de folga
> "~1,7×" é superada por este arquivo**).
