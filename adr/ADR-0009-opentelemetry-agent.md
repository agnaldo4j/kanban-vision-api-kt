# ADR-0009 — OpenTelemetry Java Agent para Tracing Distribuído

## Cabeçalho

| Campo     | Valor                                              |
|-----------|----------------------------------------------------|
| Status    | Aceita                                             |
| Data      | 2026-03-17                                         |
| Autores   | @agnaldo4j                                         |
| Branch    | feat/adr-0009-otel-agent                           |
| PR        | https://github.com/agnaldo4j/kanban-vision-api-kt/pull/68 |
| Gap       | GAP-O — P3 Domínio                                 |
| Supersede | —                                                  |

---

## Contexto e Motivação

O projeto possui logs estruturados em JSON (GAP-F / ADR-0006), métricas Prometheus via Micrometer (GAP-D / ADR-0007) e alertas Grafana (GAP-U). O pilar restante da observabilidade é **distributed tracing**: rastrear o fluxo de uma requisição desde a entrada HTTP até as queries JDBC.

Estado atual:
- `Observability.kt` propaga `requestId` via MDC e via `X-Request-ID` — correlação de logs dentro de uma requisição funciona
- Não há `traceId`/`spanId` nos logs — ferramentas como Grafana Explore não conseguem saltar de uma log line para o trace correspondente
- Não há spans HTTP nem JDBC instrumentados — latências por operação são invisíveis
- O endpoint `/metrics` expõe métricas de latência HTTP (histograma Ktor), mas sem exemplares ligados a traces

Pré-requisito satisfeito: `GAP-G` (containerização) está concluído — o agente pode ser embutido na imagem Docker sem impacto no código-fonte.

Esta ADR é necessária agora porque GAP-O é classificado como `[E]` (estrutural): a decisão afeta o `Dockerfile`, o `docker-compose.yml`, os manifestos Kubernetes e potencialmente o `build.gradle.kts` (dependência `opentelemetry-api` para spans manuais).

---

## Forças (Decision Drivers)

- [ ] **Zero ou mínima alteração de código de produção** — o domínio deve permanecer puro; spans básicos não exigem dependências em `domain/` ou `usecases/`
- [ ] **Correlação logs ↔ traces** — `traceId` e `spanId` devem aparecer nos logs JSON, permitindo navegação de uma log line ao trace
- [ ] **Cobertura automática de HTTP + JDBC** — auto-instrumentação de Ktor (entrada HTTP) e HikariCP/PostgreSQL (queries) sem escrever spans manualmente
- [ ] **Backend local integrado ao stack Grafana existente** — evitar abrir um novo painel / ferramenta para equipe
- [ ] **Sem degradação de performance observável** — overhead do agente deve ser < 5% em p99 no ambiente de desenvolvimento
- [ ] **Compatibilidade com Java 21 e Ktor 3.x** — sem conflitos de classpath

---

## Opções Consideradas

- **Opção A**: OTel Java Agent (auto-instrumentação, zero código)
- **Opção B**: OTel SDK — instrumentação manual explícita
- **Opção C**: Micrometer Tracing + OTel Bridge

---

## Decisão

**Escolhemos Opção A** (OTel Java Agent) com backend **Grafana Tempo** porque entrega auto-instrumentação completa de HTTP e JDBC sem tocar no código de produção, integra nativamente com o Grafana já provisionado via exemplares, e respeita a Dependency Rule — o agente opera como processo externo na JVM, não como dependência de módulo. O único código novo é opcional: um span manual no `RunDayUseCase` usando `opentelemetry-api` apenas em `http_api`.

---

## Análise das Opções

### Opção A — OTel Java Agent

O agente é baixado como jar no `Dockerfile` e injetado via `-javaagent`. Auto-instrumenta Ktor HTTP, HikariCP, JDBC e SLF4J (log bridge: adiciona `traceId`/`spanId` aos logs MDC automaticamente).

**Prós:**
- Zero alteração em `domain/`, `usecases/`, `sql_persistence/`
- Auto-instrumentação cobre 95% dos spans úteis (HTTP in, DB out)
- Log bridge injeta `traceId`/`spanId` no MDC sem código extra
- Amplamente testado com JVM 21; suporte oficial para Ktor 3.x via `ktor-2.0` instrumentation
- Configuração inteiramente por variáveis de ambiente — sem recompilação

