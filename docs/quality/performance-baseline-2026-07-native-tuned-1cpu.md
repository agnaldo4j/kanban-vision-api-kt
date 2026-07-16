# Performance Baseline — July 2026 · Envelope de 1 CPU (GAP-BR, ADR-0036)

Medição **complementar** ao `performance-baseline-2026-07-native-tuned.md`, motivada por uma review
do PR #285: aquele snapshot mediu tudo sob `cpus: 0.5` — o envelope **antigo** — mas a ADR-0036 elevou
produção para `limits.cpu: 1000m`. Ou seja, as conclusões daquele arquivo descrevem uma configuração
que a própria ADR aboliu. Este arquivo mede o **envelope novo**.

Mesma sessão, mesma VM, DB limpo (`down -v`) antes de cada arm, `docker-compose.limits.yml` com
`cpus: 1.0` + `mem_limit: 256m`. Números absolutos **não** comparam com outros arquivos (ADR-0027).

## Environment

| Item | Value |
|---|---|
| Hardware | Apple M2 Pro (host 12 cores) · **Docker Desktop VM: NCPU=4, 16.7 GB** |
| OS | macOS 27.0 · Docker 29.6.1 |
| Envelope | `mem_limit: 256m` · **`cpus: 1.0`** (espelha `k8s/03-deployment.yml`) |
| Tool | k6 v2.1.0, `load/simulation-journey.js`, perfil `baseline` |
| Date | 2026-07-16 |

> `Effective CPU Count` = **1** tanto sob `--cpus=0.5` quanto sob `--cpus=1.0` (quota 100000us /
> period 100000us). O mecanismo que penaliza o G1 — uma única thread de GC — **não muda** de 0,5 para
> 1,0; o que muda é quanto o CFS estrangula o app.

## Resultados

| Config | GC | PGO | req/s | p95 | `memory.peak` | `oom_kill` | `max` |
|---|---|---|---|---|---|---|---|
| **Entregue pelo GAP-BR** | Serial | ✅ | **2047.7** | **31.78 ms** | **152 MiB** (59% de 256Mi) | 0 | 0 |
| Controle sem PGO | Serial | ❌ | 1559.1 | 43.92 ms | 191 MiB (75%) | 0 | 0 |
| G1 sem PGO | **G1** | ❌ | 1607.9 | 45.39 ms | 216 MiB (**84%**) | 0 | 0 |

100% dos checks em todos os arms (786.353 no arm entregue), zero falhas, nenhum threshold k6 violado.

### Verdicts (`scripts/perf-regression.sh`)

| Comparação | Resultado |
|---|---|
| Serial → G1 **@ 1.0 CPU** | throughput **+3,13%**, p95 +2…+3% → **sem regressão** |
| Serial → Serial+PGO **@ 1.0 CPU** | **+31,3%** (1559,1 → 2047,7), p95 −28% → sem regressão |

## Reading the results

- **A rejeição do G1 na ADR-0036 é específica do envelope de 0,5 CPU e NÃO transfere para 1,0.** Lá o
  G1 era −22,4% (regressão); aqui é **+3,13%** (sem regressão). A ADR justificou a rejeição com um
  número que deixou de descrever produção no mesmo PR que a criou.
  **A decisão, porém, continua de pé — por outros motivos, medidos aqui:** os +3,13% de throughput
  vêm com **p95 pior** (+2…+3%), **+13% de memória** (216 vs 191 MiB) e **+8% de imagem**. E o custo
  de memória é o que decide: o G1 chega a **84% do limite de 256Mi**, contra 59% do artefato
  entregue. Trocar 3% de throughput por 25pp de margem de OOM é mau negócio.
- **PGO é o que carrega o ganho, não o GC.** No envelope de produção o PGO vale **+31,3%** (mais que
  os +16,7% medidos a 0,5 CPU), enquanto a escolha de GC move ~3%. Confirma a tese central da
  ADR-0036 — o eixo é CPU-por-request, não coleta.
- **A folga de memória real é ~1,7×, não ~2,8×.** Os 80–92 MiB do snapshot anterior eram do envelope
  de 0,5 CPU; a 1,0 CPU o app processa 2,6× mais requests e aloca proporcionalmente: **152 MiB**. O
  limite de 256Mi **se sustenta** (`max 0` ⇒ nunca encostou, `oom_kill 0`), mas com 1,7× de margem —
  não a folga confortável que a ADR-0036 declara. **Reavaliar o limite antes de subir `limits.cpu` de
  novo:** memória escala com throughput, e a 2 CPUs a margem provavelmente evapora.
- `memory.peak` inclui page cache reclaimável ⇒ limite superior conservador. `max 0` é a prova forte:
  o cgroup nunca precisou recuperar nada sob pressão.

## How to reproduce

```bash
export JWT_DEV_MODE=true TRUSTED_PROXY_COUNT=1 GRAFANA_ADMIN_PASSWORD=admin
docker compose down -v
docker build -t kanban-vision-api:local .        # Serial + PGO (perfil versionado)
docker compose -f docker-compose.yml -f docker-compose.limits.yml up -d --no-build
docker compose exec -T postgres psql -U kanban -d kanbanvision -c \
  "INSERT INTO organizations (id, name) VALUES ('11111111-1111-4111-8111-111111111111','k6-load-org') ON CONFLICT (id) DO NOTHING;"
k6 run load/simulation-journey.js                                          # smoke
k6 run -e PROFILE=baseline --summary-export=shipped.json load/simulation-journey.js
docker exec kanban-vision-app sh -c 'cat /sys/fs/cgroup/memory.peak; cat /sys/fs/cgroup/memory.events'
```

> **Lição operacional:** `docker-compose.limits.yml` **tem de espelhar** `k8s/03-deployment.yml`. Ele
> ficou em `cpus: 0.5` enquanto o manifesto foi para `1000m`, e por isso toda medição subsequente teria
> rodado no envelope errado — a mesma classe de erro que produziu a decisão refutada da ADR-0035.
> Ao mexer no `limits.cpu` do pod, mexa no overlay **no mesmo PR**.

> Policy (ADR-0027): snapshot imutável; nova medição = novo arquivo. Anterior desta série:
> `performance-baseline-2026-07-native-tuned.md` (envelope de 0,5 CPU).
