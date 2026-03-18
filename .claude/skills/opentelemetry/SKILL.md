argument-hint: "[observability concern, e.g.: traces, metrics, logs (optional)]"
allowed-tools: Read, Grep, Glob, Bash
---
name: opentelemetry
description: >
  Implemente e evolua a observabilidade deste projeto com OpenTelemetry, Prometheus e
  Grafana. Use este skill ao adicionar métricas, traces, logs estruturados, health checks
  com dependências, ou ao configurar o stack local de observabilidade. Cobre o caminho
  incremental desde o estado atual (MDC + logs de texto) até observabilidade completa
  (logs JSON → métricas Prometheus → traces distribuídos → alertas Grafana).
---

# OpenTelemetry, Prometheus & Grafana

> *"Observability is not about collecting data — it's about being able to ask new questions
> of your system without deploying new code."*
> — Charity Majors

> *"OpenTelemetry is a collection of APIs, SDKs, and tools. Use it to instrument, generate,
> collect, and export telemetry data (metrics, logs, and traces) to help you analyze your
> software's performance and behavior."*
> — opentelemetry.io

---

## Estado Atual do Projeto vs. Objetivo

### Hoje

```
Observability.kt
  ├── RequestIdPlugin  → gera/propaga X-Request-ID
  └── CallLogging      → loga método, path, status com MDC (requestId)

logback.xml
  └── Appender STDOUT  → texto: "%d{HH:mm:ss} [%thread] %-5level [rid=%X{requestId}] - %msg%n"
```

**O que falta:**
- Logs em JSON estruturado (GAP-F) — texto livre impossibilita agregação em Loki/CloudWatch
- Endpoint `/metrics` com métricas de aplicação (GAP-D)
- Distributed tracing com `trace_id` / `span_id` correlacionados nos logs (GAP-O)
- Health check com verificação real de dependências (GAP-B)

### Objetivo: Observabilidade em Quatro Camadas

```
┌──────────────────────────────────────────────────────────┐
│  Camada 4 — Alertas (Grafana Alerting / Alertmanager)    │
├──────────────────────────────────────────────────────────┤
│  Camada 3 — Traces (OpenTelemetry → Grafana Tempo)       │
├──────────────────────────────────────────────────────────┤
│  Camada 2 — Métricas (Micrometer → Prometheus → Grafana) │
├──────────────────────────────────────────────────────────┤
│  Camada 1 — Logs JSON (Logback → Grafana Loki)           │
└──────────────────────────────────────────────────────────┘
```

**Abordagem incremental:** cada camada é independente e entrega valor imediato.
Implemente nessa ordem; não salte etapas.

---

## I. Os Três Pilares de Observabilidade

### Logs

Registros discretos de eventos. O pilar mais maduro do projeto — já existe com
`requestId` no MDC. A evolução é trocar o formato de texto por JSON estruturado.

**Quando usar:** debug de erros específicos, auditoria de operações, contexto de negócio
que não cabe em métricas.

### Métricas

Medidas numéricas agregadas ao longo do tempo. Permitem dashboards, SLOs e alertas.
São o sinal mais barato operacionalmente (série temporal compacta).

**Quando usar:** monitoramento contínuo de latência, throughput, taxas de erro, uso de recursos.

### Traces

Rastreamento do caminho completo de uma requisição através de múltiplos componentes.
Cada span registra uma operação com duração, atributos e erros.

**Quando usar:** diagnóstico de latência, identificar qual serviço/operação está lento,
correlacionar comportamento entre componentes distribuídos.

### Baggage

Metadados propagados junto com o contexto de trace entre serviços (ex: `tenantId`,
`userId`). Diferente de atributos de span — baggage flui automaticamente sem ser
repassado manualmente.

---

## II. Tipos de Métricas Prometheus

Escolher o tipo correto é crítico para a semântica e a queryabilidade.

### Counter

Valor que **só aumenta** (ou reinicia em zero no restart). Jamais use para valores
que podem diminuir.

```
# Exemplos corretos
http_requests_total{method="POST", path="/api/v1/boards", status="201"}
simulation_days_executed_total{scenario_id="..."}
domain_errors_total{type="ValidationError"}
```

