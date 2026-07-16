---
status: accepted
date: 2026-07-16
decision-makers: "@agnaldo4j"
supersedes: ADR-0036
---

# ADR-0037 — Envelope de recursos do pod: `requests` que descrevem a realidade

> Supersede a **ADR-0036** para mudar **um** parâmetro que ela deixou intocado — `requests.cpu` — e
> que governa duas coisas ao mesmo tempo: como o scheduler empacota os pods e o que o HPA mede. Todas
> as demais decisões da 0036 são **confirmadas por medição** e reafirmadas aqui. Evidência:
> [`performance-baseline-2026-07-envelope-medido.md`](../docs/quality/performance-baseline-2026-07-envelope-medido.md).

## Context and Problem Statement

A ADR-0036 elevou `limits.cpu` para `1000m` por medição e **manteve `requests.cpu: 100m`**,
argumentando que *"o limit é teto de burst, não reserva ⇒ não consome capacidade"*. O argumento está
certo sobre **custo** e ignora duas consequências:

- **O scheduler acredita no request.** `requests.cpu: 100m` declara que o pod precisa de 0,1 core ⇒ o
  k8s empacota ~10 pods por core. Sob carga cada um quer 1000m: todos se estrangulam mutuamente. A
  razão limit/request de **10×** não é neutra — é uma promessa de burst que o nó não pode honrar.
- **O HPA mede contra o request.** `Utilization: 70%` de 100m = gatilho de **70m**, contra um teto de
  1000m. Sob qualquer carga real o pod passa de 70m e o HPA vai a `maxReplicas`. Ele deixa de medir
  saturação e passa a medir "está servindo tráfego". Sem bloco `behavior` (não existe hoje), os
  defaults dobram réplicas a cada 15s, sem janela de estabilização.

**Isto já era dívida conhecida e registrada três vezes** — ADR-0035 (*"CPU do pod… exige ADR própria"*),
ADR-0036 (Consequences) e o skill `/local-and-production-environment` — e nunca virou ADR.

Duas agravantes descobertas ao medir:

- **A mitigação da ADR-0036 é auto-refutável.** Ela condiciona a revisão a *"se o HPA mostrar-se
  descolado do uso real"* — mas **nada neste repo pode mostrar isso**: não há cAdvisor,
  kube-state-metrics nem alerta de HPA/réplicas/throttle. A condição de quitação não pode disparar.
- **Nenhum alerta enxerga a falha.** `HighHttpLatencyP95` dispara em **2s**; o p95 medido é **31 ms**
  (~60× de folga). HPA em thrash não gera 5xx nem latência — queima capacidade **em silêncio**.

**Pergunta:** que `requests` descrevem honestamente este pod, e o que isso implica para o HPA?

## Decision Drivers

- **`requests` são uma declaração factual ao scheduler**, não um botão de custo. Se divergem do uso
  real, tanto o empacotamento quanto o autoscaling ficam errados — mesmo que o custo pareça menor.
- **Medir sob o envelope real** (memória **e** CPU) — regra herdada da 0036, e foi ela que refutou
  três hipóteses desta sessão.
- **Não há cluster real.** Os manifestos são **referência**: `k8s/01-configmap.yml:8` aponta para um
  `postgres-svc` cujo StatefulSet nem existe no repo. Logo esta ADR decide por **coerência do
  manifesto**, não por custo — e diz isso, em vez de simular um cálculo de dinheiro que ninguém pode
  fazer.

## Considered Options

1. **Manter `requests.cpu: 100m`** e corrigir só o HPA (`AverageValue` em vez de `Utilization`).
   Conserta o sinal de escala e deixa o scheduler cego — a oversubscription de 10× permanece.
2. **Subir `requests.cpu` para 500m** (razão 2:1). Conserta scheduler **e** HPA de uma vez. **(escolhida)**
3. Status quo. Rejeitada: a dívida já foi registrada três vezes sem ser paga.

## Decision Outcome

**Opção 2.** Cada item abaixo cita o número que o sustenta; nada entra por argumento — o padrão que a
ADR-0036 fixou (*"nada entra por argumento"*) e violou justamente em `requests.cpu`.

### Muda

- ✅ **`requests.cpu` 100m → 500m** (razão **2:1** com `limits.cpu: 1000m`). O scheduler passa a
  empacotar honesto, e `Utilization: 70%` volta a significar algo: **350m**, contra um teto de 1000m.
  Sem telemetria de produção, 2:1 é **convenção ancorada em medição**, não precisão fingida.
- ✅ **HPA ganha `behavior`** com `stabilizationWindowSeconds` (o skill
  `/local-and-production-environment` já traz o exemplo, nunca aplicado). Sem ele os defaults levam
  2→4→8→10 réplicas em ~45s.
- ✅ **Invariante de conexões: `maxReplicas × poolSize ≤ max_connections`**, com folga para o Job de
  migração (+2 conexões) e manutenção. O pool é **fixo em 10** por pod
  (`http_api/src/main/resources/application.conf:34`; `minimumIdle` não setado ⇒ = max; **é o único
  valor do arquivo sem `${?ENV}`** — mudar exige rebuild). Hoje `10 × 10 = 100` e o **default** do
  PostgreSQL é 100 ⇒ **zero margem**: o HPA pode escalar até esgotar o banco. E o alerta
  `HikariPoolExhaustion` mede `active/max` **por instância** ⇒ 10 pods a 30% cada não disparam nada
  enquanto o banco satura. ⚠️ **O `max_connections` de produção é inverificável deste repo** (não há
  Postgres nos manifestos), então esta ADR fixa **a regra e a responsabilidade** — quem provisionar o
  banco tem de satisfazer o invariante — e **não** finge conhecer o número. Que a conta nunca tenha
  sido feita é sintoma de o banco nunca ter estado no escopo dos manifestos.

