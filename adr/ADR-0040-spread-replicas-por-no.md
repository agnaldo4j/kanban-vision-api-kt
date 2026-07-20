---
status: accepted
date: 2026-07-20
decision-makers: "@agnaldo4j"
---

# ADR-0040 — Spread de réplicas por nó: disponibilidade sob falha de nó

> O Deployment pede duas réplicas para sobreviver à queda de uma. Mas nada no manifesto diz ao
> scheduler para **separá-las** — então ele pode empilhar as duas no mesmo nó, e a falha desse nó
> derruba as duas de uma vez. Esta ADR decide **como** espalhar, sem quebrar o alvo de
> desenvolvimento single-node.

## Context and Problem Statement

`k8s/03-deployment.yml:10` fixa `replicas: 2` e o HPA sobe de `minReplicas: 2` a `maxReplicas: 8`
(`k8s/06-hpa.yml:13,28`). O Deployment tem `strategy` cuidadosa (`maxUnavailable: 0` / `maxSurge: 1`,
`k8s/03-deployment.yml:14-18`) e há um PodDisruptionBudget `minAvailable: 1` (`k8s/07-pdb.yml:9`).

Mas o pod template **não tem** `topologySpreadConstraints`, `affinity`/`podAntiAffinity` nem
`nodeSelector` — uma varredura confirma **zero** controle de espalhamento em todo o `k8s/`. Sem isso,
o kube-scheduler é livre para colocar **as duas réplicas no mesmo nó**. Consequência:

- **Falha de nó involuntária** (kernel panic, perda de VM, hardware) derruba **todas** as réplicas
  co-localizadas de uma vez. As "2 réplicas" viram **HA ilusória**: dobram o custo sem dobrar a
  sobrevivência.
- **O PDB não cobre isso.** Um PDB só limita disrupção **voluntária** — `kubectl drain`, evictions do
  autoscaler de nós, upgrades. Ele nada faz contra a queda não-anunciada de um nó. `maxUnavailable: 0`
  protege o **rollout**, não o **placement**.

Como na ADR-0037, **não há cluster real neste repositório** — `k8s/01-configmap.yml` aponta para um
`postgres-svc` cujo StatefulSet nem existe. Os manifestos são **referência**. Logo esta ADR decide por
**coerência de um manifesto correto-por-default**, não por medição, e diz isso.

**Pergunta:** como garantir que réplicas não se empilhem num nó, sem quebrar o fluxo local
single-node (Minikube), que é um alvo de primeira classe da skill `/local-and-production-environment`?

## Decision Drivers

- **Correto por default:** o manifesto de referência deve espalhar sozinho, sem exigir um overlay por
  ambiente para ser seguro.
- **Não quebrar o dev single-node.** Em Minikube (um nó), qualquer regra **dura** de separação deixa a
  2ª réplica **`Pending`** para sempre e trava o rolling update (`maxSurge: 1` cria um 3º pod que
  também não agenda).
- **Não introduzir deadlock de drain.** Anti-afinidade **dura** + `minAvailable: 1` pode travar um
  `drain`: o pod não pode ser recriado em outro nó que já hospeda um par, e o PDB proíbe removê-lo.
- **Idioma k8s atual.** `topologySpreadConstraints` é o sucessor recomendado de `podAntiAffinity` para
  espalhamento (mais expressivo, `maxSkew` explícito).

## Considered Options

1. **Status quo (nada).** Rejeitada: mantém a HA ilusória — o defeito que a ADR existe para corrigir.
2. **`podAntiAffinity` `requiredDuringScheduling…` (dura).** Garante 1 pod por nó, mas deixa réplicas
   `Pending` em clusters com menos nós do que réplicas — inclusive **todo** ambiente single-node — e
   arrisca **deadlock de drain** com o PDB. Rejeitada: incompatível com o alvo local e frágil em
   clusters pequenos.
3. **`podAntiAffinity` `preferredDuringScheduling…` (soft).** Funciona e degrada bem, mas é a forma
   **legada**; a doc do k8s recomenda `topologySpreadConstraints` para novos manifestos.
4. **`topologySpreadConstraints` com `whenUnsatisfiable: ScheduleAnyway` (soft).** **(escolhida.)**
   Espalha por nó (e, onde existir, por zona) como **preferência**: em cluster multi-nó o scheduler
   separa; em single-node ele **agenda mesmo assim**, sem estrangular o rollout.

## Decision Outcome

**Opção 4.** Adicionar ao `spec.template.spec` do `k8s/03-deployment.yml` (implementação em PR de
follow-up — esta ADR é o gate `[E]`):