### Gauge

Valor que **sobe e desce** livremente. Use para snapshots do estado atual.

```
# Exemplos corretos
db_connection_pool_active{pool="hikari"}
jvm_memory_used_bytes{area="heap"}
simulation_wip_count{scenario_id="..."}
```

### Histogram

Amostra observações em **buckets configurados**. Ideal para latência e tamanhos.
Expõe `_bucket`, `_sum`, `_count`. Use `histogram_quantile()` no PromQL.

```
# Exemplos corretos
http_request_duration_seconds_bucket{le="0.05", path="/api/v1/scenarios/.../run"}
http_request_duration_seconds_sum
http_request_duration_seconds_count

simulation_day_duration_seconds_bucket{le="0.1"}
```

### Summary

Similar ao Histogram, mas calcula quantis no **cliente** (não no servidor).
Prefira Histogram quando puder — permite agregação entre instâncias. Use Summary
apenas quando os buckets de Histogram não são conhecidos antecipadamente.

---

## III. Camada 1 — Logs JSON Estruturado

### Por que JSON?

Logs em texto livre (`%msg%n`) requerem parsing com regex no Loki/CloudWatch/Datadog.
Logs JSON são parseados nativamente, permitem filtros por campo (`level`, `traceId`,
`requestId`) e correlação automática com traces.

### Dependências

```kotlin
// http_api/build.gradle.kts
implementation("net.logstash.logback:logstash-logback-encoder:8.0")
```

### logback.xml — configuração dual (dev texto / prod JSON)

```xml
<!-- http_api/src/main/resources/logback.xml -->
<configuration>

    <!-- Appender para desenvolvimento: texto legível -->
    <appender name="STDOUT_TEXT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [rid=%X{requestId}] [tid=%X{traceId}] - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Appender para produção: JSON estruturado -->
    <appender name="STDOUT_JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeContext>false</includeContext>
            <includeMdcKeyName>requestId</includeMdcKeyName>
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <customFields>{"service":"kanban-vision-api","version":"${APP_VERSION:-local}"}</customFields>
        </encoder>
    </appender>

    <!-- Seleciona appender via variável de ambiente LOG_FORMAT=json -->
    <if condition='property("LOG_FORMAT").equals("json")'>
        <then>
            <root level="INFO">
                <appender-ref ref="STDOUT_JSON"/>
            </root>
        </then>
        <else>
            <root level="INFO">
                <appender-ref ref="STDOUT_TEXT"/>
            </root>
        </else>
    </if>

    <logger name="io.ktor" level="INFO"/>
    <logger name="com.kanbanvision" level="DEBUG"/>
</configuration>
```

### Resultado no formato JSON

```json
{
  "@timestamp": "2026-03-16T14:23:11.432Z",
  "level": "INFO",
  "logger_name": "com.kanbanvision.usecases.board.CreateBoardUseCase",
  "message": "Board created: id=abc-123 name=Meu Board duration=12ms",
  "service": "kanban-vision-api",
  "requestId": "req-uuid-here",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7"
}
```

Os campos `traceId` e `spanId` são preenchidos automaticamente pelo **OTel Log Bridge**
(Camada 3) — os logs ficam correlacionados aos traces sem código adicional.

---

## IV. Camada 2 — Métricas com Micrometer + Prometheus

### Por que Micrometer (não OTel Metrics diretamente)?

O Ktor tem plugin nativo para **Micrometer** (`ktor-server-metrics-micrometer`) que
instrumenta automaticamente todas as rotas HTTP (latência, contagem, status codes).
Micrometer pode exportar para múltiplos backends — Prometheus, OTel, Datadog — via
registry. É o bridge perfeito para este stack.

### Dependências

```kotlin
// http_api/build.gradle.kts
implementation("io.ktor:ktor-server-metrics-micrometer-jvm:3.1.2")
implementation("io.micrometer:micrometer-registry-prometheus:1.14.4")
implementation("io.micrometer:micrometer-core:1.14.4")
```

### Plugin de Métricas no Ktor