### Confirma por medição (herdado da 0036, agora com número)

- ✅ **`limits.cpu: 1000m`** — o joelho é **real**. `Effective CPU Count` por quota: 0.5→1 · 1.0→1 ·
  **2.0→2** · 4.0→4; a 2 CPUs o Netty já dimensionava o event loop para 2, logo o +31% do 1→2 foi
  medido **com** as threads. Não é artefato.
- ✅ **Threads: nada a fazer.** A 1 CPU o Netty é 1-threaded, mas `-XX:ActiveProcessorCount=2` deixa o
  throughput **flat** (−0,49%, ruído) e **piora o p95** (`create` +35,8% → regressão). **O limite é a
  quota.** A flag **não** é adotada.
- ✅ **`requests.memory: 128Mi`** — working set medido: **p50 55 MiB, máx 92 MiB** (115 amostras sob
  carga). O request cobre 1,4× o pico. Memória é **incompressível**: pods acima do request são
  despejados primeiro (QoS Burstable), então o request tem de cobrir o uso real.
- ✅ **`limits.memory: 256Mi`** — **2,8×** sobre o working set de pico, `oom_kill 0`, `max 0`.
- ✅ **Serial GC + PGO**, **epsilon** na migração, **HPA sem métrica de memória** — inalterados.

### Refunda o racional do G1 (decisão inalterada)

A ADR-0036 rejeita o G1 citando **−22,4%** — número do envelope de **0,5 CPU**, que ela mesma aboliu
no mesmo documento. **A 1,0 CPU o G1 é +3,13%, sem regressão.** O motivo real, medido: os +3,13% de
throughput custam **p95 pior**, **+13% de memória** (216 vs 191 MiB) e **+8% de imagem** — e o G1
encosta em **84% do limite de 256Mi** contra 59% do artefato entregue. Trocar 3% de throughput por
25pp de margem de OOM é mau negócio. **O G1 segue rejeitado; agora pelo motivo certo.**

### Corrige um erro deste repositório

`performance-baseline-2026-07-native-tuned-1cpu.md` afirma que *"a folga de memória real é ~1,7×, não
~2,8×"*. **A afirmação está errada**: dividiu 256Mi pelo `memory.peak` (152 MiB), que inclui **page
cache reclaimável** e não governa eviction. Pelo working set — a métrica correta — a folga é **2,8×**.
**A ADR-0036 estava certa.** Prova do ruído: dois runs da mesma config deram peak de **93** e **152**
MiB, com working set estável em ~92. **`memory.peak` prova que o limite nunca foi encostado; o working
set dimensiona o `request`** — trocá-los produz conclusões opostas dos mesmos bytes.

### Confirmation

Toda decisão acima cita
[`performance-baseline-2026-07-envelope-medido.md`](../docs/quality/performance-baseline-2026-07-envelope-medido.md),
**neste mesmo commit** (a review do #284 rejeitou, com razão, uma ADR que citava baseline ausente da
árvore).

Gate contínuo: mudança nestes parâmetros exige **nova medição sob o envelope de produção** (memória
**e** CPU, via `docker-compose.limits.yml`) documentada em `docs/quality/`. **`docker-compose.limits.yml`
espelha `k8s/03-deployment.yml` — no mesmo PR**: foi a divergência entre os dois (overlay em `cpus: 0.5`
enquanto o manifesto ia a 1000m) que quase perpetuou a medição no envelope errado.

## Consequences

- **Bom:** o scheduler passa a empacotar com base num número verdadeiro; o HPA volta a medir saturação
  em vez de presença de tráfego; o teto de réplicas ganha um invariante em vez de ser herdado; e o
  runtime fica documentado por medição — inclusive **três hipóteses refutadas**, para que ninguém
  repita as investigações.
- **Ruim (com mitigação):**
  - **`requests.cpu: 500m` reserva 5× mais** que hoje: 2 réplicas = 1 core de linha de base.
    *Mitigação:* é o preço de um scheduler que funciona; sem cluster real, o custo é nominal.
  - **O invariante de conexões não pode ser verificado deste repo.** *Mitigação:* a ADR fixa a regra e
    aponta a responsabilidade; o card de execução deriva o `maxReplicas` do `max_connections` real.
  - **Terceira ADR da série em um dia** (0035 → 0036 → 0037). *Não é churn:* cada uma foi derrubada
    por uma medição que a anterior **exigiu**. A 0035 mandou medir sob o cap de CPU e isso a refutou;
    a 0036 mandou medir sob o envelope e isso refutou parte do racional dela. É o gate funcionando.
- **Critério de reavaliação:** reavaliar o **limite de memória antes** de qualquer novo aumento de
  `limits.cpu` — memória escala com throughput (a 0,5 CPU o working set era ~80 MiB; a 1,0 CPU é 92).

## Related

- **ADR-0036** — superseded por esta. Estava **certa** sobre memória (2,8×) e sobre o joelho de CPU; o
  que muda é `requests.cpu` e o racional do G1.
- ADR-0035 — pediu esta ADR ("CPU do pod exige ADR própria") e nunca foi atendida até aqui.
- ADR-0027 — baseline k6: mecanismo de medição e comparabilidade (mesma sessão).
- ADR-0023 — política de ADRs (imutabilidade, supersessão).
- Board #6 — **GAP-BU** (execução: `k8s/`, overlay e skills).
