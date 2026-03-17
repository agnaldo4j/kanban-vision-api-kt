# ADR-0004 — Avaliação de Qualidade do Projeto: Nota, Gaps e Prioridades

## Cabeçalho

| Campo     | Valor                                                        |
|-----------|--------------------------------------------------------------|
| Status    | Aceita                                                       |
| Data      | 2026-03-16                                                   |
| Autores   | @agnaldo4j                                                   |
| Branch    | —                                                            |
| PR        | https://github.com/agnaldo4j/kanban-vision-api-kt/pull/56    |
| Supersede | —                                                            |

---

## Contexto e Motivação

Esta ADR documenta uma avaliação abrangente da qualidade do projeto `kanban-vision-api-kt`
realizada em 2026-03-16, cobrindo doze dimensões técnicas. O objetivo é registrar o estado
atual, identificar gaps com clareza e priorizar o trabalho futuro de melhoria de forma
rastreável. Cada gap listado aqui é um candidato a uma ADR de execução dedicada.

O projeto está em estado de produção emergente: arquitetura sólida, testes abrangentes,
zero violações de qualidade estática — mas com lacunas de produção que precisam ser
endereçadas antes de qualquer deploy real.

**Revisão 1 (2026-03-16):** avaliação expandida para incluir a dimensão de
**Modularidade & Evoluibilidade Arquitetural**, guiada pelo skill
`microservices-modular-monolith`. Esta dimensão avalia o quanto a estrutura atual
suporta a evolução incremental do projeto — seja crescendo como monólito modular
ou extraindo bounded contexts para microserviços quando necessário.

**Revisão 2 (2026-03-16):** dimensão **Observabilidade** enriquecida com diagnóstico
detalhado do estado atual do código (`Observability.kt`, `logback.xml`) e caminho de
implementação incremental em 5 passos guiado pelo skill `opentelemetry`. Adicionado
`GAP-U` (sem alertas configurados), reposicionado `GAP-O` (OTel traces) de P4 para P3
— o Java Agent entrega valor no monólito sem exigir extração de microserviços.

**Revisão 4 (2026-03-16):** adicionada dimensão **Governança de Mudanças Evolutivas**,
guiada pelo skill `evolutionary-change`. Esta dimensão avalia se o projeto tem um protocolo
explícito para executar seus próprios gaps de forma incremental e normativa — evitando crises
estruturais, regressões e esgotamento de contexto em sessões de LLM. O J-Curve revela que
mudanças grandes têm probabilidade de 82% de falha (PMI, 2014); o protocolo 1-gap-por-sessão
é a resposta estrutural a esse risco. Adicionado `GAP-W` (sem governança de execução dos gaps
documentada no projeto). Score geral revisado de **8.0 → 7.8** pela adição da nova dimensão
com nota 5.5.

**Revisão 3 (2026-03-16):** dimensão **Prontidão para Produção** enriquecida com o
detalhamento completo de infra trazido pelo skill `local-and-production-environment`.
`GAP-G` expandido: além de `Dockerfile` + `docker-compose`, requer manifestos Kubernetes
completos (`Namespace`, `ConfigMap`, `Secret`, `Deployment` com probes/resources/rolling,
`Service`, `Ingress`, `HPA`, `PDB`). Adicionado `GAP-V` (sem pipeline CI/CD de build e
deploy de imagem) — o CI atual só executa testes, sem construir imagem, fazer push para
registry ou acionar deployment.

---

## Nota Geral: **7.8 / 10**

### Metodologia de Pontuação

Cada dimensão foi avaliada independentemente e ponderada pelo seu impacto no ciclo de vida
do projeto (correção, manutenção, operação, evolução). A revisão 4 adicionou a dimensão
*Governança de Mudanças Evolutivas* e redistribuiu levemente os pesos das demais dimensões
(–1pp nas dimensões com peso ≥ 9%), resultando na revisão do score geral para **7.8**.

| Dimensão                              | Nota | Peso | Contribuição |
|---------------------------------------|------|------|--------------|
| Clean Architecture                    | 9.0  | 12%  | 1.08         |
| Domain-Driven Design (DDD)            | 8.0  | 12%  | 0.96         |
| Modularidade & Evoluibilidade         | 7.5  |  9%  | 0.68         |
| SOLID Principles                      | 9.0  |  8%  | 0.72         |
| Testes & Qualidade                    | 8.0  | 12%  | 0.96         |
| Kotlin Quality Pipeline               | 9.0  |  8%  | 0.72         |
| Observabilidade                       | 7.0  |  8%  | 0.56         |
| Prontidão para Produção               | 5.5  |  8%  | 0.44         |
| Governança de Mudanças Evolutivas     | 5.5  |  8%  | 0.44         |
| OpenAPI                               | 7.5  |  5%  | 0.38         |
| Design de Banco de Dados              | 8.0  |  5%  | 0.40         |
| Tratamento de Erros                   | 9.0  |  5%  | 0.45         |
| **Total**                             |      | 100% | **7.79**     |