```kotlin
// http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/Metrics.kt
package com.kanbanvision.httpapi.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheusregistry.PrometheusConfig
import io.micrometer.prometheusregistry.PrometheusMeterRegistry

fun Application.configureMetrics(): MeterRegistry {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT).apply {
        config().commonTags(
            "service", "kanban-vision-api",
            "version", System.getenv("APP_VERSION") ?: "local",
        )
    }

    // Métricas automáticas de JVM e sistema
    JvmMemoryMetrics().bindTo(registry)
    JvmGcMetrics().bindTo(registry)
    JvmThreadMetrics().bindTo(registry)
    ProcessorMetrics().bindTo(registry)

    // Plugin Ktor: instrumenta TODAS as rotas HTTP automaticamente
    install(MicrometerMetrics) {
        this.registry = registry
        // distribui latência em buckets de 1ms a 10s
        distributionStatisticConfig {
            percentilesHistogram = true
        }
    }

    // Endpoint /metrics para scraping do Prometheus
    routing {
        get("/metrics") {
            call.respond(registry.scrape())
        }
    }

    return registry
}
```

### Métricas de Domínio (instrumentação manual)

```kotlin
// http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/DomainMetrics.kt
package com.kanbanvision.httpapi.plugins

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit

class DomainMetrics(private val registry: MeterRegistry) {

    // Counter: total de simulações executadas
    fun recordSimulationDayExecuted(scenarioId: String) {
        registry.counter(
            "kanban.simulation.days.executed.total",
            "scenario_id", scenarioId,
        ).increment()
    }

    // Histogram: duração de um dia de simulação
    fun recordSimulationDayDuration(durationMs: Long) {
        registry.timer("kanban.simulation.day.duration")
            .record(durationMs, TimeUnit.MILLISECONDS)
    }

    // Counter: erros de domínio por tipo
    fun recordDomainError(errorType: String) {
        registry.counter(
            "kanban.domain.errors.total",
            "type", errorType,
        ).increment()
    }

    // Gauge: WIP atual de um cenário (snapshot)
    fun recordWipCount(scenarioId: String, count: Int) {
        registry.gauge(
            "kanban.scenario.wip.current",
            listOf(
                io.micrometer.core.instrument.Tag.of("scenario_id", scenarioId),
            ),
            count,
        )
    }
}
```

### Métricas automáticas geradas pelo plugin Ktor

```
# Latência de requisições HTTP (histogram)
http_server_requests_seconds_bucket{method="POST",status="201",uri="/api/v1/boards",le="0.05"}
http_server_requests_seconds_sum{method="POST",status="201",uri="/api/v1/boards"}
http_server_requests_seconds_count{method="POST",status="201",uri="/api/v1/boards"}

# JVM
jvm_memory_used_bytes{area="heap",id="G1 Eden Space"}
jvm_gc_pause_seconds_count{action="end of minor GC",cause="G1 Evacuation Pause"}
jvm_threads_live_threads

# Sistema
system_cpu_usage
process_cpu_usage
```

---

## V. Health Check com Dependências

O health check atual retorna sempre `200 OK` sem verificar o banco. Isso é um
falso positivo — o load balancer considera o serviço saudável mesmo se o PostgreSQL
estiver fora.

```kotlin
// http_api/src/main/kotlin/com/kanbanvision/httpapi/routes/HealthRoutes.kt
package com.kanbanvision.httpapi.routes

import com.kanbanvision.persistence.DatabaseFactory
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes() {
    // Liveness: o processo está vivo?
    get("/health/live") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
    }

    // Readiness: o serviço está pronto para receber tráfego?
    get("/health/ready") {
        val dbStatus = runCatching {
            DatabaseFactory.dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT 1").executeQuery().next()
            }
            "UP"
        }.getOrElse { "DOWN: ${it.message}" }

        val status = if (dbStatus == "UP") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(
            status,
            mapOf(
                "status" to if (dbStatus == "UP") "UP" else "DOWN",
                "checks" to mapOf("database" to dbStatus),
            ),
        )
    }

    // Mantém /health para compatibilidade retroativa
    get("/health") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
    }
}
```

