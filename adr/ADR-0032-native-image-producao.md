---
status: accepted
date: 2026-07-08
decision-makers: "@agnaldo4j"
---

# ADR-0032 — Native Image como artefato de produção (Fase 2 da ADR-0030)

> Conclui o roadmap da ADR-0030: o binário nativo (AOT) **substitui** o fat JAR como artefato
> de produção. Supersede a parte de runtime da Fase 1 (o estágio GraalVM JDK/JIT sai junto
> com o fat JAR de produção). Dev, testes e todos os gates de qualidade permanecem na JVM
> Temurin 25 — inalterados.

## Context and Problem Statement

Produção hoje: fat JAR sobre Oracle GraalVM JDK 25 em modo JIT (Fase 1, GAP-AY). Os
pré-requisitos da Fase 2 foram concluídos: observabilidade sem javaagent (ADR-0031, GAP-AZ)
e a capacidade opt-in de Native Image (GAP-BA, PR #247), cujo experimento provou o binário
funcional: **118,5 MB, startup 0,135s (~13x mais rápido que a JVM), jornada k6 completa com
100% dos checks (22 mil requests, 0 falhas)**, métricas Prometheus e spans JDBC operantes.

O experimento também delimitou os dois únicos gaps reais:

1. **Flyway não resolve migrations no native** (`ClassPathScanner: unsupported protocol:
   resource`) — em banco já migrado segue em frente; em **banco virgem falharia**. Exige
   resource-config dos `.sql` e validação do scanner.
2. **Spans HTTP do `KtorServerTelemetry` ausentes no native** — spans JDBC chegam ao Tempo
   como traces raiz, sem o pai HTTP (correlação quebrada).

Há ainda decisões de arquitetura pendentes: o Job k8s de migração roda `java -cp app.jar
MigrationMainKt` — não existe `java` num container de binário; e a estratégia de rollback.

**Pergunta a decidir**: adotar o binário nativo como artefato de produção? Com qual pipeline,
qual estratégia de migração de banco e qual rollback?

## Decision Drivers

- Startup 0,135s e footprint reduzido valem exatamente onde produção roda: K8s (startupProbe
  hoje tolera 60s; HPA e densidade se beneficiam; cold start deixa de existir na prática).
- Segurança: superfície menor — sem JIT, sem classloading dinâmico, código inalcançável
  excluído do binário (closed-world).
- A jornada de negócio completa já foi validada no binário (GAP-BA) — o risco residual está
  delimitado nos dois gaps conhecidos.
- Os gates existentes cobrem a troca: smoke do CI executa a imagem real (`/health/ready`) e
  o baseline k6 (ADR-0027) mede regressão de p95/throughput.
- Mudança evolutiva: implementação fatiada em PRs pequenos; a metadata de reachability
  (~3.8k linhas de JSON gerado por ferramenta) exigirá exceção justificada ao limite de
  400 linhas por PR.

## Considered Options

1. Status quo — fat JAR + GraalVM JIT (Fase 1 permanente).
2. Convivência de imagens — nativa como `latest` + tag `-jvm` de fallback por alguns ciclos.
3. Substituição completa com migrations **apenas** no startup (Job k8s removido).
4. **Substituição completa preservando o Job de migração** — o Job k8s passa a executar um
   segundo binário nativo compilado do `MigrationMainKt`.

## Decision Outcome

**Escolhida: Opção 4 (substituição completa preservando o Job de migração)**, com as
seguintes decisões integradas:

- **Pipeline**: os binários são compilados num estágio Docker com a imagem oficial
  `container-registry.oracle.com/graalvm/native-image:25` (via `:http_api:nativeCompile`,
  capacidade do GAP-BA); o estágio de runtime passa a uma base glibc mínima, mantendo
  uid/gid 1000 (securityContext do k8s) e o healthcheck do compose atendido (wget na imagem
  ou healthcheck ajustado — detalhe do PR de implementação).
- **Migrations**: no startup do app (chave `FLYWAY_ENABLED` mantida) para o caso comum das
  migrações aditivas, **e** o Job k8s `09-migration-job.yml` **permanece** para o
  procedimento em duas fases da ADR-0013 (migrações que mudam tipo de coluna, como a V2
  TEXT→JSONB): o comando do Job passa de `java -cp app.jar MigrationMainKt` para um
  **segundo binário nativo** (`graalvmNative` binaries) do mesmo `MigrationMainKt`.
  Nota de revisão: a primeira versão desta ADR removia o Job apoiada no lock do Flyway —
  o review apontou que o lock só serializa réplicas migrando; ele **não** protege pods
  antigos escrevendo num schema incompatível. A garantia de ordem pré-rollout é do Job.
- **Pré-condições de merge da implementação**: Gap 1 resolvido e comprovado (banco virgem
  migra no native — teste com volume zerado, cobrindo o app E o binário de migração); Gap 2
  resolvido OU aceito explicitamente com evidência e card de follow-up (traces JDBC e span
  manual continuam funcionais).
- **Metadata versionada** em `http_api/src/main/resources/META-INF/native-image/…`, com a
  exceção de tamanho justificada no PR (JSON gerado por ferramenta, não código revisável).
- **Rollback**: revert do(s) PR(s) de implementação — o pipeline JVM (`buildFatJar`)
  permanece funcional no Gradle para dev/testes e como resgate.

### Confirmation

- **PR 1/2 (esta ADR)**: diff só de docs; `./gradlew testAll` e CI verdes.
- **PRs de implementação**: smoke do CI verde executando a **imagem nativa**
  (`/health/ready`); migração comprovada em banco virgem; jornada k6 100% no container
  nativo; baseline comparativo (formato ADR-0027) com startup, memória e p95; tamanho da
  imagem documentado.
- Fim do roadmap ADR-0030 — não há fases posteriores.

## Consequences

- Bom: startup, memória e superfície de ataque — os ganhos que motivaram o roadmap inteiro.
- Bom: um único pipeline de imagem; o contrato operacional de migração (ADR-0013) preservado.
- Ruim: um segundo binário para manter (`kanban-vision-migrate` ou similar) — custo pequeno
  e localizado no bloco `graalvmNative`/Dockerfile.
- Ruim: job `build` do CI mais lento e pesado (~2min extras e ~7 GB de RSS de compilação no
  runner) — monitorar; quick build mode (`-Ob`) é opção se doer.
- Ruim: debugging/profiling limitado no native (JFR/heap dump parciais) — diagnóstico local
  continua disponível via fat JAR na JVM.
- Ruim: a metadata de reachability vira artefato versionado a manter — **regenerar via
  tracing agent (skill `/graalvm` §6, jornada k6 como exercitador) a cada mudança relevante
  de dependências**; metadata desatualizada falha em runtime, não em build (pitfall da skill).
- Nota: PITest, JaCoCo, Detekt e todos os testes seguem 100% na JVM Temurin — nenhum gate
  muda de runtime.

## Pros and Cons of the Options

### Opção 1 — Status quo (fat JAR + JIT)

- Bom: zero esforço; melhor throughput de pico (JIT).
- Ruim: desperdiça o roadmap concluído; startup/memória continuam pagando o preço da JVM.

### Opção 2 — Convivência de imagens (native + `-jvm`)

- Bom: rollback instantâneo por troca de tag.
- Ruim: dobra o tempo de build do CI e mantém dois pipelines vivos indefinidamente — a
  história mostra que o fallback "temporário" nunca é removido.

### Opção 3 — Substituição completa sem o Job (migrations só no startup)

- Bom: um pipeline; uma peça a menos no k8s.
- Ruim: **elimina o único caminho documentado de migração pré-rollout** (ADR-0013) — em
  migrações de tipo, pods antigos falhariam escrita durante o RollingUpdate sem nenhuma
  garantia de ordem. Rejeitada pelo review desta ADR.

### Opção 4 — Substituição completa preservando o Job (binário de migração)

- Bom: um pipeline de imagem; contrato ADR-0013 intacto; reversível por revert barato;
  `buildFatJar` segue disponível no Gradle.
- Ruim: segundo binário a manter; rollback exige um PR (minutos, não segundos) — aceitável.

## More Information

- Branch: `feat/adr-0032-native-image-producao` · PR: 1/2 do GAP-BB
- Item no board #6: [GAP-BB](https://github.com/users/agnaldo4j/projects/6) — o fatiamento
  da implementação vive lá, não aqui (ADR-0023)
- Referências: ADR-0030 (estratégia e fases), ADR-0031 (observabilidade sem javaagent),
  ADR-0013 (procedimento de migração em duas fases que o Job preserva), PR #247 (relatório
  do experimento GAP-BA), skill `/graalvm` (§4-§7), ADR-0027 (medição)
