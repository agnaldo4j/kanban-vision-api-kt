# Leak de contexto OTel entre requests no binário nativo — diagnóstico e mitigação (GAP-BC)

**Data**: 2026-07-08 · **Follow-up de**: ADR-0032 / GAP-BB (`docs/quality/performance-baseline-2026-07-native.md`)
**Versões**: Ktor 3.5.1 · opentelemetry-java-instrumentation 2.29.0-alpha · OTel SDK 1.63.0 · GraalVM 25

## Sintoma (observado no GAP-BB)

No binário nativo, sob carga, o event loop do Netty retém o contexto OTel do request
anterior: o `Instrumenter` do `KtorServerTelemetry` suprime novos spans SERVER
(suppression por `SpanKind`) e os spans JDBC/manuais dos requests seguintes encadeiam
num mesmo trace gigante ou nascem como traces sem raiz SERVER.

## Root cause (upstream)

O `SuspendFunctionGun` (SFG) do Ktor executa interceptors com uma continuation
compartilhada; em suspensões com dispatcher externo (`withContext(Dispatchers.IO)`) o
`ThreadContextElement` do OTel (`Context.asContextElement()`) recebe `updateThreadContext`
sem o `restoreThreadContext` pareado — a thread do event loop fica presa ao contexto velho.

- [KTOR-9431](https://youtrack.jetbrains.com/issue/KTOR-9431) — exatamente este bug; **corrigido no Ktor 3.4.3** (PR ktorio/ktor#5503, contido na 3.5.1).
- [opentelemetry-java-instrumentation#16430](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/16430) — mesmo sintoma; fechado com workarounds; o instrumentation 2.29.0 já embute o paliativo `EmptyInterceptor`.
- [KTOR-6802](https://youtrack.jetbrains.com/issue/KTOR-6802) — variante com autoreload; ainda aberto.

**Os dois reparos conhecidos já estão nas versões do projeto e o leak ainda assim
reproduz no binário nativo** — a manifestação é específica de native image (o fix
KTOR-9431 usa "conditional redispatch" que se comporta diferente no AOT). Na JVM o
leak NÃO reproduz mais (validado com Netty real + 512 requests concorrentes).

## Evidência empírica (docker compose local, jornada k6 smoke, Tempo API)

| Cenário | Requests | Traces amostrados | Traces com exatamente 1 span SERVER | Observações |
|---|---|---|---|---|
| Nativo, default | 20.791 (0 falhas) | 5.000 | **14 (0,3%)** | 4.986 traces sem raiz SERVER; maior trace: **9.822 spans** |
| Nativo, `-Dio.ktor.internal.disable.sfg=true` | 22.041 (0 falhas) | 5.000 | **5.000 (100%)** | maior trace: 7 spans; rotas nomeadas (`GET .../{simulationId}/cfd`); throughput smoke 734 vs 692 req/s (≥ baseline) |
| Nativo, imagem final (flag no ENTRYPOINT) | 14.871 (0 falhas) | 5.000 | **5.000 (100%)** | rebuild com o Dockerfile deste PR; mesmo resultado |
| JVM (test host sequencial + Netty concorrente) | 24 + 512 | — | 100% | `TelemetryContextIsolationTest` — safety net permanente |

Metodologia: `docker compose up --build` (stack completo com Tempo), org seedada,
`k6 run load/simulation-journey.js` (smoke), análise via `GET /api/search` +
`GET /api/traces/{id}` do Tempo contando spans `SPAN_KIND_SERVER` por trace.

## Mitigação aplicada

`-Dio.ktor.internal.disable.sfg=true` no `ENTRYPOINT` do Dockerfile (runtime nativo).
A flag troca o SFG pelo `DebugPipelineContext` (caminho coroutines convencional, sem a
otimização de continuation compartilhada). Sem regressão de latência/throughput no smoke;
nomes de rota preservados (a mitigação por reset de contexto avaliada no GAP-BB degradava
o nome — esta não).

- Produção k8s segue com `OTEL_TRACES_EXPORTER=none` por default (inalterado); com a
  flag no ENTRYPOINT, ligar traces via patch passa a produzir traces íntegros.
- Dev/testes na JVM não precisam da flag.

## Follow-ups

- Reavaliar a flag a cada release do Ktor 3.x (procurar fix nativo do SFG) e do
  opentelemetry-java-instrumentation 2.x.
- Issue upstream **não** publicada nesta rodada (decisão registrada no card GAP-BC);
  este arquivo contém o material de repro caso se decida reportar.
- Achado colateral (fora de escopo, candidato a card): no nativo, o caminho de erro de
  `POST /auth/token` com payload inválido falha ao serializar `DomainErrorResponse`
  ("Cannot reflectively read or write field ... $$serializer") — gap de reachability
  metadata no caminho de erro.
