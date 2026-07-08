---
status: accepted
date: 2026-07-07
decision-makers: "@agnaldo4j"
---

# ADR-0031 — Observabilidade sem javaagent: traces via OTel SDK + instrumentação de biblioteca

> Supersede **parcialmente** a ADR-0009: sai o OTel Java Agent (mecanismo de instrumentação);
> ficam o backend Tempo, as envs `OTEL_*`, a política de sinais (traces=OTLP, metrics=Micrometer,
> logs=SLF4J) e a correlação log↔trace via MDC.

## Context and Problem Statement

A ADR-0009 adotou o **OTel Java Agent** (hoje v2.29.0, no `ENTRYPOINT` do Dockerfile) para
traces — único sinal sob responsabilidade do agente (`OTEL_METRICS_EXPORTER=none`,
`OTEL_LOGS_EXPORTER=none`; métricas são 100% Micrometer/Prometheus). O agente instrumenta
bytecode **em runtime**, o que não existe no mundo fechado do Native Image — ele é o bloqueador
declarado da Fase 2 da ADR-0030 (GAP-BB).

Remover o agente sem substituto quebra três coisas hoje:

1. `SpanHelper.withSpan` (`http_api/plugins/SpanHelper.kt`) vira **no-op** — o span manual
   `simulation.run_day` depende do `GlobalOpenTelemetry` que o agente registra.
2. `traceId`/`spanId` somem do MDC — o `logback-json.xml` os inclui via
   `includeMdcKeyName`, populados pelo log bridge do agente; ficariam **vazios em silêncio**.
3. Spans automáticos de HTTP (Ktor/Netty) e JDBC/HikariCP desaparecem.

**Pergunta a decidir**: como manter os traces (e a correlação log↔trace) sem o javaagent?

## Decision Drivers

- Destravar a Fase 2 Native Image (ADR-0030) — o agente é o pré-requisito duro do GAP-BB.
- Paridade de sinais é inegociável (ADR-0009): spans de HTTP e JDBC + `traceId`/`spanId` no MDC.
- Dependency Rule: nenhuma dependência OTel em `domain/`, `usecases/` ou `sql_persistence/` —
  a objeção da ADR-0009 às opções sem agente (instrumentar cada use case) precisa continuar valendo.
- Reaproveitar a infraestrutura existente: envs `OTEL_*`, Tempo em `:4317` (OTLP gRPC), Grafana.
- Mudança evolutiva: implementação em um PR focado (≤400 linhas), reversível.

## Considered Options

1. Manter o agente (status quo).
2. **OTel SDK autoconfigure + instrumentação de biblioteca** (`opentelemetry-ktor-3.0`,
   `opentelemetry-jdbc`, `opentelemetry-hikaricp-3.0`, `opentelemetry-logback-mdc-1.0`).
3. SDK manual sem bibliotecas de instrumentação (spans só onde escrevermos código).
4. Remover traces.

## Decision Outcome

**Escolhida: Opção 2.** O projeto opentelemetry-java-instrumentation publica as mesmas
instrumentações do agente como **bibliotecas standalone** (supported-libraries.md), instaláveis
exclusivamente nas camadas externas — o que invalida a objeção original da ADR-0009 à abordagem
sem agente. Mapa de paridade:

| Sinal (agente hoje) | Substituto em build time | Onde |
|---|---|---|
| Registro do `GlobalOpenTelemetry` | `AutoConfiguredOpenTelemetrySdk` (lê as mesmas envs `OTEL_*`) + `GlobalOpenTelemetry.set` | bootstrap do `http_api` (`Main.kt`/plugin) |
| Spans HTTP server (Ktor/Netty) | `opentelemetry-ktor-3.0` (plugin Ktor server) | `http_api/plugins` |
| Spans JDBC + pool | `opentelemetry-jdbc` (wrap do DataSource) + `opentelemetry-hikaricp-3.0` | `AppModule` (wiring DI — único lugar que já conhece o DataSource concreto) |
| `traceId`/`spanId` no MDC | `opentelemetry-logback-mdc-1.0` (appender wrapper) | `logback*.xml` |
| Span manual `simulation.run_day` | `SpanHelper.withSpan` inalterado (passa a exportar de verdade via SDK global) | já existe |
| Exportação OTLP → Tempo | `opentelemetry-exporter-otlp` | dependência do `http_api` |

