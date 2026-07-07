---
status: accepted
date: 2026-07-07
decision-makers: "@agnaldo4j"
---

# ADR-0030 — Estratégia GraalVM para produção, JVM para desenvolvimento e testes

## Context and Problem Statement

O runtime de produção atual é o fat JAR Ktor/Netty (`:http_api:buildFatJar`) sobre
`eclipse-temurin:25-jre-alpine`, com o OTel Java Agent no `ENTRYPOINT` (ADR-0009). Dev e testes
usam Temurin 25 via `.sdkmanrc` e toolchain 25 (ADR-0024). Não há nenhuma menção a GraalVM no
projeto.

GraalVM oferece dois modos com perfis de risco muito diferentes:

| Modo | Ganho | Risco |
|---|---|---|
| GraalVM JDK (Graal JIT) | Throughput igual ou melhor que o C2; drop-in no fat JAR | Baixo — troca de imagem de runtime |
| Native Image (AOT) | Startup ~100x mais rápido, fração da memória, container mínimo | Alto — closed-world: reachability metadata para Netty/Koin/JDBC/Flyway e o OTel javaagent é **incompatível** |

**Pergunta a decidir**: adotar GraalVM em produção? Em qual modo? E como fazê-lo sem violar a
política de mudança evolutiva (gaps pequenos e reversíveis, board #6)?

## Decision Drivers

- Startup e footprint de memória importam em produção/K8s (densidade, HPA, cold start) — mas o
  p95 e o throughput do baseline k6 (ADR-0027) não podem regredir sem medição comparativa.
- Observabilidade é inegociável (ADR-0009): o OTel javaagent instrumenta bytecode em runtime e
  **não existe** no mundo fechado do Native Image — qualquer caminho AOT precisa resolver isso antes.
- Dev e testes devem permanecer simples: Temurin 25, `.sdkmanrc` e `testAll` intocados.
- Política de mudança evolutiva (`/evolutionary-change`): nada de big-bang de runtime; cada passo
  é um gap pequeno, medível e reversível.
- A direção precisa ficar registrada e acionável (skill), não implícita em conversas.

## Considered Options

1. Manter Temurin JVM puro (status quo).
2. GraalVM JDK (Graal JIT) em produção — trocar apenas a imagem de runtime.
3. Native Image (AOT) direto.
4. Estratégia **GraalVM-prod / JVM-dev** documentada em skill, com caminho evolutivo
   JIT → Native Image executado em gaps futuros.

## Decision Outcome

**Escolhida: Opção 4**, porque registra a direção arquitetural uma única vez sem executar nada
neste ciclo: a Fase 1 (GraalVM JDK no estágio runtime do Dockerfile) vira um gap de baixo risco,
e a Fase 2 (Native Image) só entra quando seus pré-requisitos — observabilidade sem javaagent e
reachability metadata do stack — forem resolvidos em gaps próprios. **Nenhuma mudança de build
nesta entrega.** O guia operacional (modos, distribuições, fases, metadata, pitfalls) viverá na
skill `/graalvm` (`.claude/skills/graalvm/SKILL.md`), **entregue no PR 2/2 do GAP-AX** — este
PR (1/2) contém apenas esta ADR.

### Confirmation

- **Neste PR (1/2)**: diff só de docs e `./gradlew testAll`/CI verdes — a prova de que nada
  mudou de runtime.
- **Ao final do GAP-AX (PR 2/2)**: existência de `.claude/skills/graalvm/SKILL.md` + linha
  `/graalvm` na tabela Skills do `CLAUDE.md`.
- **Gaps futuros de Fase 1/2**: confirmações executáveis próprias — smoke da imagem + baseline
  k6 comparativo (formato da ADR-0027).

## Consequences

- Bom: a direção fica decidida — sessões futuras não re-debatem GraalVM do zero, puxam o gap.
- Bom: dev/test permanecem em Temurin — zero atrito imediato; produção só muda com card no board.
- Ruim: nenhum ganho de runtime até os gaps de execução serem puxados — intencional.
- Ruim: a skill pode envelhecer (versões/compatibilidades mudam). Mitigação: a skill não crava
  versões — usa comandos de descoberta e manda consultar as docs oficiais no momento do gap.
- Nota: a Fase 2 **supersede parte da ADR-0009** (o javaagent sai do ENTRYPOINT); pela
  ADR-0023 (imutabilidade), isso exigirá uma nova ADR quando o gap for puxado.

## Pros and Cons of the Options

### Opção 1 — Status quo Temurin

- Bom: zero esforço, zero risco.
- Ruim: desperdiça ganho de startup/memória disponível e deixa a decisão implícita para sempre.

### Opção 2 — GraalVM JDK (JIT) em produção

- Bom: drop-in — o fat JAR e o javaagent continuam funcionando; Graal JIT ≥ C2.
- Ruim: ganho modesto; executada sozinha, sem estratégia registrada, vira mudança órfã de contexto.

### Opção 3 — Native Image direto

- Bom: ganho máximo (startup, memória, superfície de ataque menor).
- Ruim: risco máximo num único salto — OTel quebra, Netty/Koin/JDBC/Flyway exigem metadata,
  viola a política de mudança evolutiva.

### Opção 4 — Estratégia documentada + fases em gaps futuros

- Bom: alinha com `/evolutionary-change`; conhecimento versionado (skill); fases reversíveis e
  medidas contra o baseline.
- Ruim: esta entrega não muda runtime nenhum (intencional — o valor é a decisão registrada).

## More Information

- Branch: `feat/adr-0030-graalvm-estrategia-producao` · PR: 1/2 do GAP-AX
- Item no board #6: [GAP-AX](https://github.com/users/agnaldo4j/projects/6) — o plano de execução
  das fases vive lá, não aqui (ADR-0023)
- Referências: https://www.graalvm.org/latest/introduction/, ADR-0009 (OTel agent),
  ADR-0024 (toolchain Java 25), ADR-0027 (baseline k6 que mede as fases), ADR-0023 (imutabilidade)