---

## Avaliação por Dimensão

### Clean Architecture — 9.0/10

**Pontos fortes:**
- `domain/` é 100% puro: zero dependências de framework
- Portos (interfaces de repositório) vivem em `usecases/repositories/` — decisão explicitada em memória do projeto
- CQS aplicado sistematicamente: `Command.validate()` + `Query` marker
- DI wiring centralizado em `AppModule.kt` (Koin) — apenas `http_api` conhece implementações concretas
- DTOs de transporte isolados em `http_api/routes/` e `http_api/dtos/`

**Gaps:**
- Não há porto para serviços externos além de repositórios (ex.: porto para envio de notificações, eventos, etc.)
- `SimulationEngine` como `object` acoplado ao `domain` — sem interface, não pode ser substituído por mock em testes de use case

---

### Domain-Driven Design — 8.0/10

**Pontos fortes:**
- Entidades ricas com factory methods (`Board.create`, `Card.create`, `WorkItem.create`)
- Value objects tipados com `@JvmInline` (`BoardId`, `ScenarioId`, `SimulationDay`, etc.)
- Erros de domínio como `sealed class DomainError` — hierarquia expressiva
- `SimulationEngine` como Domain Service puro e determinístico
- State machine explícita em `WorkItem` (`advance()`, `block()`, `incrementAge()`)

**Gaps (GAP-H, GAP-I, GAP-J):**
- Sem Domain Events: mudanças de estado (`WorkItem` bloqueado, cenário executado) não emitem eventos — impossível reagir a eles sem polling ou acoplamento direto
- `Board` não é Aggregate Root verdadeiro: não protege invariantes sobre `Column` e `Card` (ex.: não valida posições únicas ao adicionar coluna); a responsabilidade está espalhada entre use cases
- `Tenant` é anêmico: apenas `id` + `name`, sem nenhuma lógica de domínio ou invariantes — candidato a Value Object ou enriquecimento
- Sem Context Map documentado: quando novos bounded contexts surgirem (Analytics, Billing), não há registro das relações e padrões de integração (GAP-T)

---

### Modularidade & Evoluibilidade Arquitetural — 7.5/10

Esta dimensão avalia o quanto a estrutura atual suporta o crescimento do projeto —
seja como monólito modular bem governado ou como ponto de partida para extração
incremental de microserviços.

**Pontos fortes:**
- **Domain-Oriented Monolith** implementado: 4 módulos Gradle organizados por responsabilidade (`domain`, `usecases`, `sql_persistence`, `http_api`), não por camada técnica avulsa
- **Domain Module API** parcialmente implementado: `usecases/repositories/` funciona como API pública do módulo — outras camadas dependem das interfaces, nunca das implementações JDBC
- Ports-and-adapters prontos para extração: a interface `BoardRepository` pode virar um cliente HTTP sem que `usecases/` ou `http_api/` precisem mudar
- `Either<DomainError, T>` compatível com chamada local e remota — o contrato de retorno não precisa mudar em uma extração
- Koin `AppModule` é o único ponto de wiring — trocar `JdbcBoardRepository` por `HttpBoardRepository` é uma linha

**Gaps (GAP-R, GAP-S, GAP-T):**
- **GAP-R — Domain API Build Module ausente**: API e implementação do módulo `usecases/` vivem no mesmo artefato Gradle — `http_api` compila contra a implementação, não só a API. Quando o módulo crescer, mudanças de implementação recompilam todos os clientes desnecessariamente
- **GAP-S — Sem enforcement de boundary no Gradle**: nada impede hoje que `http_api/build.gradle.kts` adicione `implementation(project(":sql_persistence"))` e importe `JdbcBoardRepository` diretamente, violando a Dependency Rule. A boundary existe por convenção, não por constraint técnica
- **GAP-T — Sem Context Map nem documentação de Bounded Contexts**: a medida que novos domínios emergirem (Analytics, Tenant Management, integração com Jira), não há documento que registre as relações entre eles (Customer-Supplier, ACL, Shared Kernel)
- `SimulationEngine` como `object` singleton sem interface — não pode ser extraído para serviço separado sem refatoração prévia (ver GAP-P)

---

### SOLID Principles — 9.0/10

**Pontos fortes:**
- SRP: cada arquivo tem uma única responsabilidade (extração de `ScenarioDtos.kt` no Ciclo 5 evidencia isso)
- OCP: novos comportamentos adicionam implementações, não modificam interfaces
- ISP: repositórios segregados por entidade (`BoardRepository`, `CardRepository`, etc.)
- DIP: use cases dependem de interfaces (portos), nunca de `Jdbc*Repository` diretamente

**Gaps:**
- `SimulationEngine` como `object` singleton viola DIP parcialmente: não pode ser injetado como abstração

---

### Testes & Qualidade — 8.0/10