**Dois endpoints separados** é a convenção Kubernetes:
- `/health/live` → **liveness probe**: se falhar, reinicia o pod
- `/health/ready` → **readiness probe**: se falhar, remove do load balancer sem reiniciar

---

## VI. Camada 3 — Traces com OpenTelemetry Java Agent

### Estratégia: Java Agent (zero-code)

O **OpenTelemetry Java Agent** instrumenta automaticamente o Ktor (Netty), JDBC,
HikariCP, Koin e Logback sem alterar o código da aplicação. É o caminho com melhor
relação custo/benefício para este projeto.

**O que o agente instrumenta automaticamente:**
- Spans HTTP de entrada (método, path, status, duração)
- Spans JDBC (query SQL, duração — sem os valores dos parâmetros, por segurança)
- HikariCP connection pool (tempo de espera por conexão)
- Propagação de contexto W3C TraceContext (`traceparent` header)
- Bridge de logs: injeta `trace_id` e `span_id` no MDC do Logback automaticamente

### Configuração via variáveis de ambiente

```bash
# docker-compose.yml ou Kubernetes env
JAVA_TOOL_OPTIONS="-javaagent:/opt/opentelemetry-javaagent.jar"

# Exportador: envia para Grafana Alloy / OTel Collector
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
OTEL_EXPORTER_OTLP_PROTOCOL=grpc

# Identificação do serviço (aparece em todos os spans)
OTEL_SERVICE_NAME=kanban-vision-api
OTEL_RESOURCE_ATTRIBUTES=service.version=1.0.0,deployment.environment=production

# Sampling: 100% em dev, ajustar em prod (ex: 0.1 = 10%)
OTEL_TRACES_SAMPLER=parentbased_traceidratio
OTEL_TRACES_SAMPLER_ARG=1.0

# Métricas via OTel (alternativa ao Micrometer/Prometheus)
OTEL_METRICS_EXPORTER=none  # desabilitar se usando Micrometer
OTEL_LOGS_EXPORTER=none     # desabilitar se usando logstash-logback-encoder
```

### Dockerfile com agente

```dockerfile
# http_api/Dockerfile
FROM eclipse-temurin:21-jre AS runtime

# Download do agente (fixar versão para builds reproduzíveis)
ARG OTEL_AGENT_VERSION=2.12.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar \
    /opt/opentelemetry-javaagent.jar

WORKDIR /app
COPY build/libs/kanban-vision-api.jar app.jar

ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/opentelemetry-javaagent.jar"
ENV LOG_FORMAT=json

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Spans customizados (instrumentação manual complementar)

O agente cobre HTTP e JDBC. Para lógica de domínio relevante (ex: execução de simulação),
adicione spans manuais:

```kotlin
// Dependência adicional para instrumentação manual
// implementation("io.opentelemetry:opentelemetry-api:1.47.0")

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.StatusCode

class RunDayUseCase(
    private val scenarioRepository: ScenarioRepository,
    private val snapshotRepository: SnapshotRepository,
) {
    private val tracer = GlobalOpenTelemetry.getTracer("com.kanbanvision.usecases")

    suspend fun execute(command: RunDayCommand): Either<DomainError, DailySnapshot> {
        val span = tracer.spanBuilder("simulation.runDay")
            .setAttribute("scenario.id", command.scenarioId.value)
            .setAttribute("simulation.day", command.day.value.toLong())
            .startSpan()

        return try {
            span.makeCurrent().use {
                either {
                    // lógica existente sem alteração
                    val scenario = scenarioRepository.findById(command.scenarioId).bind()
                    val state = scenarioRepository.findState(command.scenarioId).bind()
                    val result = SimulationEngine.runDay(scenario.id, state, emptyList(), state.currentDay.value.toLong())
                    span.setAttribute("simulation.wip_count", result.snapshot.metrics.wipCount.toLong())
                    span.setAttribute("simulation.throughput", result.snapshot.metrics.throughput.toLong())
                    snapshotRepository.save(result.snapshot).bind()
                    result.snapshot
                }.also { either ->
                    either.fold(
                        ifLeft = { error ->
                            span.setStatus(StatusCode.ERROR, error.toString())
                        },
                        ifRight = { span.setStatus(StatusCode.OK) },
                    )
                }
            }
        } finally {
            span.end()
        }
    }
}
```

---

## VII. Stack Local de Observabilidade (docker-compose)

Stack completo para desenvolvimento e homologação, usando o Grafana OSS.

```yaml
# docker-compose.observability.yml
version: "3.9"

