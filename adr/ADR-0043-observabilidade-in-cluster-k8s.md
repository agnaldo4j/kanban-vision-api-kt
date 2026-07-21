---
status: accepted
date: 2026-07-21
decision-makers: "@agnaldo4j"
---

# ADR-0043 — Stack de observabilidade in-cluster (k8s): honrar as anotações de scrape

> O pod template anota-se para ser coletado (`prometheus.io/scrape: "true"`), mas nada em `k8s/`
> **coleta**. As métricas, regras de alerta e dashboards existem apenas no stack docker-compose. Esta
> ADR decide **como** entregar essa observabilidade dentro do cluster, sem introduzir uma dependência
> que quebre o alvo local single-node.

## Context and Problem Statement

O `k8s/03-deployment.yml:29-32` anota cada pod para coleta Prometheus:

```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "8080"
  prometheus.io/path: "/metrics"
```

Essas anotações só têm efeito para um **Prometheus in-cluster fazendo Kubernetes service discovery** —
que **não existe** no repositório. Uma varredura confirma: `k8s/kustomization.yaml` não inclui **nenhum**
recurso de monitoração (a numeração pula 07 → 09); não há `Deployment`/`ConfigMap` de Prometheus,
Alertmanager ou Grafana, nem `ServiceMonitor`/`PrometheusRule`/CRD de Operator em lugar algum de `k8s/`.

Todo o stack de observabilidade — `observability/prometheus.yml`, `prometheus-alerts.yml`,
`alertmanager.yml`, `tempo.yml`, `grafana/**` — está montado **exclusivamente** via `docker-compose.yml`
(serviços `prometheus`/`alertmanager`/`grafana`/`cadvisor`/`tempo`). É **local-only**. O próprio repo já
documenta o buraco:

- `k8s/01-configmap.yml:31-41` desliga traços de propósito — `OTEL_*_EXPORTER: "none"` "because this repo
  does not deploy a Tempo/OTLP collector Service".
- `observability/alertmanager.yml` (cabeçalho): *"O Alertmanager in-cluster (k8s) é o **GAP-CB [E]** — não
  há Prometheus in-cluster ainda, então manifesto k8s aqui seria config morta."*
- `observability/prometheus.yml:20-23` já antecipa que a coleta de produção descobre pods **pelas
  anotações** e põe `instance=podIP:port`, ao contrário do alvo estático `app:8080` do compose.

Consequência: as anotações são uma **promessa sem coletor**. O manifesto de referência afirma "sou
observável no cluster" e não é.

Como na ADR-0037 e na ADR-0040, **não há cluster real neste repositório** — `k8s/01-configmap.yml` aponta
para um `postgres-svc`/`redis-svc` que nem existem. Os manifestos são **referência**. Logo esta ADR decide
por **coerência de um manifesto correto-por-default**, não por medição, e diz isso.

**Pergunta:** como fazer as anotações de scrape valerem no cluster — com alertas e dashboards — sem exigir
instalar um Operator, quebrando o fluxo local single-node (Minikube), alvo de primeira classe da skill
`/local-and-production-environment`?

## Decision Drivers

- **Correto por default:** o manifesto de referência deve ser auto-suficiente — como o compose já é —, não
  depender de infra externa pré-instalada para cumprir o que anota.
- **Rodar em Minikube single-node sem instalar nada.** Um Operator (CRDs) exige um passo de instalação
  fora do repo; num cluster novo/local as CRDs não existem e o `kubectl apply` falha.
- **Paridade com o compose (uma fonte de config).** As regras de alerta, o roteamento do Alertmanager e os
  dashboards já existem e são exercitados pelo job `config-lint` (GAP-CY); reusá-los evita drift entre
  local e cluster.
- **Sem dependência externa dura.** Nada que o repo não controle deve ser pré-requisito de um `apply`.
- **Incremental / PR-able.** O follow-up de implementação precisa caber no limite de PR (≤400 linhas) —
  possivelmente dividido por componente.

## Considered Options

1. **Status quo (anotações mortas).** Rejeitada: mantém a promessa quebrada que a ADR existe para corrigir.
2. **Prometheus Operator (`ServiceMonitor` + `PrometheusRule` + CRs de Grafana).** É o idioma padrão da
   indústria, mas **pressupõe o kube-prometheus-stack/Operator instalado** — uma dependência externa dura
   que o repo não controla e que **não existe num Minikube nu**. `kubectl apply` de um `ServiceMonitor` sem
   a CRD falha. Rejeitada: quebra o alvo local-first e troca "config morta" por "manifesto que nem aplica".
3. **Contract-only / BYO monitoração.** Declarar que a monitoração de cluster é *bring-your-own* e que a
   responsabilidade do repo termina em `/metrics` + anotações + regras portáveis. Rejeitada: deixa o stack
   **não implantado** no cluster — o oposto do que o gap pede. O compose entrega o stack inteiro; o k8s de
   referência deveria também.