**Pontos fortes:**
- 95% de cobertura de instruções JaCoCo (gate automatizado)
- Testes de integração com PostgreSQL embarcado (zonky) — sem mocks para SQL
- Testes de comportamento do `SimulationEngine` com 45+ cenários, incluindo determinismo
- Testes de rota com `testApplication()` e MockK

**Gaps (GAP-K, GAP-L):**
- Sem testes de contrato (consumer-driven): se a API mudar, consumidores externos não são notificados automaticamente. Crítico se o projeto evoluir para microserviços — sem contract tests, a extração de um serviço não tem rede de segurança de compatibilidade
- Sem testes de mutação (PITest): cobertura de linha alta não garante qualidade de asserções; mutantes podem sobreviver
- Sem testes de performance/carga: nenhum benchmark para `SimulationEngine.runDay()` com volumes grandes
- `IntegrationTestSetup` usa `Dispatchers.IO` implícito sem `@TestCoroutineScheduler` — testes lentos por design

---

### Kotlin Quality Pipeline — 9.0/10

**Pontos fortes:**
- Convention plugin `kanban.kotlin-common.gradle.kts` — DRY total, zero duplicação entre módulos
- Detekt com `warningsAsErrors = true` e limiares agressivos (cyclomatic ≤ 10, LargeClass ≤ 200 linhas)
- KtLint 1.5.0 no estilo oficial Kotlin
- JaCoCo com exclusões cirúrgicas para código gerado (state-machine de suspensão, serializers)
- CI/CD roda `testAll` em todo PR com upload de artefatos

**Gaps:**
- Sem testes de mutação no pipeline
- Versões de dependências declaradas inline por módulo (sem `libs.versions.toml`) — aumenta risco de divergência entre módulos em projetos maiores. Relevante especialmente se novos módulos Gradle forem adicionados para novos bounded contexts

---

### Observabilidade — 7.0/10

**Estado atual no código:**

```
Observability.kt
  ├── RequestIdPlugin  → gera/propaga X-Request-ID; injeta no MDC como "requestId"
  └── CallLogging      → loga método, path e status HTTP com MDC

logback.xml
  └── Appender STDOUT (texto): "%d{HH:mm:ss} [%thread] %-5level [rid=%X{requestId}] - %msg%n"
      Sem JSON. Sem traceId. Sem spanId. Formato não parseável por Loki/CloudWatch.
```

**Pontos fortes:**
- `requestId` propagado do header `X-Request-ID` ou gerado, injetado no MDC e retornado na resposta — base sólida para correlação
- `requestId` presente em todas as respostas de erro (corrigido no Ciclo 5)
- SLF4J + Logback configurado; pronto para receber appender JSON sem alterar o código de negócio

**Gaps:**

- **GAP-F — Logs em texto, não JSON**: `logback.xml` usa pattern de texto livre. Impossível filtrar por campo (`level`, `requestId`, `traceId`) em Loki, CloudWatch ou Datadog sem regex frágil. Correção: `logstash-logback-encoder` com appender condicional (`LOG_FORMAT=json` em produção, texto em dev)

- **GAP-B — Health check sem verificação de dependências**: `GET /health` retorna sempre `200 OK` sem checar PostgreSQL — falso positivo quando o banco está fora. Dois endpoints são necessários seguindo a convenção Kubernetes: `/health/live` (liveness — o processo respira?) e `/health/ready` (readiness — o serviço está apto para tráfego, incluindo DB check?)

- **GAP-D — Sem métricas de aplicação**: nenhum endpoint `/metrics` para scraping do Prometheus. Sem métricas, alertas e dashboards são impossíveis. Abordagem: plugin `ktor-server-metrics-micrometer` (instrumenta todas as rotas HTTP automaticamente) + `micrometer-registry-prometheus` + `DomainMetrics` para métricas de negócio (`kanban.simulation.days.executed.total`, `kanban.scenario.wip.current`). Labels devem ser de **baixa cardinalidade** — nunca usar UUIDs ou `requestId` como label

- **GAP-U — Sem alertas configurados**: mesmo após adicionar métricas, nenhum alerta está definido para condições críticas: taxa de erros HTTP > 5%, latência P95 > 2s, pool HikariCP > 90%, serviço fora do ar. Sem alertas, problemas em produção só são detectados por usuários ou por monitoramento manual

- **GAP-O — Sem distributed tracing (OpenTelemetry)**: `requestId` é um identificador local; não é um `traceId` W3C propagável entre serviços. O OTel Java Agent resolve isto com zero alteração de código: instrumenta automaticamente Ktor (Netty), JDBC, HikariCP e faz bridge com o Logback — injetando `traceId` e `spanId` no MDC automaticamente. Valor imediato mesmo no monólito: spans de JDBC mostram qual query está lenta sem adicionar logs manuais. Depende do `GAP-G` (Docker) para configuração via variáveis de ambiente no container

---

### Prontidão para Produção — 5.5/10