services:
  # ──────────────── Aplicação ────────────────
  kanban-api:
    build: .
    ports:
      - "8080:8080"
    environment:
      LOG_FORMAT: json
      DATABASE_URL: jdbc:postgresql://postgres:5432/kanbanvision
      DATABASE_USER: kanban
      DATABASE_PASSWORD: kanban
      JAVA_TOOL_OPTIONS: "-javaagent:/opt/opentelemetry-javaagent.jar"
      OTEL_SERVICE_NAME: kanban-vision-api
      OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4317
      OTEL_TRACES_SAMPLER: parentbased_traceidratio
      OTEL_TRACES_SAMPLER_ARG: "1.0"
      OTEL_METRICS_EXPORTER: none
      OTEL_LOGS_EXPORTER: none
    depends_on:
      - postgres
      - otel-collector

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: kanbanvision
      POSTGRES_USER: kanban
      POSTGRES_PASSWORD: kanban
    volumes:
      - postgres_data:/var/lib/postgresql/data

  # ──────────────── OTel Collector ────────────────
  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.119.0
    volumes:
      - ./config/otel-collector.yml:/etc/otel/config.yml
    command: ["--config=/etc/otel/config.yml"]
    ports:
      - "4317:4317"   # OTLP gRPC
      - "4318:4318"   # OTLP HTTP
    depends_on:
      - tempo
      - loki

  # ──────────────── Grafana Stack ────────────────
  grafana:
    image: grafana/grafana:11.4.0
    ports:
      - "3000:3000"
    environment:
      GF_AUTH_ANONYMOUS_ENABLED: "true"
      GF_AUTH_ANONYMOUS_ORG_ROLE: Admin
    volumes:
      - ./config/grafana/datasources:/etc/grafana/provisioning/datasources
      - ./config/grafana/dashboards:/etc/grafana/provisioning/dashboards
    depends_on:
      - prometheus
      - loki
      - tempo

  prometheus:
    image: prom/prometheus:v3.1.0
    volumes:
      - ./config/prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  loki:
    image: grafana/loki:3.3.2
    ports:
      - "3100:3100"
    command: -config.file=/etc/loki/local-config.yaml

  tempo:
    image: grafana/tempo:2.7.0
    command: ["-config.file=/etc/tempo/config.yml"]
    volumes:
      - ./config/tempo.yml:/etc/tempo/config.yml
    ports:
      - "3200:3200"
      - "4317"   # recebe OTLP do collector

volumes:
  postgres_data:
```

### config/otel-collector.yml

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 1s
  memory_limiter:
    limit_mib: 256

exporters:
  otlp/tempo:
    endpoint: tempo:4317
    tls:
      insecure: true
  loki:
    endpoint: http://loki:3100/loki/api/v1/push
    default_labels_enabled:
      exporter: false
      job: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [otlp/tempo]
    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [loki]
```

### config/prometheus.yml

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: kanban-vision-api
    static_configs:
      - targets: ["kanban-api:8080"]
    metrics_path: /metrics