**Fica igual**: envs `OTEL_*` do compose/k8s (o autoconfigure as lê), Tempo/porta 4317, métricas
Micrometer/Prometheus, dashboards Grafana, `OTEL_TRACES_EXPORTER=none` como default de produção
no ConfigMap k8s. Versões exatas dos artefatos são detalhe do PR de implementação — alinhar o
BOM de instrumentação (linha 2.x) com a API `io.opentelemetry:opentelemetry-api` 1.63.0 já usada.

### Confirmation

- **PR 1/2 (esta ADR)**: diff só de docs; `./gradlew testAll` e CI verdes.
- **PR 2/2 (implementação)**: `-javaagent` e o stage `otel-agent` fora do Dockerfile; trace da
  jornada k6 visível no Tempo (compose local); `traceId`/`spanId` presentes nos logs JSON;
  `SpanHelperTest` verde; **novo teste de integração de exportação de spans** (hoje não existe —
  rede de segurança a criar); baseline k6 comparativo (formato ADR-0027) — o agente custava
  1-3s de startup segundo a ADR-0009, ganho esperado na medição.

## Consequences

- Bom: destrava o GAP-BB (Native Image); a instrumentação vira código versionado e revisável em
  vez de bytecode mágico em runtime; ~15 MB e 1-3s de startup do agente saem do container.
- Bom: `SpanHelper.withSpan` passa a funcionar em qualquer ambiente com SDK registrado — hoje é
  no-op fora do container (testes, run local sem agente).
- Ruim: a cobertura de instrumentação vira responsabilidade nossa — biblioteca nova no stack
  exige avaliar cobertura manualmente (o agente cobria tudo "de graça"). Mitigação: a tabela de
  paridade acima é o contrato mínimo; gaps novos viram cards.
- Ruim: spans internos do Netty (abaixo do nível Ktor) deixam de existir — aceito, o nível Ktor
  cobre a jornada de negócio.
- Docs a atualizar no PR de implementação: `stack.md` (linha Observability), skill
  `/opentelemetry` (seção do agente), `CLAUDE.md` (tabela), `README.md` (menção ao agente).

## Pros and Cons of the Options

### Opção 1 — Manter o agente

- Bom: zero esforço; cobertura máxima automática.
- Ruim: bloqueia a Fase 2 permanentemente; startup e memória pagam o agente para sempre.

### Opção 2 — SDK autoconfigure + instrumentação de biblioteca

- Bom: paridade de sinais com o agente nas camadas que importam; Dependency Rule preservada
  (tudo em `http_api`/`AppModule`/logback); mesmas envs e infra.
- Ruim: mais dependências diretas no `http_api`; paridade a validar sinal a sinal na implementação.

### Opção 3 — SDK manual sem libs

- Bom: dependências mínimas.
- Ruim: cobertura cai para o único span manual; reconstruir HTTP/JDBC à mão é reinventar as
  bibliotecas da Opção 2 com mais bugs.

### Opção 4 — Remover traces

- Bom: simplicidade máxima.
- Ruim: viola o driver "observabilidade inegociável" (ADR-0009); regressão de diagnóstico.

## More Information

- Branch: `feat/adr-0031-observabilidade-sem-javaagent` · PR: 1/2 do GAP-AZ
- Item no board #6: [GAP-AZ](https://github.com/users/agnaldo4j/projects/6) — o plano de execução
  vive lá, não aqui (ADR-0023)
- Referências: ADR-0009 (decisão original e rollback previsto), ADR-0030 (Fase 2 bloqueada pelo
  agente), ADR-0027 (protocolo de medição), ADR-0023 (imutabilidade),
  https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/supported-libraries.md
