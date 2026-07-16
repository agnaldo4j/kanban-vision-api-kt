---
status: superseded
superseded-by: ADR-0037
date: 2026-07-16
decision-makers: "@agnaldo4j"
supersedes: ADR-0035
---

# ADR-0036 — Runtime nativo medido: PGO + CPU do pod, sem G1

> **Status: Supersedida pela [ADR-0037](ADR-0037-envelope-recursos-pod.md)** (2026-07-16).

> Supersede a **ADR-0035**. A medição do GAP-BR (`docs/quality/performance-baseline-2026-07-native-tuned.md`)
> refutou o G1 **sob o envelope real de produção** (−22,4%) e revelou que o gargalo nunca foi o GC:
> é o `limits.cpu: 500m`, que custa **−69%** de throughput. Esta ADR fixa o que a medição sustenta —
> **PGO** (+16,7%), **`limits.cpu: 1000m`** (+153%), right-size da memória e Epsilon na migração — e
> **rejeita o G1**.

## Context and Problem Statement

A ADR-0035 decidiu `--gc=G1` + PGO + right-size para recuperar os −7,1% de throughput que a migração
para Native Image (ADR-0032) custou. Ela própria condicionou tudo a medição, e — após a review do PR
#283 — exigiu que a medição rodasse **sob o cap de CPU do pod**, porque a justificativa do G1 é
paralelismo e o pod é limitado a meia CPU.

A medição foi feita (GAP-BR, 6 arms + curva de CPU, mesma sessão, ADR-0027). Ela **refuta a decisão
da ADR-0035** e muda o diagnóstico do problema:

- **O G1 perde sob o envelope real.** Sob `cpus: 0.5` o runtime reporta `Effective CPU Count: 1` ⇒ o
  G1 roda com ~1 thread de GC: paga o overhead do coletor paralelo sem receber o paralelismo.
  **−22,4% de throughput**, +7% de p95, +18% de memória, +8% de imagem. O `perf-regression.sh`
  classifica como REGRESSÃO. Nem dobrar a memória salva (com 512Mi ainda é −7,8% vs Serial com 256Mi).
- **A tese da ADR-0035 estava certa em princípio, e irrelevante na prática.** Com 4 CPUs o G1 de fato
  bate o Serial (+4,8%). O pod é que não tem núcleos.
- **O gargalo real nunca foi medido: `limits.cpu: 500m` custa −69,4%** (2197,5 → 673,5 req/s) e +423%
  de p95. Perseguir ±5% via GC enquanto o cap come 69% é otimizar a coisa errada por uma ordem de
  grandeza. Os −7,1% que motivaram a ADR-0035 foram medidos em **host livre** — uma configuração que
  produção nunca teve.

**Pergunta:** com o envelope de produção medido, que parâmetros de runtime adotar?

## Decision Drivers

- **Medição sob o envelope real** (memória **e** CPU) — herdado da ADR-0035, e foi ele que refutou o G1.
- Custo-benefício honesto: adotar o que a medição sustenta, na ordem do impacto medido.
- Não inchar o repositório nem o build de CI sem contrapartida proporcional.
- Elevar `limits.cpu` **não** consome capacidade de cluster: o scheduler reserva `requests.cpu`
  (100m, inalterado); o *limit* é teto de burst. O custo é contenção no nó, não alocação.

## Considered Options

1. **Manter a ADR-0035** — adotar G1 assim mesmo. Rejeitada: regressão medida de −22,4%.
2. **Reverter para Serial + só right-size** (a Opção 2 da ADR-0035) — segura, mas deixa +16,7% (PGO) e
   +153% (CPU) na mesa.
3. **Adotar o que a medição sustenta: Serial + PGO + `limits.cpu: 1000m` + right-size + Epsilon.
   Sem G1. (escolhida)**

## Decision Outcome

**Opção 3.** Cada item abaixo tem número medido no baseline; nada entra por argumento.

- ❌ **G1 rejeitado.** −22,4% sob `cpus: 0.5` + 256Mi. Produção segue no **Serial GC** (default do
  SubstrateVM). Reavaliar **apenas** se o `limits.cpu` do pod subir a ponto de haver núcleos para
  paralelizar — e então com nova medição.
- ✅ **PGO adotado** (`--pgo`, Oracle GraalVM). **+16,7%** de throughput (673,5 → 785,8 req/s) sob o
  envelope real, com p95 melhor em todos os endpoints, **imagem menor** (324 → 296 MB) e memória menor
  (82,8 → 80,3 MiB). Ganha no eixo oposto ao do G1: reduz CPU por request, e o app é CPU-bound.
- ✅ **`limits.cpu: 500m` → `1000m`.** **+153%** de throughput (785,8 → 1989,8 req/s) e p95 −60%
  (81,7 → 32,9 ms). É o joelho da curva: 0,5→1 é superlinear (remove o throttling do CFS), 1→2 rende
  bem menos (+31%). `requests.cpu` fica em **100m** — sem custo de densidade.
- ✅ **Right-size de memória: `256Mi/512Mi` → `request 128Mi` / `limit 256Mi`.** Pico medido de
  **80–92 MiB** sob carga (estável em qualquer nível de CPU), `oom_kill 0` e `max 0` — a memória
  **nunca encostou** no limite, então o right-size custa **zero** throughput. Folga de ~2,8×.