```yaml
# Requer que o pod template do Deployment ganhe o label distintivo
# `app.kubernetes.io/component: api`. O migration Job (k8s/09-migration-job.yml:52-54) compartilha
# `.../name: kanban-vision-api`; sem o `component` distintivo o Job entraria na conta do skew
# (ex.: migration no nó A + 2 réplicas no nó B ⇒ skew 1 "balanceado", anulando a separação).
topologySpreadConstraints:
  # Espalha réplicas entre NÓS — soft (ScheduleAnyway): em prod multi-nó o scheduler separa;
  # em single-node (Minikube) agenda mesmo assim, sem deixar a 2ª réplica Pending (ADR-0040).
  - maxSkew: 1
    topologyKey: kubernetes.io/hostname
    whenUnsatisfiable: ScheduleAnyway
    matchLabelKeys:
      - pod-template-hash   # escopa o skew à revisão do rollout (exclui RS antigo e o Job)
    labelSelector:
      matchLabels:
        app.kubernetes.io/name: kanban-vision-api
        app.kubernetes.io/component: api
  # Espalha entre ZONAS onde o cluster as expõe; também soft, inócuo num cluster sem zonas.
  - maxSkew: 1
    topologyKey: topology.kubernetes.io/zone
    whenUnsatisfiable: ScheduleAnyway
    matchLabelKeys:
      - pod-template-hash
    labelSelector:
      matchLabels:
        app.kubernetes.io/name: kanban-vision-api
        app.kubernetes.io/component: api
```

Decisões fixadas:

- **`whenUnsatisfiable: ScheduleAnyway` (soft), não `DoNotSchedule`.** A separação é uma **preferência
  forte**, não um requisito — é o que reconcilia "espalhar em prod" com "agendar em single-node".
- **`labelSelector` restrito aos pods do Deployment.** O migration Job
  (`k8s/09-migration-job.yml:52-54`) compartilha `app.kubernetes.io/name: kanban-vision-api`, então
  selecionar só por `name` o contaria no skew (`migration` no nó A + 2 réplicas no nó B ⇒ skew 1,
  "balanceado" — anulando a separação). A implementação **adiciona `app.kubernetes.io/component: api`**
  ao pod template do Deployment (o Job já usa `component: migration`) e o constraint seleciona por
  `name` + `component: api`. Adicionar um label ao pod template é seguro: o `.spec.selector` do
  Deployment (imutável) segue por `name`, e o pod continua superconjunto dele.
- **`matchLabelKeys: [pod-template-hash]`** escopa o cálculo à **revisão do rollout** — num rolling
  update não mistura pods do ReplicaSet antigo e novo (e, de quebra, exclui o Job, que não tem esse
  label). Requer k8s ≥1.27 (beta ligado por default; estável 1.30); onde indisponível, o `component: api`
  sozinho já exclui o Job.
- **Dois topologyKeys:** `kubernetes.io/hostname` (o que resolve o defeito) e
  `topology.kubernetes.io/zone` (defesa em profundidade onde há zonas; sem custo onde não há).
- **PDB permanece inalterado.** Spread (involuntário) e PDB (voluntário) são **ortogonais** e
  complementares. HPA e `strategy` também ficam como estão.

## Consequences

- **Bom:** num cluster multi-nó com nós viáveis, o scheduler passa a **separar** as réplicas, de modo
  que a queda de um nó deixa de ser, **na prática**, um ponto único — as 2 réplicas passam a entregar a
  disponibilidade que o custo delas já paga, e o spread alinha-se ao PDB. É uma **melhora forte da
  postura por default** (não uma garantia — ver o trade-off abaixo), sem exigir overlay.
- **Trade-off (aceito) — soft NÃO garante separação.** Sendo `ScheduleAnyway`, se não houver nó viável
  o scheduler co-localiza assim mesmo, e aí a falha de um nó ainda derruba todas as réplicas. "Sem nó
  viável" não é só "nós de menos": inclui nós com `taint`, sem capacidade, ou que percam no scoring do
  scheduler. Logo o manifesto **reduz drasticamente** a probabilidade do ponto único, mas **não a
  elimina** — operadores não devem tratar isto como HA garantida. É o preço de não quebrar o dev
  single-node nem arriscar deadlock de drain; uma garantia dura é **incompatível** com os alvos deste
  repo. Quem operar um cluster grande e quiser a garantia pode endurecer para `DoNotSchedule` **via
  overlay** de ambiente — nunca no manifesto base.
- **Custo zero em Minikube:** `ScheduleAnyway` não altera o comportamento single-node.
- **Implementação:** ~10 linhas em `k8s/03-deployment.yml` (PR de follow-up). Sem código de app, sem
  impacto em gates de qualidade. Validar com `kustomize build k8s/` (+ `kubeconform` se disponível).

**Critério de reavaliação:** se o repo passar a definir um cluster real com ≥3 nós e SLA que exija
garantia dura de spread, reavaliar `DoNotSchedule` (base ou overlay), medindo a interação com o PDB
num `drain`.

## Related

- **GAP-BY** (board #6) — origem; execução do patch de manifesto é o follow-up desta ADR.
- **ADR-0037** — envelope de recursos do pod; fonte do framing "manifestos são referência" e da
  relação réplicas × HPA × invariante de conexões.
- **ADR-0023** — política de ADRs (imutabilidade, supersessão).
- Doc k8s — [Pod Topology Spread Constraints](https://kubernetes.io/docs/concepts/scheduling-eviction/topology-spread-constraints/).