```

---

## VIII. Convenções Semânticas (Naming)

Siga as [Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/) para que
os dados sejam interpretáveis por ferramentas de observabilidade padrão.

### Nomes de Métricas

| Padrão | Exemplo correto | Exemplo errado |
|---|---|---|
| `snake_case` por namespace | `kanban.simulation.days.executed.total` | `simDaysRun` |
| Sufixo de unidade | `http_request_duration_seconds` | `http_request_duration` |
| Sufixo `_total` para counters | `domain_errors_total` | `domain_errors` |
| Namespace por domínio | `kanban.board.created.total` | `created_total` |

### Atributos de Span (HTTP — convenção OTel)

| Atributo | Valor exemplo |
|---|---|
| `http.request.method` | `POST` |
| `http.route` | `/api/v1/scenarios/{scenarioId}/run` |
| `http.response.status_code` | `201` |
| `url.scheme` | `https` |
| `server.address` | `api.kanbanvision.io` |

### Atributos de Span (DB — convenção OTel)

| Atributo | Valor exemplo |
|---|---|
| `db.system` | `postgresql` |
| `db.name` | `kanbanvision` |
| `db.operation.name` | `INSERT` |
| `db.collection.name` | `boards` |

### Labels de Métricas (domínio)

```kotlin
// ✅ Labels de baixa cardinalidade (poucos valores distintos)
"status" to "success"          // success, error
"error_type" to "ValidationError"  // sealed class, conjunto fechado
"http_method" to "POST"        // GET, POST, PUT, DELETE

// ❌ Labels de alta cardinalidade (evitar — explodem o número de séries)
"scenario_id" to scenarioId.value   // UUID aleatório — quebra o Prometheus
"request_id" to requestId          // único por requisição
"user_id" to userId                // use em spans/logs, não em métricas
```

---

## IX. PromQL — Queries Essenciais

```promql
# Taxa de requisições por segundo (últimos 5 min)
rate(http_server_requests_seconds_count{job="kanban-vision-api"}[5m])

# Percentil 95 de latência HTTP
histogram_quantile(0.95,
  rate(http_server_requests_seconds_bucket{job="kanban-vision-api"}[5m])
)

# Taxa de erros 5xx
rate(http_server_requests_seconds_count{status=~"5.."}[5m])
  /
rate(http_server_requests_seconds_count[5m])

# Uso de memória heap JVM
jvm_memory_used_bytes{area="heap"}
  /
jvm_memory_max_bytes{area="heap"}

# Simulações executadas por minuto
rate(kanban_simulation_days_executed_total[1m])

# Uso do connection pool HikariCP
hikaricp_connections_active{pool="HikariPool-1"}
  /
hikaricp_connections_max{pool="HikariPool-1"}
```

---

## X. Alertas Recomendados

Configure no Grafana Alerting ou Prometheus Alertmanager:

```yaml
# config/prometheus-alerts.yml
groups:
  - name: kanban-vision-api
    rules:

      - alert: HighErrorRate
        expr: |
          rate(http_server_requests_seconds_count{status=~"5.."}[5m])
          /
          rate(http_server_requests_seconds_count[5m]) > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Taxa de erros HTTP > 5% por 2 minutos"

      - alert: HighLatencyP95
        expr: |
          histogram_quantile(0.95,
            rate(http_server_requests_seconds_bucket[5m])
          ) > 2.0
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Latência P95 > 2s por 5 minutos"

      - alert: DatabaseConnectionPoolExhausted
        expr: |
          hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Pool HikariCP > 90% de uso"

      - alert: ServiceDown
        expr: up{job="kanban-vision-api"} == 0
        for: 30s
        labels:
          severity: critical
        annotations:
          summary: "kanban-vision-api está fora do ar"
```

---

## XI. Caminho de Implementação Incremental

Cada passo entrega valor imediato e é independente dos seguintes.

```
Passo 1 — Logs JSON (GAP-F)
  ├── Adicionar logstash-logback-encoder ao build.gradle.kts
  ├── Atualizar logback.xml com appender JSON condicional (LOG_FORMAT=json)
  └── Testar: LOG_FORMAT=json ./gradlew :http_api:run

Passo 2 — Health Check real (GAP-B)
  ├── Adicionar GET /health/live e GET /health/ready em HealthRoutes.kt
  ├── /health/ready verifica DatabaseFactory.dataSource.connection
  └── Testar: curl localhost:8080/health/ready

Passo 3 — Métricas Prometheus (GAP-D)
  ├── Adicionar ktor-server-metrics-micrometer + micrometer-registry-prometheus
  ├── Criar plugins/Metrics.kt com configureMetrics()
  ├── Adicionar GET /metrics em Routing.kt
  ├── Criar DomainMetrics para métricas de negócio
  └── Testar: curl localhost:8080/metrics | grep kanban

