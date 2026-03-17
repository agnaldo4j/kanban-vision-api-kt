# ADR-0007 — Métricas de Aplicação com Micrometer e Prometheus

**Data:** 2026-03-16
**Status:** Aceita
**Gap:** GAP-D (Ciclo Operações — P2)
**Autores:** Equipe de Engenharia

---

## Contexto

O projeto não possui endpoint de métricas. Sem métricas, não é possível:
- Criar dashboards de latência, throughput e taxa de erro no Grafana
- Configurar alertas automáticos (GAP-U)
- Monitorar o estado do pool HikariCP e da JVM em produção

**Problema identificado no ADR-0004 (GAP-D):** ausência completa de instrumentação — zero visibilidade sobre o comportamento da aplicação em produção.

---

## Decisão

Adicionar instrumentação via **Micrometer** com **Prometheus** como registry, expondo um endpoint `/metrics` (texto Prometheus) para scraping externo.

### Componentes

| Componente | Responsabilidade |
|---|---|
| `ktor-server-metrics-micrometer` | Plugin Ktor que auto-instrumenta todas as rotas HTTP (latência, throughput, erros) via `CallMeasure` |
| `micrometer-registry-prometheus` | Registry que serializa métricas no formato de texto do Prometheus |
| `PrometheusMeterRegistry` | Singleton Koin compartilhado entre `configureMetrics()` e `DomainMetrics` |
| `DomainMetrics` | Wrapper de métricas de negócio — isola o código de domínio das APIs do Micrometer |
| `GET /metrics` | Endpoint fora do bloco `authenticate("jwt-auth")` — acessível pelo Prometheus sem token |

### Métricas automáticas (sem código adicional)

O plugin `MicrometerMetrics` do Ktor instrui automaticamente:
- `ktor.http.server.requests` — histograma de latência por rota, método e status code
- Métricas de JVM: heap, GC, threads, class loading
- Métricas de HikariCP (via Micrometer auto-detection): pool size, connections ativas, tempo de espera

### Métricas de domínio (`DomainMetrics`)

| Métrica | Tipo | Descrição |
|---|---|---|
| `kanban.simulation.days.executed` | Counter | Incrementado após cada `RunDayUseCase` bem-sucedido |

**Labels**: todas as métricas de domínio usam **baixa cardinalidade** — nunca `scenarioId`, `requestId` ou outros UUIDs como label. Isso evita explosão de cardinalidade no TSDB do Prometheus.

### Posicionamento do `/metrics`

O endpoint `/metrics` é registrado em `configureMetrics()`, **fora** do bloco `authenticate("jwt-auth")` de `configureRouting()`. Prometheus scraping é feito de dentro da rede (cluster Kubernetes) e não requer autenticação JWT.

Em produção com Kubernetes, o acesso externo ao `/metrics` deve ser bloqueado via `NetworkPolicy` — apenas o namespace do Prometheus tem acesso.

---

## Consequências

### Positivas
- Latência HTTP disponível imediatamente (P50, P95, P99 por rota)
- Pool HikariCP visível via Micrometer auto-detection
- JVM metrics (heap, GC) sem código adicional
- `DomainMetrics` é extensível: novos counters/gauges adicionados sem tocar em rotas existentes
- Preparação para GAP-U (alertas Grafana) e GAP-O (OTel traces correlacionados com métricas)

### Negativas / trade-offs
- Duas novas dependências (~5 MB total: Micrometer + Prometheus client)
- Endpoint `/metrics` sem autenticação — deve ser protegido por `NetworkPolicy` em produção (ver GAP-G)
- 12 test modules atualizados para incluir `mockk<DomainMetrics>(relaxed = true)` — churn necessário

---

## Alternativas Consideradas

| Alternativa | Motivo de rejeição |
|---|---|
| Expor `/metrics` dentro do `authenticate("jwt-auth")` | Prometheus não envia JWT — inviável sem configuração extra de service account |
| Usar OpenMetrics (`micrometer-registry-openmetrics`) | Menos suportado no ecossistema Prometheus/Grafana atual; Prometheus scraper suporta ambos, mas text format é mais estável |
| Implementar métricas manualmente sem Micrometer | Reinventa abstrações que Micrometer já resolve (histogramas, percentis, tags) |
| Adicionar gauge para WIP atual (`kanban.scenario.wip.current`) | Requer query ao repositório ou cache em memória — escopo de GAP-U; adiado |

---

## Validação

- `./gradlew testAll` verde
- `GET /metrics` retorna 200 com corpo não-vazio em formato Prometheus
- `DomainMetricsTest` valida incremento do counter com `SimpleMeterRegistry`

---

## Próximo passo relacionado

- **GAP-U**: configurar alertas no Grafana usando as métricas expostas aqui
- **GAP-G**: containerização com `docker-compose` incluindo Prometheus + Grafana locais para validar o stack completo

---

## Referências

- [Micrometer Ktor integration](https://ktor.io/docs/server-metrics-micrometer.html)
- [micrometer-registry-prometheus](https://micrometer.io/docs/registry/prometheus)
- ADR-0004 — GAP-D: "Sem métricas de aplicação"
- ADR-0004 — GAP-U: alertas dependentes das métricas aqui definidas