Esta é a dimensão com maior déficit. O projeto tem excelente qualidade de código, mas
carece de todos os aspectos operacionais necessários para um deploy seguro.

**Pontos fortes:**
- Queries parametrizadas (sem SQL injection)
- HikariCP com parâmetros de produção (timeout, max lifetime, leak detection)
- `requestId` para rastreabilidade

**Gaps (GAP-A, GAP-C, GAP-E, GAP-G, GAP-V):**
- **GAP-A — Autenticação/Autorização**: zero mecanismo de autenticação (JWT, OAuth2, API Key) — qualquer pessoa com acesso à rede pode criar/deletar dados
- **GAP-C — Graceful Shutdown**: `embeddedServer(Netty)` sem hook de encerramento — conexões abertas podem ser abortadas no deploy
- **GAP-E — Rate Limiting**: sem throttling por cliente/IP — vulnerável a abuso e DoS acidental
- **GAP-G — Containerização e Orquestração**: sem `Dockerfile`, `docker-compose.yml` ou manifestos Kubernetes — deploy manual e não reproduzível. Escopo completo: `Dockerfile` multi-stage (`eclipse-temurin:21-jdk` builder → `eclipse-temurin:21-jre` runtime) com OTel Java Agent embutido; `docker-compose.yml` (dev: app + PostgreSQL com health checks); manifestos Kubernetes completos: `Namespace`, `ConfigMap`, `Secret` (template), `Deployment` (com `startupProbe`, `livenessProbe`, `readinessProbe`, `resources.requests/limits`, `RollingUpdate`, `securityContext`), `Service`, `Ingress`, `HorizontalPodAutoscaler`, `PodDisruptionBudget`
- **GAP-V — Pipeline CI/CD sem build e deploy de imagem**: o CI atual (`testAll`) apenas executa testes e quality gates — não constrói imagem Docker, não faz push para registry, não aciona deployment no cluster. A cadeia completa de CI/CD de uma aplicação containerizada inclui: build da imagem, scan de vulnerabilidades, push para registry (GHCR/ECR), atualização de manifesto K8s e apply/rollout no cluster
- Sem configuração de CORS
- Sem limite de tamanho de requisição (payload arbitrariamente grande aceito)
- Sem estratégia documentada de secrets (variáveis de ambiente, Vault, etc.)
- Sem circuit breaker para resiliência de banco de dados

---

### Governança de Mudanças Evolutivas — 5.5/10

Esta dimensão avalia se o projeto tem protocolo explícito para executar seus próprios gaps
de forma **normativa e incremental** — evitando crises estruturais, J-curves profundas e
esgotamento de contexto em sessões LLM.

A distinção fundamental (Anderson, *Kanban*): mudanças **normativas** (novos endpoints,
extrair interface, adicionar appender) não geram resistência e não invocam crise. Mudanças
**estruturais** (reorganizar todos os pacotes, trocar framework, alterar contrato de API
pública) geram resistência e exigem planejamento cuidadoso. O PMI (2014) reporta que apenas
**18% das grandes iniciativas de mudança entregam o resultado esperado** — o antídoto é
executar muitos J-curves pequenos em vez de um grande.

**Pontos fortes:**
- ADR-0004 existe: 20 gaps documentados, priorizados e rastreáveis em git
- Ciclos de execução definidos (Hardening, Operações, Domínio, Excelência) com dependências entre gaps
- Convention plugin centraliza qualidade — gate automático impede regressões durante mudanças

**Gaps (GAP-W):**
- **GAP-W — Sem governança formal de execução**: os 20 gaps do ADR-0004 não têm:
  (1) classificação explícita de **normativo vs estrutural** — sem isso, sessões LLM tendem a executar gaps estruturais sem ADR prévia;
  (2) **protocolo 1-gap-por-sessão** documentado no projeto — o esgotamento de contexto em sessões longas é a principal causa de inconsistências de código (violações Detekt não percebidas, testes esquecidos, padrões `Either` ignorados);
  (3) **J-curve tolerances** formalizadas: os limites de Safety (CI verde) e Patience (PR revisado em < 48h) existem implicitamente mas não são referenciados ao planejar execuções;
  (4) gaps estruturais (GAP-A, GAP-H, GAP-K, GAP-R) sem ADR dedicada aprovada antes da execução

---



**Pontos fortes:**
- Spec gerada automaticamente do código (sem drift entre código e documentação)
- Swagger UI disponível em `/swagger`
- Tags, summaries e descriptions presentes nas rotas principais
- DTOs de request/response documentados com tipos

**Gaps (GAP-N):**
- Alguns códigos de resposta de erro ausentes nas specs (ex.: `409 Conflict` para `DayAlreadyExecuted` em `POST .../run`)
- Sem exemplos de request/response body
- Sem schema de autenticação (quando for implementado)
- Headers de resposta não documentados (`X-Request-ID` retornado, mas não especificado)
- Sem estratégia de versionamento de API documentada (atualmente `v1` hardcoded nas rotas) — relevante ao planejar extração de microserviços com versioning independente por serviço

