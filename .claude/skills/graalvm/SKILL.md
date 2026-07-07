---
name: graalvm
description: >
  Guie a adoção de GraalVM em produção neste projeto (ADR-0030): GraalVM para
  produção, JVM Temurin para desenvolvimento e testes. Use este skill ao evoluir
  o Dockerfile de runtime, avaliar Native Image, gerar reachability metadata,
  ou decidir entre modo JIT (Graal compiler) e AOT para o fat JAR Ktor.
argument-hint: "[tarefa, ex.: fase-1-jit, native-image, metadata (opcional)]"
allowed-tools: Read, Grep, Glob, Bash, Edit
---

# GraalVM — produção (ADR-0030)

> **Política central**: produção roda GraalVM; desenvolvimento e testes continuam na
> **JVM Temurin 25** (`.sdkmanrc` e toolchain 25 intocados — ADR-0024; `testAll` sempre em
> Temurin). Este skill é o guia; a **execução acontece só via card no board #6** — nada aqui
> autoriza mudar o build sem gap puxado.

---

## 1. Os dois modos

GraalVM não é uma coisa só. O caminho evolutivo do projeto é **JIT primeiro, Native Image
como meta** (ADR-0030).

| Aspecto | GraalVM JDK (Graal JIT) | Native Image (AOT) |
|---|---|---|
| Esforço de adoção | Trocar a imagem de runtime do Dockerfile | Plugin Gradle + reachability metadata + novo pipeline |
| Startup | Segundos (igual JVM) | Dezenas de ms (~100x) |
| Memória | Heap JVM normal | Fração da JVM |
| Peak throughput | ≥ C2 (HotSpot) | Geralmente menor (sem JIT; PGO mitiga na Oracle GraalVM) |
| OTel javaagent | Funciona | **Incompatível** — instrumentação de bytecode em runtime não existe |
| Reflection/proxies/resources | Livre | Exige reachability metadata (seção 6) |
| Debugging/profiling | Ferramentas JVM normais | Limitado (JFR/heap dump com suporte parcial) |

**Closed-world assumption**: no Native Image, tudo que é alcançável em runtime precisa ser
conhecido em build time — o que não foi declarado não existe no binário.

## 2. Distribuições e licença

| Distribuição | Licença | Notas |
|---|---|---|
| Oracle GraalVM | GFTC (grátis, inclusive produção; não-OSS) | Recursos extra: G1 no native, PGO |
| GraalVM Community Edition | GPLv2 + Classpath Exception | 100% open source, base OpenJDK |

- Preferir imagens Docker oficiais (`container-registry.oracle.com/graalvm/...` ou
  `ghcr.io/graalvm/...`).
- **Nunca cravar versão neste arquivo** — consultar https://www.graalvm.org/downloads/ e casar
  com Java 25 no momento do gap.

## 3. Instalação local (SDKMAN)

```bash
sdk list java | grep -i graal        # descobrir identificadores disponíveis
sdk install java <versao>-graal      # instalar
sdk use java <versao>-graal          # usar SÓ no shell atual (experimento pontual)
native-image --version               # confirmar o componente AOT presente
```

- **NÃO alterar `.sdkmanrc`** — o default de dev permanece Temurin (`java=25.0.3-tem`).
  GraalVM local serve só para experimentos de Native Image / tracing agent.

## 4. Caminho evolutivo em fases (cada fase = card no board #6)

**Fase 1 — GraalVM JDK (JIT) no runtime** · gap pequeno, baixo risco:
- Trocar apenas o estágio `runtime` do `Dockerfile` (`eclipse-temurin:25-jre-alpine` → imagem
  GraalVM JDK). Atenção: imagens GraalVM oficiais são **glibc** — provável migração de base
  alpine → glibc slim, mantendo user não-root uid 1000 e o download do OTel javaagent.
- Estágio de build, fat JAR e `ENTRYPOINT` com javaagent **não mudam**.
- Verificação: smoke da imagem + baseline k6 comparativo (`/load-testing`) — startup, memória, p95.

**Fase 2 — Native Image (meta)** · gap maior, com pré-requisito duro:
- **Pré-requisito**: migrar observabilidade para longe do javaagent (OTel SDK/Micrometer em
  build time) — supersede parte da ADR-0009 e **exige nova ADR antes**, conforme a política de
  imutabilidade da ADR-0023.
- Plugin `org.graalvm.buildtools.native` em `:http_api`, reachability metadata (seções 5-6),
  estágio de build com `native-image`, `ENTRYPOINT` vira o binário — sem `java`, sem javaagent.
- Verificação: smoke exercitando TODAS as rotas + baseline k6 comparativo.

## 5. Compatibilidade do stack DESTE projeto (Native Image)

> Cada linha é **exigência a validar no momento do gap** (docs oficiais + Reachability Metadata
> Repository), não fato consumado.

| Dependência | O que exige no Native Image |
|---|---|
| OTel javaagent 2.x | **Incompatível** — bloqueador da Fase 2; substituir por instrumentação em build time |
| Koin 4.x | DSL `module {}` é lambda-based (favorável); validar usos de `KClass`/reflection na versão em uso |
| Netty (engine Ktor) | Reflection e `Unsafe` internos; metadata existe no repositório da comunidade — validar combinação Ktor/Netty; alternativa: engine CIO |
| kotlinx.serialization | Favorável (serializers em compile time); atenção a polimorfismo aberto/serializers dinâmicos |
| PostgreSQL JDBC + HikariCP | Carga via `Class.forName` — reflect-config; metadata comunitária em grande parte disponível |
| Flyway | Migrations `.sql` precisam entrar via resource-config |
| Arrow-kt | Funções puras/inline, baixo risco; confirmar se Optics usa reflection |

## 6. Reachability metadata

Duas fontes, nesta ordem:

1. **GraalVM Reachability Metadata Repository** — habilitado pelo plugin Gradle
   (`org.graalvm.buildtools.native`), cobre libs populares sem esforço.
2. **Tracing Agent** — rodar o app na JVM com o agent, exercitando as rotas; a jornada k6 de
   `/load-testing` serve como exercitador:

```bash
# GraalVM local (seção 3) + fat JAR buildado
java -agentlib:native-image-agent=config-output-dir=http_api/src/main/resources/META-INF/native-image/com.kanbanvision/http_api \
  -jar http_api/build/libs/kanban-vision-api.jar
# noutro terminal: k6 run load/simulation-journey.js   # exercita a jornada completa
```

- O agent só registra **o que foi exercitado** — rota não visitada = metadata faltante =
  falha em runtime. Cobrir todas as rotas antes de confiar na saída.

## 7. Pitfalls conhecidos

- **Build e testes ficam sempre em Temurin** — a estratégia é só runtime de produção; se
  `testAll` passou a depender de GraalVM, algo está errado.
- **Alpine/musl**: Native Image em musl exige toolchain própria — o caminho de menor atrito é
  imagem base glibc; não insistir em alpine.
- **Metadata faltante falha em runtime** (`MissingReflectionRegistrationError` /
  `ClassNotFoundException`), não em build — smoke da imagem é obrigatório no DoD do gap.
- **Não cravar versões** (GraalVM, plugin, identificador SDKMAN) nem aqui nem em ADR — matriz de
  compatibilidade oficial no momento do gap.
- **Fat JAR e native são pipelines separados**: `mergeServiceFiles()` do fat JAR é irrelevante
  no binário nativo — não misturar os dois nem "aproveitar" configs de um no outro.