- ✅ **Binário de migração: `--gc=epsilon`.** Validado em DB virgem sob 256Mi + 0,5 CPU: 2 migrações,
  `exit 0`, sem OOM. ⚠️ Epsilon **nunca libera** ⇒ o pico é função da **alocação total** do Job, não do
  live set. Seguro para as migrações atuais (8 KB de DDL); **um backfill de dados grande pode estourar**.
- ✅ **Métrica de memória removida do HPA.** Escala só por CPU (70%). Num runtime de heap pequeno e não
  devolvido ao SO, utilização de memória não acompanha carga: com `request 128Mi`, 80% = 102Mi contra
  um pico de ~92 MiB — a métrica viraria **latch** de réplicas, não sinal.
- ✅ **Heap: sem `-Xmx` fixo** (mantido da ADR-0035). Sem G1, o knob relevante volta a ser o do Serial
  (`-XX:MaximumHeapSizePercent`, default 80%) — mas a medição não indicou necessidade de fixá-lo.

**Perfil PGO — como é versionado.** O perfil capturado tem **39,6 MB**; o repositório inteiro tem
8,9 MB. Versiona-se **gzipado** (`default.iprof.gz`, 5,1 MB) em `http_api/src/pgo-profiles/main/`, e o
Dockerfile o descomprime antes do `nativeCompile` — o plugin Gradle acha o `*.iprof` pela convenção.
Autocontido, sem Git LFS e sem dobrar o build nativo do CI. **Custo aceito:** cada recaptura soma
~5 MB permanentes ao histórico. Recapturar é raro (só quando o código muda a ponto de o perfil
envelhecer); perfil obsoleto **degrada suavemente** — o native-image ignora métodos que não casam.

### Confirmation

Esta ADR **nasce da medição** que a ADR-0035 exigiu, e o snapshot vem **neste mesmo commit**:
[`docs/quality/performance-baseline-2026-07-native-tuned.md`](../docs/quality/performance-baseline-2026-07-native-tuned.md)
— 8 arms + curva de CPU, mesma sessão, control e treatment sob o mesmo envelope, com o veredito de
cada comparação emitido pelo `scripts/perf-regression.sh` (ADR-0027). Cada ✅/❌ acima cita o número
que o sustenta e é inspecionável/reproduzível lá (seção *How to reproduce*).

Gate contínuo: qualquer mudança futura nestes parâmetros exige **nova medição sob o envelope de
produção** (memória **e** CPU) documentada em `docs/quality/` — medição em host livre não autoriza
decisão de runtime. O workflow k6 (`load-test.yml`, ADR-0027) permanece manual e **nunca** gate de PR.

## Consequences

- **Bom:** +16,7% (PGO) e +153% (CPU) medidos sob o envelope real — juntos, mais que revertem os −7,1%
  da migração para o nativo; imagem 8,6% menor; pods right-sized com 2,8× de folga; o HPA deixa de ter
  uma métrica enganosa; runtime auditável por medição, não por analogia.
- **Ruim (com mitigação):**
  - **+5,1 MB no repo por captura de perfil.** *Mitigação:* gzip (12,7% do original) e recaptura rara.
  - **PGO trava em Oracle GraalVM** — já é a edição em uso.
  - **Captura de perfil é manual** (instrument → workload k6 → optimize), fora do `docker build`, que
    não tem DB nem k6. *Mitigação:* procedimento documentado no skill `/graalvm` §8.
  - **Perfil capturado em arm64, CI builda amd64.** O `.iprof` é baseado em contadores, não em timing.
    *Mitigação:* verificar o log do build por warning de rejeição; se não portar, capturar sob
    `--platform linux/amd64` (perfil só coleta frequências — mas **nunca** medir throughput sob emulação).
  - **`limits.cpu: 1000m` com `requests.cpu: 100m`** amplia a razão limit/request para 10× — mais
    contenção potencial no nó. *Mitigação:* `requests` inalterado ⇒ sem impacto de densidade/scheduling;
    reavaliar `requests` se o HPA (Utilization sobre o request) mostrar-se descolado do uso real.
- **Critério de reavaliação:** se uma medição futura sob o envelope vigente mostrar o G1 vencendo (por
  exemplo após novo aumento de `limits.cpu`), abrir ADR superseding com o baseline que a sustente.

## Related

- **ADR-0035** — superseded por esta. A decisão (G1) foi refutada pela medição que ela mesma exigiu;
  o rigor que ela impôs (medir sob o envelope) é o que produziu esta ADR. Permanece como registro.
- ADR-0032 / ADR-0030 — Native Image em produção (origem dos −7,1% e do artefato).
- ADR-0027 — baseline k6: mecanismo de medição e política de comparabilidade (mesma sessão).
- ADR-0023 — política de ADRs (imutabilidade, supersessão, ADR-first para `[E]`).
- Skills `/graalvm` §8 (PGO, GC, heap) e `/local-and-production-environment` (sizing).
- Board #6 — **GAP-BR** (execução e progresso).