---

### Design de Banco de Dados — 8.0/10

**Pontos fortes:**
- Flyway para migrações versionadas com validação de checksums
- Índices em chaves estrangeiras (V2)
- CHECK constraints para invariantes de dados (`wip_limit > 0`, `team_size > 0`)
- REPEATABLE_READ isolation level
- `baseline_on_migrate = true` para bancos pré-existentes

**Gaps (GAP-M):**
- `simulation_states` e `daily_snapshots` armazenados como JSON blob — sem queryabilidade (impossível filtrar por campo interno sem jsonb operators). Este gap também bloqueia a extração do `SimulationEngine` para microserviço independente: um serviço separado precisaria de schema próprio e o blob impede migração limpa
- Sem paginação em endpoints de lista (`GET /columns?boardId=...` retorna todos os itens)
- Sem soft delete ou audit trail (`deleted_at`, `created_by`, `updated_at`)
- Sem estratégia de particionamento para `daily_snapshots` (crescimento ilimitado por cenário)
- Banco compartilhado por todos os módulos sem boundaries de schema: não há separação entre schema do módulo Kanban e schema do módulo de simulação — dificulta eventual *Database per Service* se um bounded context for extraído

---

### Tratamento de Erros — 9.0/10

**Pontos fortes:**
- `Either<DomainError, T>` em todas as operações falíveis
- `sealed class DomainError` com matching exaustivo nas rotas
- `respondWithDomainError()` unificado (corrigido no Ciclo 5)
- `StatusPages` para erros não tratados

**Gaps:**
- `PersistenceError` não loga o erro original antes de retornar "Internal server error" — dificuldade de diagnóstico sem correlação de logs com stack trace

---

## Gaps Priorizados

> **Legenda de Tipo:** `N` = Normativa (execute diretamente, 1 sessão LLM) | `M` = Médio impacto (1 ADR interna + 1 PR focado) | `E` = Estrutural (requer ADR dedicada aprovada **antes** de qualquer código)

### Prioridade 1 — Crítico (Bloqueador de Produção)

| ID    | Tipo | Gap                              | Dimensão              | Esforço Estimado |
|-------|------|----------------------------------|-----------------------|------------------|
| GAP-A | E    | Autenticação/Autorização (JWT)   | Prontidão Produção    | Alto             |
| GAP-B | N    | Health Check real (DB + deps)    | Observabilidade       | Baixo            |
| GAP-C | N    | Graceful Shutdown                | Prontidão Produção    | Baixo            |

### Prioridade 2 — Alta (Operações em Produção)

| ID    | Tipo | Gap                                | Dimensão              | Esforço Estimado |
|-------|------|------------------------------------|------------------------|------------------|
| GAP-F | N    | Logging estruturado (JSON)         | Observabilidade        | Baixo            |
| GAP-D | M    | Métricas (Micrometer/Prometheus)   | Observabilidade        | Médio            |
| GAP-E | M    | Rate Limiting                      | Prontidão Produção     | Baixo            |
| GAP-G | M    | Containerização + K8s manifests    | Prontidão Produção     | Médio            |
| GAP-U | M    | Alertas (Grafana / Alertmanager)   | Observabilidade        | Médio            |
| GAP-V | E    | Pipeline CI/CD (build+push+deploy) | CI/CD                  | Médio            |

### Prioridade 3 — Média (Qualidade de Domínio, Tracing e Evolução Arquitetural)

| ID    | Tipo | Gap                                           | Dimensão                          | Esforço Estimado |
|-------|------|-----------------------------------------------|-----------------------------------|------------------|
| GAP-W | N    | Governança de execução de gaps (J-curve)      | Governança de Mudanças Evolutivas | Baixo            |
| GAP-O | E    | OTel Java Agent (tracing + log bridge)        | Observabilidade                   | Médio            |
| GAP-P | N    | SimulationEngine como interface               | Clean Arch / Modularidade         | Baixo            |
| GAP-Q | N    | Logging do PersistenceError real              | Tratamento de Erros               | Baixo            |
| GAP-S | M    | Enforcement de boundary no Gradle             | Modularidade & Evoluibilidade     | Baixo            |
| GAP-I | M    | Board como Aggregate Root real                | DDD                               | Médio            |
| GAP-J | M    | Paginação em endpoints de lista               | DB / API                          | Médio            |
| GAP-H | E    | Domain Events                                 | DDD                               | Alto             |
| GAP-K | E    | Testes de contrato (Pact)                     | Testes                            | Alto             |

### Prioridade 4 — Baixa (Excelência Técnica e Futuro Arquitetural)