4. **Stack self-contained: `Deployment`s planos + configs montadas de `ConfigMap` + `kubernetes_sd_configs`.**
   **(escolhida.)** Prometheus, Alertmanager e Grafana como `Deployment`s comuns, com as configs vindas de
   `ConfigMap`s que **reusam** os `observability/*.yml`. O Prometheus descobre alvos por
   `kubernetes_sd_configs` honrando as anotações `prometheus.io/*`. **Sem CRD/Operator** — aplica num
   Minikube nu e espelha o compose.

## Decision Outcome

**Opção 4.** A implementação (PR de follow-up — esta ADR é o gate `[E]`) adiciona a `k8s/`, e ao
`kustomization.yaml`, um stack de observabilidade self-contained:

- **`k8s/10-prometheus.yml`** — `Deployment` + `Service` de Prometheus, mais `ServiceAccount` e RBAC
  (`ClusterRole`/`ClusterRoleBinding`). O `ClusterRole` precisa de **dois** níveis, não um: leitura de
  `pods`/`endpoints`/`nodes` para a **descoberta** (service discovery), **e** o subrecurso `nodes/proxy`
  para **autorizar o scrape** das métricas do kubelet/cAdvisor (ler o objeto `nodes` só descobre alvos, não
  autoriza a chamada `/metrics/cadvisor`). O scrape do kubelet carrega
  `bearer_token_file: /var/run/secrets/kubernetes.io/serviceaccount/token` + `tls_config`
  (`ca_file` do serviceaccount; `insecure_skip_verify` só no perfil Minikube). Sem isso, um cluster com
  RBAC padrão devolve **403** e as 3 regras de container ficam sem dado, silenciosamente.
- **`k8s/11-prometheus-config.yml`** — `ConfigMap` com o scrape config **reescrito** dos alvos **estáticos**
  do compose (`app:8080`, `cadvisor:8080`) para `kubernetes_sd_configs`: (a) **pod SD** honrando as anotações
  `prometheus.io/scrape|port|path` do pod da API (mantendo `instance=podIP:port`, a semântica de produção
  que o compose não reproduz); (b) **node SD** para o cAdvisor do kubelet via o caminho de proxy do
  apiserver (`.../nodes/<node>/proxy/metrics/cadvisor`). Reusa as regras de negócio/HTTP/pool de
  `observability/prometheus-alerts.yml` **verbatim**; as 3 regras de **container**
  (`ContainerCpuThrottling`/`ContainerMemoryNearLimit`/`ContainerOomKilled`) são a **variante k8s escopada
  a `namespace="kanban-vision"`** — ver a decisão fixada abaixo. O bloco `alerting:` aponta para o `Service`
  do Alertmanager in-cluster.
- **`k8s/12-alertmanager.yml`** — `Deployment` + `Service` de Alertmanager, config de um `ConfigMap` que
  reusa `observability/alertmanager.yml`, **mais um `Deployment` + `Service` `alert-sink`** in-cluster (o
  equivalente k8s do echo-sink que hoje só existe no compose). Sem ele, os 3 receivers
  (`default`/`critical`/`warning`) reusados apontam para `http://alert-sink:8080`, um nome que **não
  resolve** no cluster — e toda notificação falharia por DNS, recriando o "para o vazio" que esta ADR existe
  para fechar. O `Service` `alert-sink` no namespace `kanban-vision` faz os URLs reusados resolverem e dá
  **prova ponta-a-ponta** de que o alerta chega ao receiver; ambientes reais **repontam** os receivers para
  um notificador de verdade (Slack/PagerDuty/e-mail) **via overlay**, com segredos em `Secret` do k8s.
- **`k8s/13-grafana.yml`** — `Deployment` + `Service` de Grafana, com datasource e dashboard provisionados de
  `ConfigMap`s que reusam `observability/grafana/**`.

Decisões fixadas:

- **Sem Operator/CRD.** É o que reconcilia "entregar o stack no cluster" com "aplicar num Minikube nu". Quem
  operar um cluster com kube-prometheus-stack pode migrar para `ServiceMonitor`/`PrometheusRule` **via
  overlay** — nunca no manifesto base.
- **Reuso de config, não cópia divergente — com um delta k8s explícito.** Roteamento do Alertmanager,
  dashboards e as regras de negócio/HTTP/pool vêm dos `observability/*.yml` verbatim; o cluster e o compose
  compartilham a fonte, e o `config-lint` (GAP-CY) segue sendo o guarda. **Exceção decidida aqui:** as 3
  regras de container e a topologia de receiver **divergem** entre compose e k8s (ver os dois bullets
  abaixo) — não são reusadas cegamente.
- **Receiver alcançável no cluster (não só no compose).** O stack **inclui** um `alert-sink` in-cluster
  (bullet `k8s/12` acima) para que os receivers reusados resolvam e a entrega de alerta tenha prova
  ponta-a-ponta; a produção repontam para um notificador real via overlay. Isso fecha a lacuna de o
  Alertmanager "aceitar e não entregar".
