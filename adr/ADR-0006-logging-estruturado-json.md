# ADR-0006 — Logging Estruturado JSON com logstash-logback-encoder

**Data:** 2026-03-16
**Status:** Aceita
**Gap:** GAP-F (Ciclo Operações — P2)
**Autores:** Equipe de Engenharia

---

## Contexto

O projeto utiliza SLF4J + Logback com pattern de texto livre:

```
%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [rid=%X{requestId}] - %msg%n
```

Em ambientes de produção (Kubernetes, cloud), logs são coletados por agentes como Fluentd, Promtail ou CloudWatch Agent e ingeridos em plataformas como Loki, Datadog ou OpenSearch. Filtrar logs em texto livre exige regex frágil e impede queries por campo (`level`, `requestId`, `traceId`).

**Problema identificado no ADR-0004 (GAP-F):** sem logging estruturado, não é possível:
- Filtrar todos os logs de uma requisição por `requestId` sem regex
- Criar alertas baseados em `level=ERROR` de forma confiável
- Correlacionar logs com traces quando OTel for adicionado (GAP-O)

---

## Decisão

Adicionar `net.logstash.logback:logstash-logback-encoder:8.0` ao módulo `http_api` e configurar o `logback.xml` com **dois appenders** e seleção condicional via variável de ambiente `LOG_FORMAT`:

| `LOG_FORMAT` | Appender ativo | Formato |
|---|---|---|
| `json` | `STDOUT_JSON` (`LogstashEncoder`) | JSON estruturado — campos `level`, `message`, `logger_name`, `requestId`, `timestamp` |
| qualquer outro valor (incluindo ausente) | `STDOUT_TEXT` (pattern existente) | Texto legível — comportamento atual, preservado para dev |

### Por que `logstash-logback-encoder`?

- Integração nativa com Logback (zero código novo) — apenas configuração XML
- Suporte automático a MDC: `requestId` injetado pelo plugin de observabilidade do Ktor vira campo de primeiro nível no JSON
- Compatível com Loki, Datadog, OpenSearch, CloudWatch sem transformação adicional
- Quando GAP-O (OTel) for implementado, `traceId` e `spanId` injetados via MDC Bridge também aparecem automaticamente como campos JSON
- Amplamente adotado no ecossistema JVM/Kotlin

### Por que appender condicional e não sempre JSON?

- Logs JSON são difíceis de ler em desenvolvimento — reduz experiência local
- Não requer mudança no processo de desenvolvimento existente
- Em produção, `LOG_FORMAT=json` é setado via variável de ambiente no container (Kubernetes ConfigMap / docker-compose env)

---

## Consequências

### Positivas
- Logs de produção filtráveis por `requestId`, `level`, `logger_name` sem regex
- Preparação para correlação com traces (GAP-O): MDC `traceId`/`spanId` aparecerão automaticamente como campos JSON
- Zero alteração de código de produção — mudança puramente de configuração
- Dev experience preservada (texto por default)

### Negativas / trade-offs
- Adição de dependência `logstash-logback-encoder` (~150 KB JAR)
- `logback-janino` (necessário para `<if condition>` no XML) é uma dependência transitiva adicionada — sem impacto em produção
- Logs em staging/CI são texto por default; equipes devem lembrar de setar `LOG_FORMAT=json` no ambiente de homologação

---

## Alternativas Consideradas

| Alternativa | Motivo de rejeição |
|---|---|
| Sempre JSON (sem condicional) | Degrada experiência de desenvolvimento local |
| Logback `JsonEncoder` nativo (Logback 1.4+) | Formato menos compatível com plataformas de log existentes; logstash-encoder tem maior adoção |
| Mudar para Log4j2 + JSON layout | Mudança estrutural desnecessária; o projeto já usa Logback via Ktor |
| Implementar JSON manualmente via `LayoutWrappingEncoder` | Reinventar o que logstash-encoder já resolve |

---

## Validação

- `./gradlew testAll` verde com novo `logback.xml`
- Em dev (sem `LOG_FORMAT`): output texto, sem regressão
- Com `LOG_FORMAT=json`: output JSON contendo campos `level`, `message`, `logger_name`, `requestId`

---

## Referências

- [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder)
- [Logback conditional configuration (Janino)](https://logback.qos.ch/manual/configuration.html#conditional)
- ADR-0004 — GAP-F: "Logs em texto, não JSON"
- ADR-0005 — GAP-A: contexto de requestId via MDC
