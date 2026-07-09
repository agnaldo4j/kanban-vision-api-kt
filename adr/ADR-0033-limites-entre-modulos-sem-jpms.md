---
status: accepted
date: 2026-07-09
decision-makers: "@agnaldo4j"
---

# ADR-0033 — Limites entre módulos: Gradle `implementation` + Konsist (não adotar JPMS)

> Avaliamos usar o **Java Platform Module System (JPMS / `module-info.java`)** para tornar os
> contratos entre módulos explícitos e reforçar Screaming + Hexagonal Architecture. **Decisão:
> não adotar JPMS.** O custo/benefício é ruim para *este* stack (Kotlin 2.4 + Java 25 + **GraalVM
> Native Image** + frameworks reflection-heavy). Mantemos o enforcement atual — Gradle
> `implementation`/`api` + as fitness functions Konsist (ADR-0026) — e o reforçamos com uma nova
> regra Konsist de "pacote de contrato" (o equivalente barato ao `exports` do JPMS). Reavaliar
> apenas sob os gatilhos descritos em *Consequences*.

## Context and Problem Statement

Os limites entre os 4 módulos de produção (`domain`, `usecases`, `sql_persistence`, `http_api`) +
o módulo de teste `architecture` são hoje reforçados em **dois níveis**:

1. **Gradle** — visibilidade de dependência: só `usecases` usa `api(project(":domain"))` (re-exporta
   `domain` + o `Either` do Arrow); todo o resto usa `implementation` (encapsulado). `http_api` só
   vê `sql_persistence` para wiring de DI.
2. **Konsist** (ADR-0026) — 5 fitness functions que **falham o CI**: `HexagonalArchitectureTest`
   (dependency rule), `DomainPurityTest` (domain sem frameworks), `PortsPlacementTest` (ports em
   `usecases`, nunca em `domain`), `ContextBoundaryTest` (Kanban Management BC ↛ Simulation BC) e
   `ConventionsTest` (incl. `Jdbc*`/`Exposed*` importados só no `AppModule`).

A pergunta: **o JPMS fortaleceria esses contratos?** Ele adicionaria um terceiro nível —
encapsulação declarada em `module-info` (`exports`/`requires`), verificada pelo compilador e (na
JVM) pela runtime. E o padrão `provides/uses` (ServiceLoader) poderia expressar ports/adapters com
limites verificados pelo compilador.

## Decision Drivers

- Contratos de módulo **explícitos e verificados**, resistentes a refactors.
- Reforçar Screaming Architecture (superfície pública intencional) e Hexagonal (ports/adapters).
- **Restrição dura:** precisa conviver com **Kotlin 2.4**, **Java 25**, produção em **GraalVM Native
  Image** (ADR-0030/0032) e um stack reflection-heavy (Ktor, Koin, kotlinx.serialization, Exposed,
  HikariCP).
- **Não regredir** o enforcement Konsist já existente nem adicionar atrito de build/runtime.

## Considered Options

1. **JPMS completo** — `module-info.java` por módulo + ports/adapters via `provides/uses`
   (ServiceLoader), substituindo/complementando o Koin.
2. **JPMS parcial** — `module-info.java` só com `exports`/`requires` (sem services); manter o Koin.
3. **Status quo reforçado** — manter Gradle `implementation` + Konsist e adicionar uma **regra
   Konsist de "pacote de contrato"** que proíbe imports cross-module de pacotes não-contratuais
   (`*.internal`) — o equivalente barato ao `exports` do JPMS. **(escolhida)**

## Decision Outcome

**Opção escolhida: 3 — status quo reforçado. Não adotamos JPMS.**

O JPMS resolveria um problema que, neste projeto, **já está resolvido** por Gradle +
Konsist, ao custo de um mecanismo frágil e, na produção nativa, sem valor de runtime. A lacuna real
que o `exports` cobriria (esconder *tipos públicos* em pacotes de implementação — Kotlin não tem
package-private) é endereçada de forma mais barata por uma fitness function, no idioma que o time
já domina.

### Confirmation

- As 5 fitness functions Konsist (ADR-0026) já falham o CI em qualquer violação de fronteira; a
  visibilidade de dependência é garantida por Gradle `implementation`/`api`.
- O reforço desta decisão é uma **nova regra Konsist de pacote de contrato** (equivalente ao
  `exports`), rastreada como um gap `[M]` no board #6 (GAP-BS). A Confirmation desta ADR se cumpre
  quando essa regra estiver verde no `testAll`.

## Pros and Cons of the Options

### Opção 1 — JPMS completo (module-info + ServiceLoader)

- ✅ Limites de compilação declarativos; core sem dependência de framework de DI; ServiceLoader é
  AOT-friendly.
