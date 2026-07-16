---
status: superseded
superseded-by: ADR-0036
date: 2026-07-16
decision-makers: "@agnaldo4j"
---

# ADR-0035 — Parâmetros de runtime de produção: G1 + PGO no nativo e right-size do k8s

> **Status: Supersedida pela [ADR-0036](ADR-0036-runtime-nativo-medido-pgo-cpu.md)** (2026-07-16).

> Produção roda o binário GraalVM Native Image (ADR-0030/0032) com **zero tuning**: Serial GC
> default, sem heap policy, e pods dimensionados para JVM (`256Mi`/`512Mi`) enquanto o nativo usa
> ~40–82 MiB. Esta ADR adota um **perfil de runtime nativo tunado** — `--gc=G1` + PGO (Oracle
> GraalVM, build-time) para recuperar throughput, `--gc=epsilon` no binário de migração, e
> **right-size** da memória do k8s ao footprint real — com os números finais fixados por uma
> medição k6 no mesmo ambiente (ADR-0027).

## Context and Problem Statement

O runtime de produção é o Native Image (ADR-0032). Duas lacunas de NFR permanecem sem decisão
explícita:

- **Throughput.** A migração para o nativo trocou **−7,1% throughput / +5,7% p95** por startup 9×
  e memória −79/−94% (`docs/quality/performance-baseline-2026-07-native.md`). O trade-off foi
  aceito na ADR-0032, mas há margem para recuperar parte do throughput via GC e otimização
  guiada por perfil (PGO) — hoje o nativo roda com os **defaults do SubstrateVM (Serial GC)**,
  sem nenhum `buildArg` de GC/otimização e sem heap policy.
- **Sizing.** O `k8s/03-deployment.yml` pede `256Mi`/limita `512Mi` — valores dimensionados para
  a JVM. O footprint nativo medido é ~40–82 MiB (73,6 post-boot · 81,7 post-smoke · 41,5
  post-baseline), i.e. o pod está **superdimensionado ~6×**: desperdiça capacidade de cluster e
  o autoscaler de memória (HPA target 80%) praticamente nunca dispara.

**Pergunta:** que GC/heap adotar no binário nativo e como dimensionar a memória do container em
produção?

## Decision Drivers

- Recuperar parte do throughput perdido na migração para o nativo, sem abrir mão de startup/memória.
- Right-size honesto ao artefato real (o nativo, não a JVM), liberando capacidade e corrigindo o HPA.
- **Validação empírica** — qualquer parâmetro só entra com medição k6 no mesmo ambiente (ADR-0027),
  **e sob o envelope de recursos do pod** (memória **e** CPU): um número medido em host livre não
  descreve produção.
- Oracle GraalVM já é a edição em uso (`container-registry.oracle.com/graalvm/native-image:25`) →
  **G1 e PGO estão disponíveis** sem troca de licença/edição (na CE só haveria Serial/Epsilon).
- Respeitar a imutabilidade por política de `build.gradle.kts`/`Dockerfile` (mudança só via ADR).

## Considered Options

1. **Status quo** — Serial GC, sem tuning; pods JVM-sized (`256Mi`/`512Mi`).
2. **Só right-size** — reduzir a memória do k8s ao footprint nativo; GC/heap intocados nos defaults.
3. **Perfil de runtime nativo tunado** — `--gc=G1` + PGO no binário da API, `--gc=epsilon` no
   binário de migração, sem `-Xmx` fixo, **e** right-size do k8s com margem para o overhead do G1
   e para spikes. **(escolhida)**

## Decision Outcome

**Opção escolhida: 3 — perfil de runtime nativo tunado.** Produção passa a rodar um runtime
deliberado e medido, não os defaults:

- ✅ **GC da API: `--gc=G1`** (Oracle GraalVM, build-time em `graalvmNative.buildArgs`) — o G1
  paralelo bate o Serial single-threaded **quando há núcleos para paralelizar**.
  ⚠️ **Isto é hipótese a provar, não premissa.** O `k8s/03-deployment.yml` limita o pod a
  `limits.cpu: 500m`, e sob essa quota o runtime reporta **`Effective CPU Count: 1`** (medido:
  `java -XshowSettings:system` com `--cpus=0.5` → CPU Quota 50000us / Period 100000us). Com 1 CPU
  efetiva o G1 roda com ~1 thread de GC e **pode ser mais lento que o Serial** — paga o overhead do
  coletor paralelo sem receber o paralelismo. Por isso o G1 só entra se a Confirmation abaixo provar
  ganho **sob o cap de CPU de produção**; medição em host sem limite não autoriza esta decisão.
- ✅ **PGO** (`--pgo`) — a maior alavanca de throughput do nativo: *instrumented build* → captura
  de perfil com workload representativo (k6) → *optimized build*.
- ✅ **Binário de migração: `--gc=epsilon`** (no-op) — carga bounded e curta; nunca para a API
  long-running. Micro-otimização segura.
- ✅ **Heap: sem `-Xmx` fixo.** Confiar na cgroup-awareness do SubstrateVM. Se a medição indicar
  necessidade de limitar o heap sob o container right-sized, usar a knob **correta para o G1** —
  `-XX:MaxRAMPercentage=N` (relativa à memória disponível) ou `-Xmx` explícito. Atenção:
  `-XX:MaximumHeapSizePercent` é opção do **Serial GC** e **não** ajusta o heap do binário G1
  (Oracle, *Memory Management* do Native Image) — não usar no perfil desta ADR. `-Xms` não se
  aplica (não há warm-up de JIT).
- ✅ **Right-size k8s:** memória da API de `256Mi/512Mi` → alvo **`request 128Mi` / `limit 256Mi`**
  (mantém ~3× sobre o pico ~82 MiB, com margem para a base do G1 e spikes); mesmo tratamento no
  Job de migração; reavaliar o target de memória do HPA (na prática a CPU 70% é quem escala).
  **Os números finais são fixados pela medição** do GAP-BR — os alvos acima são o ponto de partida.

O trabalho de implementação (mudar `build.gradle.kts`, `Dockerfile`, `k8s/`, medir e documentar)
é rastreado como **GAP-BR** no board #6 — não neste documento.

### Confirmation

A Confirmation se cumpre quando a implementação (GAP-BR) documentar, **no mesmo ambiente**
(ADR-0027, hardware e parâmetros registrados junto do número), um baseline k6 novo em
`docs/quality/performance-baseline-YYYY-MM-native-tuned.md` provando:

- **(a) Throughput ≥** o nativo atual e recuperando em direção ao JIT — senão G1/PGO não se
  justificam e uma ADR superseding reverte a decisão. ⚠️ **Medido sob o envelope de CPU do pod**
  (`limits.cpu: 500m` → `cpus: 0.5`), não em host livre: a justificativa do G1 é paralelismo, e sob
  a quota de produção o runtime vê **1 CPU efetiva**. Um ganho medido em host irrestrito **não**
  satisfaz este critério — mediria uma configuração que não existe em produção. O control (Serial)
  roda sob o mesmo cap, na mesma sessão;
- **(b) Memória sob limite real:** o app precisa rodar **com o `limit` de memória alvo aplicado ao
  container** (não desbloqueado), e a medição registrar `container_memory_working_set_bytes` sob o
  baseline k6 **dentro** desse limite, **sem OOMKill nem throttle**, com margem de spike observada.
  ⚠️ O `load-test.yml` atual sobe `docker compose` **sem `--memory` nem `--cpus`** e o Prometheus
  raspa só o `/metrics` do app — isso valida (c) mas **não** exercita o envelope do pod. Portanto
  GAP-BR **deve** incluir um run com o container limitado ao alvo **nas duas dimensões** — memória
  **e** CPU (`compose` com `mem_limit` + `cpus`, `docker run --memory=256Mi --cpus=0.5`, ou um run
  k8s/kind) — coletando o working-set via cgroup/cAdvisor. O envelope de CPU não é detalhe de
  sizing: é o que decide (a), porque governa quantas threads o G1 tem;