Passo 4 — Docker + Observability Stack (GAP-G + stack local)
  ├── Criar Dockerfile multi-stage com OTel Java Agent embutido
  ├── Criar docker-compose.yml (app + postgres)
  ├── Criar docker-compose.observability.yml (collector + grafana + prometheus + loki + tempo)
  └── Testar: docker compose -f docker-compose.yml -f docker-compose.observability.yml up

Passo 5 — Traces distribuídos (GAP-O)
  ├── Configurar variáveis OTEL_* no Dockerfile / docker-compose
  ├── Verificar spans automáticos (HTTP + JDBC) no Grafana Tempo
  ├── Adicionar spans manuais em RunDayUseCase (operação de alto valor)
  └── Verificar correlação trace_id nos logs JSON (automática pelo agente)
```

---

## XII. Checklist de Qualidade

### Logs

- [ ] Logs em produção usam formato **JSON** (appender logstash-logback-encoder)?
- [ ] Todo log de erro inclui **stack trace** (`e.message` não é suficiente — use `log.error("msg", e)`)?
- [ ] `requestId` está presente no MDC em **todas** as requisições?
- [ ] `traceId` e `spanId` estão nos logs quando o agente OTel está ativo?
- [ ] Nenhuma informação sensível (senha, token, PII) é logada?

### Métricas

- [ ] Endpoint `/metrics` responde com métricas Prometheus?
- [ ] Métricas HTTP automáticas (latência, contagem, status) estão presentes?
- [ ] Métricas de JVM (heap, GC, threads) estão presentes?
- [ ] Métricas de domínio relevantes (`simulation.days.executed.total`, etc.) foram adicionadas?
- [ ] Labels são de **baixa cardinalidade** (sem UUIDs, sem requestId)?
- [ ] Nomes seguem `snake_case` com unidade e sufixo `_total`/`_seconds`/`_bytes`?

### Health Check

- [ ] `/health/ready` verifica conectividade com **PostgreSQL**?
- [ ] `/health/live` retorna `200 OK` sempre (não verifica dependências)?
- [ ] Retorna `503 Service Unavailable` quando dependência está fora?
- [ ] Kubernetes probes apontam para os endpoints corretos (`/live` e `/ready`)?

### Traces

- [ ] Spans HTTP de entrada incluem `http.route`, `http.method`, `http.status_code`?
- [ ] Spans JDBC incluem `db.system`, `db.operation.name`?
- [ ] Operações de alto valor têm spans manuais (`simulation.runDay`)?
- [ ] Erros de domínio são registrados nos spans com `span.setStatus(StatusCode.ERROR)`?
- [ ] `trace_id` está correlacionado nos logs JSON?

### Infraestrutura

- [ ] Prometheus faz scraping do endpoint `/metrics` a cada 15s?
- [ ] Grafana tem datasource configurado para Prometheus, Loki e Tempo?
- [ ] Alertas críticos estão configurados (error rate, latência P95, pool esgotado, serviço fora)?
- [ ] OTel Collector está configurado com `memory_limiter` para evitar OOM?
- [ ] Java Agent está fixado em versão específica (não `latest`) para builds reproduzíveis?

---

## Referências

- OpenTelemetry. *Documentation*. https://opentelemetry.io/docs/
- OpenTelemetry. *Java Instrumentation*. https://opentelemetry.io/docs/languages/java/
- OpenTelemetry. *Semantic Conventions*. https://opentelemetry.io/docs/specs/semconv/
- Prometheus. *Overview*. https://prometheus.io/docs/introduction/overview/
- Prometheus. *Metric Types*. https://prometheus.io/docs/concepts/metric_types/
- Grafana. *Documentation*. https://grafana.com/docs/
- Grafana. *Loki*. https://grafana.com/docs/loki/
- Grafana. *Tempo*. https://grafana.com/docs/tempo/
- Ktor. *Micrometer Plugin*. https://ktor.io/docs/server-metrics-micrometer.html
- logstash-logback-encoder. https://github.com/logfellow/logstash-logback-encoder