- ❌ **Kotlin não emite `module-info.class`** — só via `module-info.java` compilado por javac com
  `--patch-module` sobre a saída Kotlin (canal manual e frágil).
  [kotlinlang.org](https://kotlinlang.org/docs/gradle-configure-project.html)
- ❌ **`internal` do Kotlin ≠ `exports` do JPMS**, e Kotlin **não tem package-private**
  ([KT-29227](https://youtrack.jetbrains.com/issue/KT-29227), [KEEP #469](https://github.com/Kotlin/KEEP/discussions/469)).
- ❌ Falhas recorrentes de module graph com `kotlin.stdlib`
  ([Gradle #13978](https://github.com/gradle/gradle/issues/13978), [KTIJ-29390](https://youtrack.jetbrains.com/issue/KTIJ-29390)).
- ❌ ServiceLoader **duplica o Koin** com menos recursos (sem injeção por construtor, escopos,
  qualifiers) e força afrouxar a visibilidade Kotlin. Padrão válido em teoria
  ([TomCools/jpms-hexagonal-architecture](https://github.com/TomCools/jpms-hexagonal-architecture)),
  redundante aqui.

### Opção 2 — JPMS parcial (só exports/requires)

- ✅ `exports`/`requires` explícitos, sem mexer no DI.
- ❌ Herda todos os problemas Kotlin↔JPMS da Opção 1.
- ❌ **Grafo de dependências é majoritariamente "automatic modules"** (nome derivado do jar,
  frágil): Ktor, Arrow, Exposed, Koin, HikariCP, Micrometer; OpenTelemetry **recusou** modularizar
  ([otel-java #5052](https://github.com/open-telemetry/opentelemetry-java/issues/5052)). Só
  kotlinx.serialization tem `module-info` real. `requires` sobre automatic modules não entrega
  garantia alguma.
- ❌ **GraalVM Native Image** (closed-world AOT): não há module layer no binário — a encapsulação de
  **runtime** do JPMS é um no-op, e o trimming é redundante com a reachability analysis
  ([GraalVM native-image](https://www.graalvm.org/jdk25/reference-manual/native-image/)). Restaria
  só a checagem de compilação — e ainda assim frágil.
- ❌ Os 6 consumidores de reflexão (kotlinx.serialization, Koin, Ktor, Exposed, HikariCP, Flyway/
  Logback/Micrometer/OTel) exigiriam `opens`, corroendo o próprio benefício de encapsulação.

### Opção 3 — status quo reforçado (escolhida)

- ✅ Zero atrito novo de build/runtime; funciona com Kotlin, automatic modules e nativo.
- ✅ Konsist já cobre **mais** do que o JPMS cobriria (context boundary, convenções, pureza) e falha
  o CI.
- ✅ A regra de "pacote de contrato" entrega o único ganho real do `exports` (esconder tipos
  públicos de implementação) em build-time, no idioma atual.
- ❌ Continua sendo enforcement em **tempo de teste/CI**, não no compilador/runtime (aceitável —
  gate obrigatório de `main` via branch protection).

## Consequences

- **Nenhuma mudança** de build, pipeline ou runtime. `module-info.java`, `jlink`, `jmod` e
  ServiceLoader permanecem ausentes por decisão.
- Reforço a implementar: **GAP-BS `[M]`** — regra Konsist de pacote de contrato.
- **Critério de reavaliação** (revisitar o JPMS só se **ambos** ocorrerem): (1) passarmos a publicar
  uma biblioteca `domain` *framework-free* para consumidores JPMS **externos**; **e** (2) as
  dependências críticas passarem a shippar `module-info` real. Enquanto o alvo de produção for
  Native Image, o valor de runtime do JPMS permanece nulo.
- Itens Kotlin a acompanhar: package-private ([KT-29227](https://youtrack.jetbrains.com/issue/KT-29227))
  e "shared internals" ([KEEP #469](https://github.com/Kotlin/KEEP/discussions/469)).

## Sources

- Kotlin + JPMS (`--patch-module`): https://kotlinlang.org/docs/gradle-configure-project.html
- `kotlin.stdlib` no module graph: https://github.com/gradle/gradle/issues/13978 · https://youtrack.jetbrains.com/issue/KTIJ-29390
- Kotlin sem package-private / shared internals: https://youtrack.jetbrains.com/issue/KT-29227 · https://github.com/Kotlin/KEEP/discussions/469
- OpenTelemetry recusa `module-info`: https://github.com/open-telemetry/opentelemetry-java/issues/5052
- Automatic modules (risco): https://blog.joda.org/2017/05/java-se-9-jpms-automatic-modules.html · https://nipafx.dev/java-modules-jpms-maturity-model/
- GraalVM Native Image (closed-world): https://www.graalvm.org/jdk25/reference-manual/native-image/
- JPMS + hexagonal (ServiceLoader): https://github.com/TomCools/jpms-hexagonal-architecture
- Relevância do JPMS em 2026: https://www.javacodegeeks.com/2026/04/java-module-system-in-2026-still-ignored-still-relevant.html

## Related

ADR-0026 (fitness functions Konsist) · ADR-0028 (forbidden imports) · ADR-0030/0032 (GraalVM Native
Image) · `docs/context-map.md` · wiki [Architecture Fitness Functions](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Architecture-Fitness-Functions)