| ID    | Tipo | Gap                                       | Dimensão                      | Esforço Estimado |
|-------|------|-------------------------------------------|-------------------------------|------------------|
| GAP-L | M    | Testes de mutação (PITest)                | Kotlin Pipeline               | Médio            |
| GAP-M | E    | JSON blob + schema boundaries no DB       | DB Design / Modularidade      | Médio            |
| GAP-N | N    | Exemplos e security no OpenAPI            | OpenAPI                       | Baixo            |
| GAP-R | E    | Domain API Build Module (usecases-api/)   | Modularidade & Evoluibilidade | Médio            |
| GAP-T | N    | Context Map e documentação de BCs         | DDD / Modularidade            | Baixo            |

---

## Ordem de Execução Sugerida

Os gaps de **Prioridade 1** devem ser endereçados juntos num único ciclo de hardening
antes de qualquer deploy. Os de **Prioridade 2** constituem um segundo ciclo de
operacionalização. Os de **Prioridade 3** e **4** são melhorias contínuas sem urgência.

```
Ciclo Hardening  (P1):  GAP-B → GAP-C → GAP-A
Ciclo Operações  (P2):  GAP-F → GAP-B(ready) → GAP-D → GAP-E → GAP-G → GAP-V → GAP-U
Ciclo Domínio    (P3):  GAP-W → GAP-O → GAP-P → GAP-Q → GAP-S → GAP-I → GAP-J → GAP-H → GAP-K
Ciclo Excelência (P4):  GAP-T → GAP-N → GAP-R → GAP-L → GAP-M
```

> **Protocolo de execução (skill `evolutionary-change`):** cada gap deve ser executado em
> uma **sessão LLM separada** — recarregando CLAUDE.md e os arquivos alvo no início de cada
> sessão. Gaps **normativos (N)** podem ser executados diretamente com 1 sessão + 1 PR.
> Gaps **médio impacto (M)** requerem discussão de design na sessão antes do código.
> Gaps **estruturais (E)** — GAP-A, GAP-V, GAP-O, GAP-H, GAP-K, GAP-R, GAP-M —
> exigem ADR dedicada aprovada **antes** de qualquer código ser escrito (ver seção ADRs de Execução).
> Safety: CI verde (`./gradlew testAll`). Patience: PR revisado em < 48h.

**Justificativa das ordens:**

- **Ciclo Hardening**: `GAP-B` (health check `/ready` com DB check) e `GAP-C` (graceful shutdown) são normativas de baixo esforço — executar em sessões separadas; `GAP-A` (autenticação) é estrutural, exige ADR-0005 aprovada antes da execução
- **Ciclo Operações**: `GAP-F` (logs JSON) é a base — `traceId` e `spanId` só aparecem nos logs se o formato for JSON; `GAP-D` (métricas) antes de `GAP-U` (alertas) porque alertas dependem de métricas; `GAP-G` (containerização) deve incluir o OTel Java Agent no `Dockerfile` e todos os manifestos K8s, preparando `GAP-O` e `GAP-V`; `GAP-V` (pipeline CI/CD) depende de `GAP-G` — sem imagem não há o que construir/publicar; `GAP-U` (alertas) ao final do ciclo, quando métricas e infra containerizada já existem
- **Ciclo Domínio**: `GAP-W` primeiro — define o protocolo antes de executar os demais; `GAP-O` (OTel Java Agent) entrega valor imediato no monólito (spans JDBC, spans HTTP, `traceId` nos logs) e **depende apenas do `GAP-G`** (Docker) ser feito; `GAP-P` (`SimulationEngine` como interface) desbloqueia spans manuais de alto valor no `RunDayUseCase`; `GAP-H` (Domain Events) é estrutural — depende de `GAP-P` e de ADR dedicada; `GAP-K` (contract tests) é estrutural — depende de `GAP-G` para rodar o broker Pact
- **Ciclo Excelência**: `GAP-T` (Context Map) primeiro — documenta o futuro antes de implementá-lo; `GAP-R` (Domain API Build Module) é estrutural — ADR dedicada antes de executar; só se justifica quando build time > 2min

---

## ADRs de Execução Planejadas (Gaps Estruturais)

Os 7 gaps do tipo `E` não podem ser executados sem uma ADR dedicada aprovada antes do código.
Cada ADR abaixo é uma sessão LLM independente — seguindo o protocolo 1-gap-por-sessão.

| ADR       | Gap   | Título                               | Pré-requisitos       | Status    |
|-----------|-------|--------------------------------------|----------------------|-----------|
| ADR-0005  | GAP-A | Autenticação e Autorização (JWT/OAuth2) | —                 | Pendente  |
| ADR-0006  | GAP-V | Pipeline CI/CD (build + push + deploy) | GAP-G concluído   | Pendente  |
| ADR-0007  | GAP-O | OpenTelemetry Agent Integration      | GAP-G concluído      | Pendente  |
| ADR-0008  | GAP-H | Domain Events                        | GAP-P concluído      | Pendente  |
| ADR-0009  | GAP-K | Contract Tests com Pact              | GAP-G concluído      | Pendente  |
| ADR-0010  | GAP-R | Domain API Build Module (usecases-api/) | build time > 2min | Pendente  |
| ADR-0011  | GAP-M | Schema Boundaries e JSON blob no DB  | —                    | Pendente  |