**Contras:**
- Jar extra (~15 MB) na imagem runtime
- Startup time ligeiramente maior (instrumentação em bytecode ao subir)
- Spans manuais de negócio exigem `opentelemetry-api` como dependência em `http_api` (só se desejado)

### Opção B — OTel SDK Manual

Adicionar `opentelemetry-sdk` e instrumentar cada operação explicitamente.

**Prós:**
- Controle total sobre quais spans criar e com quais atributos

**Contras:**
- Requer mudanças em `usecases/` e potencialmente `domain/` — viola Dependency Rule
- Alto custo de implementação por operação: `tracer.spanBuilder(...).startSpan()` em cada use case
- Cobertura JDBC ausente sem instrumentação adicional do pool
- Duplica esforço que o agente faz automaticamente

### Opção C — Micrometer Tracing + OTel Bridge

Usar `micrometer-tracing-bridge-otel` (já usamos Micrometer para métricas).

**Prós:**
- API unificada Micrometer para métricas e traces
- Familiar: mesma abstração já usada em `Metrics.kt`

**Contras:**
- Requer injeção explícita de `Tracer` em cada use case que precise de span — viola Dependency Rule para spans de negócio
- Auto-instrumentação HTTP/JDBC ainda exige o agente ou bibliotecas adicionais de integração
- Adiciona dependência em `usecases/` para o `Tracer`, quebrando o isolamento de camada
- Complexidade maior sem benefício claro sobre Opção A para este estágio do projeto

---

## Consequências

**Positivas:**
- Logs JSON passam a incluir `trace_id` e `span_id` automaticamente (via log bridge do agente)
- Grafana Explore: salto direto de log line → trace → span JDBC
- Visibilidade de latência por operação (HTTP handler, query Flyway, query de negócio)
- Exemplares nos histogramas Prometheus ligam métrica de latência ao trace correspondente

**Negativas / Trade-offs:**
- Imagem Docker aumenta ~15 MB (jar do agente) — mitigado por layer cache
- Startup ~1–3 s mais lento — aceitável para o ambiente atual; sem impacto em produção com K8s readiness probe
- Grafana Tempo adiciona um container ao docker-compose — recurso local aumenta ~512 MB RAM

**Neutras:**
- O agente não altera o comportamento da aplicação — apenas observa
- Variáveis OTel são opt-in: sem `OTEL_EXPORTER_OTLP_ENDPOINT` o agente exporta para `/dev/null` (modo no-op)

---

## Plano de Implementação

### Fase 1 — Agente no Dockerfile e docker-compose

- [ ] **1.** Adicionar etapa no `Dockerfile` (stage `build` ou layer dedicado): baixar `opentelemetry-javaagent.jar` via `curl` de `https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.14.0/opentelemetry-javaagent.jar`
- [ ] **2.** Copiar o jar para o stage `runtime` em `/app/opentelemetry-javaagent.jar`
- [ ] **3.** Atualizar `ENTRYPOINT` no `Dockerfile` para incluir `-javaagent:/app/opentelemetry-javaagent.jar`
- [ ] **4.** Adicionar serviço `tempo` no `docker-compose.yml` (image `grafana/tempo:2.4.1`) com porta OTLP gRPC `4317`
- [ ] **5.** Adicionar variáveis OTel ao serviço `app` no `docker-compose.yml`:
  ```
  OTEL_SERVICE_NAME: "kanban-vision-api"
  OTEL_EXPORTER_OTLP_ENDPOINT: "http://tempo:4317"
  OTEL_EXPORTER_OTLP_PROTOCOL: "grpc"
  OTEL_RESOURCE_ATTRIBUTES: "deployment.environment=local"
  OTEL_LOGS_EXPORTER: "none"   # logs via SLF4J bridge (MDC)
  OTEL_METRICS_EXPORTER: "none" # métricas via Micrometer/Prometheus
  OTEL_TRACES_EXPORTER: "otlp"
  ```
- [ ] **6.** Provisionar Tempo como datasource no Grafana (`observability/grafana/provisioning/datasources/tempo.yml`)

### Fase 2 — Kubernetes