- **cAdvisor não é um `Deployment` — e suas regras ganham escopo.** Em k8s, métricas de container vêm dos
  endpoints cAdvisor do **kubelet** (via node SD) — diferente do compose, que sobe um cAdvisor dedicado a
  este stack. Nenhum `Deployment` de cAdvisor é enviado. **Consequência decidida:** o kubelet expõe **todos**
  os containers do nó (todos os namespaces), então o seletor `{name!=""}` das regras de container do compose
  paginaria por workloads de terceiros. As regras de container in-cluster são portanto **escopadas a
  `namespace="kanban-vision"`** (label injetado pelo relabel do node/pod SD), não reusadas verbatim — é o
  que preserva o driver "correto por default".
- **Traços ficam FORA de escopo (fronteira explícita).** Esta ADR entrega **métricas + alertas + dashboards**.
  `OTEL_*_EXPORTER` permanece `none` até um coletor (Tempo/OTLP) ser implantado — isso é um card futuro, não
  uma omissão. `k8s/01-configmap.yml` já descreve o patch de overlay que liga traços quando houver coletor.
- **Observabilidade não é HA.** Uma réplica por componente e armazenamento efêmero (`emptyDir`) são
  aceitáveis para um manifesto de referência; PVC/HA de monitoração, se necessários, são card futuro.

## Consequences

- **Bom:** as anotações de scrape passam a ter um coletor — a promessa do manifesto vira verdade; as 9
  regras de `prometheus-alerts.yml` podem disparar in-cluster **e alcançar um receiver** (não mais "para o
  vazio"); a semântica de label (`instance=podIP:port`) passa a ser a de produção, encerrando o descasamento
  GAP-BW/compose; paridade com o compose por uma fonte de config; **zero dependência externa** — aplica num
  Minikube nu.
- **Trade-off (aceito):** mais manifestos para manter; monitoração não-HA (réplica única, storage efêmero);
  reescrever o scrape estático→SD é onde mora o risco de erro (o coletor precisa casar exatamente as
  anotações que `03-deployment.yml` emite, e o scrape do kubelet exige o RBAC `nodes/proxy` + auth acima).
  O `alert-sink` in-cluster é um receiver de **prova**, não um notificador de produção — ambientes reais
  repontam via overlay. Continua sendo **manifesto de referência** — sem cluster real para medir.
- **Implementação:** vários manifestos novos (`k8s/10..13`) + entradas no `kustomization.yaml`. **Sem código
  de app, sem impacto em gates de qualidade.** Provável ultrapassagem do limite de PR (≤400 linhas) ⇒
  **dividir** o follow-up (ex.: Prometheus+SD+RBAC → Alertmanager → Grafana), cada PR verde.

## Confirmation

Como o CI **não tem cluster real**, a confirmação é por **coerência**, no espírito da ADR-0040 e do job
`config-lint` (GAP-CY):

- O PR de implementação valida com `kubectl kustomize k8s/` (build limpo; + `kubeconform` se disponível).
- O `kubernetes_sd_configs` do `ConfigMap` de scrape deve **chavear pelas mesmas** anotações
  `prometheus.io/scrape|port|path` que o `k8s/03-deployment.yml` emite — verificação de coerência anotação↔SD.
- **Receiver alcançável:** todo `url`/target dos receivers no `ConfigMap` do Alertmanager deve resolver para
  um `Service` presente no próprio `kustomization.yaml` (o `alert-sink` in-cluster) — coerência receiver↔Service,
  para o "para o vazio" não voltar por baixo.
- **RBAC do kubelet:** o `ClusterRole` deve conter `nodes/proxy` (ou `nodes/metrics`) além de `nodes`, e o
  job de scrape do cAdvisor deve declarar `bearer_token_file` + `tls_config` — sem isso o scrape do kubelet é
  403 num cluster com RBAC padrão.
- **Escopo das regras de container:** as regras `Container*` in-cluster devem carregar o filtro
  `namespace="kanban-vision"` (não o `{name!=""}` do compose) — coerência de não-paging cross-tenant.
- **Follow-up de gate (opcional):** estender o `config-lint` (`promtool check config` / `check rules`) para
  também lintar o `ConfigMap` de scrape + regras k8s, do mesmo modo que já linta `observability/*.yml`.

**Critério de reavaliação:** se o repo passar a assumir um cluster com kube-prometheus-stack como baseline,
reavaliar a migração para `ServiceMonitor`/`PrometheusRule` (base ou overlay).

## Related

- **GAP-CB** (board #6) — origem; a implementação dos manifestos é o follow-up desta ADR.
- **ADR-0031** — observabilidade sem javaagent (traces via OTel SDK); fonte da fronteira "traços fora de
  escopo até haver coletor".
- **ADR-0037 / ADR-0040** — manifestos k8s de referência; fonte do framing "não há cluster real → decidir por
  coerência".
- **GAP-CA / GAP-CY** — Alertmanager e `config-lint` no compose; esta ADR leva o mesmo stack ao cluster.
- **ADR-0023** — política de ADRs (imutabilidade, supersessão).
- Doc k8s — [Prometheus Kubernetes SD](https://prometheus.io/docs/prometheus/latest/configuration/configuration/#kubernetes_sd_config).