- **(c)** p95 por-endpoint dentro do baseline vigente.

O gate de **throughput/p95** continua sendo o workflow manual k6 (`.github/workflows/load-test.yml`,
ADR-0027); o gate de **memória/right-size** exige adicionalmente o run com limite de container
acima. Ambos versionados em `docs/quality/` — nunca gate de PR. Uma mudança de parâmetro sem nova
medição documentada é rejeitada no review.

## Pros and Cons of the Options

### Opção 1 — Status quo
- ✅ Zero esforço/risco; o nativo já é estável em produção.
- ❌ Deixa throughput na mesa e mantém a memória ~6× superdimensionada (desperdício + HPA distorcido).

### Opção 2 — Só right-size
- ✅ Ganho de capacidade imediato e de baixo risco; corrige o HPA de memória.
- ❌ Não recupera nada do throughput perdido; ignora que G1/PGO já estão disponíveis na edição atual.

### Opção 3 — Perfil tunado (escolhida)
- ✅ Ataca throughput **e** sizing na mesma decisão; runtime deliberado e medido; Epsilon zera o
  overhead de GC na migração.
- ❌ G1 + PGO aumentam imagem e memória base (tensão com o right-size); PGO adiciona um build de
  3 passos (CI nativo mais longo + captura de workload). Mitigações abaixo.

## Consequences

- **Bom:** recupera parte do throughput perdido no nativo; runtime de produção deliberado e
  auditável por medição; pods right-sized liberam capacidade de cluster e corrigem o autoscaler de
  memória; Epsilon elimina o overhead de GC no binário de migração (bounded).
- **Ruim (com mitigação):**
  - G1 + PGO **aumentam imagem e memória base** — tensão direta com o objetivo de right-size.
    *Mitigação:* manter margem no `limit` e deixar a medição k6 + `container_memory_working_set_bytes`
    fixar os números finais (os `128Mi/256Mi` são ponto de partida, não dogma).
  - **PGO adiciona um build de 3 passos** (instrument → profile → optimize) → CI nativo mais longo
    e um passo de captura de workload representativo. *Mitigação:* documentar o procedimento no
    skill `/graalvm` (§8) como parte do GAP-BR.
  - G1/PGO travam em **Oracle GraalVM** — já é a edição em uso; não há regressão de licença.
  - Mudanças em `build.gradle.kts` / `Dockerfile` / `k8s/` são config imutável → seguem o fluxo
    ADR (esta) + PR de execução (GAP-BR).
- **Critério de reavaliação:** se o baseline k6 do GAP-BR mostrar que G1/PGO **não** recuperam
  throughput material **ou** estouram o budget de memória right-sized, abrir uma ADR superseding
  que reverte para **Serial GC + apenas right-size** (equivalente à Opção 2).
- **Ramificação prevista (cap de CPU):** se o G1 ganhar **sem** limite de CPU mas empatar/perder sob
  `cpus: 0.5`, a conclusão não é "o G1 é ruim" — é que **o lever é `limits.cpu`**, não o GC. Elevar a
  CPU do pod é outra decisão (custo de cluster, densidade, HPA) e exige **ADR própria**; não pode ser
  contrabandeada no GAP-BR. Neste cenário o GAP-BR entrega right-size + epsilon e a superseding
  documenta o resultado negativo **medido** do G1.

## Related

- ADR-0030 / ADR-0031 / ADR-0032 — GraalVM Native Image em produção (JIT vs AOT, OTel, imagem nativa).
- ADR-0027 — baseline k6 (o mecanismo de medição/Confirmation desta ADR).
- ADR-0023 — política de ADRs (ADR-first para `[E]`; imutabilidade; MADR 4.0).
- Skills `/graalvm` (§8 — runtime tuning: GC/heap/PGO) e `/local-and-production-environment` (sizing).
- Baseline de referência: `docs/quality/performance-baseline-2026-07-native.md`.
- Board #6 — item **GAP-BR** (o plano de execução e o progresso vivem lá, não aqui).