> **Como usar esta tabela:** antes de executar qualquer gap `E`, abra uma sessão LLM
> dedicada para escrever a ADR correspondente. Só após a ADR estar merged inicie a
> sessão de implementação. A coluna "Pré-requisitos" indica dependências técnicas — não
> confundir com a ordem da ADR, que pode ser escrita independentemente.

---

## Decisão

Esta ADR não decide uma implementação específica — ela registra o diagnóstico e prioriza
os gaps para que ADRs futuras (ADR-0005 a ADR-0011) possam ser escritas com contexto
completo. Cada gap de Prioridade 1 e 2 deve ter uma ADR dedicada antes de execução.

O projeto é um **Monólito Modular bem estruturado** e deve permanecer assim enquanto:
as boundaries de domínio ainda estão sendo descobertas, o time é pequeno, e nenhum
bounded context apresenta necessidade de scaling ou ownership independente. A extração
para microserviços é um caminho viável no futuro — e os gaps de Prioridade 3 e 4 da
dimensão Modularidade preparam esse caminho sem antecipar complexidade desnecessária.

---

## Consequências

**Positivas:**
- Backlog de qualidade explícito, priorizado e rastreável em git — agora com 20 gaps categorizados em 12 dimensões
- Onboarding de novos colaboradores: mapa claro do que está bom, do que falta e do caminho evolutivo
- Ciclos futuros de melhoria podem referenciar esta ADR como baseline
- A dimensão de Modularidade & Evoluibilidade torna explícita a estratégia de crescimento arquitetural
- O caminho incremental de observabilidade (5 passos) permite entregar valor imediato em cada passo sem esperar o stack completo
- `GAP-O` reposicionado de P4 para P3: o OTel Java Agent entrega spans HTTP + JDBC + correlação de logs no monólito atual, sem aguardar extração de microserviços
- A dimensão de Governança de Mudanças Evolutivas torna explícito o **protocolo de execução dos próprios gaps**: 1 sessão LLM por gap, ADR antes de gaps estruturais, J-curve tolerances definidas — reduz risco de regressões e de projetos de melhoria que falham antes de completar o J-curve

**Negativas:**
- 20 gaps documentados podem gerar sensação de "projeto incompleto" — contexto: o score
  de 7.8/10 indica projeto saudável; os gaps são em sua maioria operacionais e de
  evoluibilidade futura, não de correção de problemas presentes
- Score geral revisado de 8.0 para 7.8 pela adição da dimensão Governança (nota 5.5) — reflexo honesto do estado atual, não deterioração do projeto

**Neutras:**
- Esta ADR não altera nenhum código, teste ou configuração

---

## Plano de Implementação (por gap)

> Cada linha é **1 sessão LLM + 1 PR**. `[N]` = executar diretamente. `[M]` = discutir design na sessão antes do código. `[E→ADR-XXXX]` = escrever e aprovar a ADR indicada **antes** de iniciar a sessão de implementação.

**Ciclo Hardening (P1): ✅ CONCLUÍDO**
- [x] `[N]` **GAP-B** — `/health/live` + `/health/ready` com DB check → PR #58
- [x] `[N]` **GAP-C** — Graceful shutdown em `Main.kt` → PR #58
- [x] `[E→ADR-0005]` **GAP-A** — JWT Bearer authentication (ADR-0005) → PR #57

**Ciclo Operações (P2): ✅ CONCLUÍDO**
- [x] `[N]` **GAP-F** — Logging estruturado JSON (`logstash-logback-encoder`) → PR #59
- [x] `[M]` **GAP-D** — Métricas Micrometer/Prometheus + `DomainMetrics` → PR #60
- [x] `[M]` **GAP-E** — Rate limiting global (`ktor-server-rate-limit`) → PR #61
- [x] `[M]` **GAP-G** — Dockerfile multi-stage + docker-compose + K8s manifests → PR #62
- [x] `[E→ADR-0008]` **GAP-V** — CI/CD pipeline build+push Docker (ADR-0008) → PR #63
- [x] `[M]` **GAP-U** — Alertas Prometheus + Dashboard Grafana → PR #64