- [ ] **7.** Adicionar variáveis OTel ao `k8s/01-configmap.yml` (service name, OTLP endpoint, protocol)
- [ ] **8.** Referenciar as novas chaves do ConfigMap no `k8s/03-deployment.yml` via `envFrom` ou `env[].valueFrom`

### Fase 3 — Span manual (opcional, baixo risco)

- [ ] **9.** Adicionar `opentelemetry-api:2.14.0` como dependência `implementation` **somente em `http_api/build.gradle.kts`** (não em `usecases/` nem `domain/`)
- [ ] **10.** Criar `SpanHelper.kt` em `http_api/` com função de extensão que envolve uma suspending lambda em um span nomeado
- [ ] **11.** Envolver a chamada a `runDayUseCase.execute(...)` em `ScenarioRoutes.kt` com o span `simulation.run_day`

### Fase 4 — Verificação e qualidade

- [ ] **12.** Subir stack com `docker compose up --build` e verificar no Grafana Explore (Tempo datasource) que spans HTTP aparecem
- [ ] **13.** Verificar nos logs JSON (`LOG_FORMAT=json`) que `trace_id` e `span_id` estão presentes
- [ ] **14.** Executar `./gradlew testAll` — build verde (sem novas dependências em `domain/usecases/sql_persistence/`)
- [ ] **15.** Atualizar ADR-0004: marcar GAP-O como `[x]` com referência ao PR
- [ ] **16.** Atualizar diagramas C4 no README (skill `/c4-model`) — Tempo aparece no diagrama de container

---

## Garantias de Qualidade

### DOD — Definition of Done

- [ ] **1. Contrato e Rastreabilidade**: branch `feat/adr-0009-otel-agent` → PR referenciado nesta ADR
- [ ] **2. Testes Técnicos**: `./gradlew testAll` verde; nenhum teste de unidade existente afetado (agente não altera lógica)
- [ ] **3. Versionamento e Compatibilidade**: `opentelemetry-javaagent.jar` versão fixada (v2.14.0); `opentelemetry-api` apenas em `http_api` se Fase 3 for executada
- [ ] **4. Segurança e Compliance**: nenhum secret adicionado; `OTEL_EXPORTER_OTLP_ENDPOINT` configurável por ambiente
- [ ] **5. CI/CD**: `./gradlew testAll` verde no CI; job `build` valida Dockerfile com agente
- [ ] **6. Observabilidade**: `trace_id` presente nos logs JSON; spans HTTP e JDBC visíveis no Grafana Tempo
- [ ] **7. Performance e Confiabilidade**: overhead do agente < 5% p99 verificado manualmente no ambiente local
- [ ] **8. Deploy Seguro**: rollback = remover `-javaagent` do `ENTRYPOINT` e variáveis OTel; health checks existentes não afetados
- [ ] **9. Documentação**: README atualizado com seção sobre tracing; CLAUDE.md Stack table atualizada com Jaeger/Tempo

### Qualidade de Código

| Ferramenta | Requisito               | Ação se falhar                 |
|------------|-------------------------|-------------------------------|
| Detekt     | zero violações          | Refatorar — nunca suprimir    |
| KtLint     | zero erros              | `./gradlew ktlintFormat`      |
| JaCoCo     | ≥ 95% por módulo        | Escrever teste faltante       |

### Aderência à Arquitetura

- [ ] **Dependency Rule**: `opentelemetry-api` adicionado **somente** em `http_api/build.gradle.kts` (se Fase 3 executada); `domain/`, `usecases/`, `sql_persistence/` permanecem sem dependência OTel
- [ ] **Domain puro**: zero imports de `io.opentelemetry` em `domain/` após a entrega
- [ ] **DTOs nas boundaries**: `SpanHelper.kt` fica em `http_api/` — não cruza para dentro do domínio

---

## Referências

- OTel Java Agent releases: https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases
- Ktor auto-instrumentation: https://opentelemetry.io/docs/zero-code/java/agent/
- Grafana Tempo: https://grafana.com/docs/tempo/latest/
- ADR-0006 — Logging estruturado JSON (log bridge contexto)
- ADR-0007 — Métricas Micrometer/Prometheus (contexto do stack de observabilidade)
- ADR-0004 — Avaliação de qualidade: GAP-O
- Skill: `/opentelemetry`
- Skill: `/local-and-production-environment`
- Skill: `/evolutionary-change`