**Ciclo Domínio (P3):**
- [ ] `[N]` **GAP-W** *(parcialmente concluído nesta revisão)* — Criar `docs/gap-execution-protocol.md` com J-curve tolerances explícitas e checklist de execução pré/pós gap. Adicionar `evolutionary-change` no protocolo do `CLAUDE.md` com referência a este arquivo
- [ ] `[E→ADR-0007]` **GAP-O** — Escrever ADR-0007 (decisão: configuração do OTel Agent, exporters, sampling). Só após aprovação e GAP-G concluído: configurar variáveis OTel no `Dockerfile`/`docker-compose`, verificar spans HTTP/JDBC, adicionar span manual no `RunDayUseCase`
- [ ] `[N]` **GAP-P** — Extrair interface `SimulationEnginePort` em `usecases/`, implementada por `SimulationEngine` (habilita mock em testes de use case e span manual via injeção)
- [ ] `[N]` **GAP-Q** — Logar stack trace do erro original em `JdbcBoardRepository` (e demais) com `log.error("Persistence error", e)` antes de mapear para `DomainError.PersistenceError`
- [ ] `[M]` **GAP-S** — Adicionar custom Detekt rule ou Gradle constraint impedindo import direto de `JdbcBoardRepository` fora do `AppModule`
- [ ] `[M]` **GAP-I** — Mover validações de invariantes de `Column`/`Card` para `Board.addColumn()` / `Board.addCard()`
- [ ] `[M]` **GAP-J** — Adicionar parâmetros `page` e `size` nos endpoints de lista
- [ ] `[E→ADR-0008]` **GAP-H** — Escrever ADR-0008 (modelo de Domain Events e mecanismo de publicação). Requer GAP-P concluído. Só após aprovação: implementar eventos e listeners
- [ ] `[E→ADR-0009]` **GAP-K** — Escrever ADR-0009 (estratégia de contract testing com Pact). Requer GAP-G concluído. Só após aprovação: configurar Pact broker e escrever consumer tests

**Ciclo Excelência (P4):**
- [ ] `[N]` **GAP-T** — Criar `docs/context-map.md` documentando bounded contexts atuais e candidatos a extração (Analytics, Tenant Management) com padrões de integração (ACL, Customer-Supplier)
- [ ] `[N]` **GAP-N** — Adicionar exemplos nos request bodies e documentar `X-Request-ID` nas respostas OpenAPI. Adicionar schema de autenticação quando `GAP-A` for implementado
- [ ] `[E→ADR-0010]` **GAP-R** — Escrever ADR-0010 apenas quando build time > 2min. Decisão: separação de `usecases-api/` e `usecases-impl/`. Só após aprovação: criar novos módulos Gradle
- [ ] `[M]` **GAP-L** — Integrar PITest no pipeline de qualidade (Gradle plugin)
- [ ] `[E→ADR-0011]` **GAP-M** — Escrever ADR-0011 (estratégia de schema boundaries e jsonb vs colunas). Só após aprovação: criar migração Flyway e atualizar queries

---

## Referências

- ADR-0002 — Ciclo 5 de qualidade (DTO consistência, estado anterior)
- ADR-0003 — Flyway migrations (contexto de evolução de schema)
- Skill: [adr](.claude/skills/adr/SKILL.md)
- Skill: [clean-architecture](.claude/skills/clean-architecture/SKILL.md)
- Skill: [ddd](.claude/skills/ddd/SKILL.md)
- Skill: [testing-and-observability](.claude/skills/testing-and-observability/SKILL.md)
- Skill: [kotlin-quality-pipeline](.claude/skills/kotlin-quality-pipeline/SKILL.md)
- Skill: [openapi-quality](.claude/skills/openapi-quality/SKILL.md)
- Skill: [definition-of-done](.claude/skills/definition-of-done/SKILL.md)
- Skill: [microservices-modular-monolith](.claude/skills/microservices-modular-monolith/SKILL.md)
- Skill: [opentelemetry](.claude/skills/opentelemetry/SKILL.md)
- Skill: [local-and-production-environment](.claude/skills/local-and-production-environment/SKILL.md)
- Skill: [evolutionary-change](.claude/skills/evolutionary-change/SKILL.md)
- Richardson, Chris. *Modular Monolith Patterns for Fast Flow*. https://microservices.io/post/architecture/2024/09/09/modular-monolith-patterns-for-fast-flow.html
- Fowler, Martin. *MonolithFirst*. https://martinfowler.com/bliki/MonolithFirst.html
- OpenTelemetry. *Documentation*. https://opentelemetry.io/docs/
- Prometheus. *Overview*. https://prometheus.io/docs/introduction/overview/
- Grafana. *Documentation*. https://grafana.com/docs/
- Docker. *Documentation*. https://docs.docker.com/
- Podman. *Documentation*. https://podman.io/docs
- Kubernetes. *Documentation*. https://kubernetes.io/pt-br/docs/home/
- Minikube. *Get Started*. https://minikube.sigs.k8s.io/docs/start/
- Anderson, David. *Kanban: Successful Evolutionary Change for Your Technology Business*. Blue Hole Press, 2010
- de la Sablonnière, Roxane. "Toward a Psychology of Social Change: A Typology of Social Change." *Frontiers in Social Psychology*, 2017. https://doi.org/10.3389/fpsyg.2017.00397
- PMI. *Organizational Change Management*. 2014. https://www.pmi.org/learning/thought-leadership/pulse/organizational-change-management
- Senge, Peter. *The Fifth Discipline: The Art & Practice of the Learning Organization*
- Fowler, Martin. *Strangler Fig Application*. https://martinfowler.com/bliki/StranglerFigApplication.html
