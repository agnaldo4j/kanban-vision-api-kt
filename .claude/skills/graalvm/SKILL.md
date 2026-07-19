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

> 📖 **Wiki:** ao mexer no runtime nativo/JIT, atualize as páginas `GraalVM` e `JVM` — mecânica na skill `/wiki-maintenance` (pipeline de build via `/c4-model`).

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

> **STATUS (2026-07-08)**: roadmap CONCLUÍDO — Fase 1 entregue no GAP-AY (ADR-0030) e
> Fase 2 entregue no GAP-BB (ADR-0032): produção roda os binários nativos (app +
> migração) compilados no Dockerfile; migrations via `FLYWAY_LOCATIONS=filesystem:`
> (o ClassPathScanner do Flyway não lê resources do binário). As seções abaixo ficam
> como referência do processo.

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
| Netty (engine Ktor) | Reflection e `Unsafe` internos; metadata existe no repositório da comunidade — validar combinação Ktor/Netty; alternativa: engine CIO. **No nativo o SFG do Ktor vaza contexto OTel entre requests mesmo com o fix KTOR-9431** — exige `-Dio.ktor.internal.disable.sfg=true` no ENTRYPOINT (GAP-BC, `docs/quality/otel-context-leak-native-2026-07.md`) |
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

## 8. Runtime tuning — memória e GC (produção)

> **Referência, não mandato.** Esta seção documenta *quais* parâmetros existem e *como decidir/
> validar*. Mudar de fato a config de produção (GC, heap, right-size do container) é uma decisão de
> requisito não-funcional → exige **ADR nova** (ADR-0023). Nada aqui altera a config por si só.

### Runtime atual (medido — ADR-0036)

Produção roda **Serial GC + PGO**, sem `-Xmx`. Baseline:
`docs/quality/performance-baseline-2026-07-native-tuned.md`.

> **A lição mais cara desta seção: medir sob o envelope do pod, nunca em host livre.**
> Sob `limits.cpu: 500m` o runtime reporta **`Effective CPU Count: 1`**. Um host de 4+ núcleos mede
> uma configuração que **não existe em produção** — foi assim que o G1 pareceu bom e virou decisão
> (ADR-0035) antes de a medição o refutar em −22,4% (ADR-0036). Use `docker-compose.limits.yml`.

### Garbage collector (SubstrateVM)

| GC | Como ligar | Veredito **medido** (ADR-0036) |
|---|---|---|
| **Serial** (default) | — | ✅ **É o que roda em produção.** Single-threaded, overhead mínimo — casa com heap pequeno (~80–92 MiB). |
| **G1** | build-time `--gc=G1` (**só Oracle GraalVM**; Linux AMD64/AArch64) | ❌ **−22,4% sob `cpus: 0.5`** — 1 thread de GC ⇒ overhead do coletor paralelo sem paralelismo. ✅ +4,8% com 4 CPUs. Custa +8% de imagem, +18% de memória. Dobrar o heap não salva (ainda −7,8%). **Só reconsiderar se o `limits.cpu` subir muito — com nova medição.** |
| **Epsilon** | build-time `--gc=epsilon` (no-op) | ✅ **No binário de migração** (bounded e curto). ⚠️ Nunca libera ⇒ o pico é a alocação **TOTAL**, não o live set. **Nunca** para a API long-running. |

### Heap em runtime

⚠️ **O knob depende do GC — não são intercambiáveis:**

| GC | Knob | Default |
|---|---|---|
| **Serial** | `-XX:MaximumHeapSizePercent=N` | 80% da memória disponível |
| **G1** | `-XX:MaxRAMPercentage=N` | **25%** |

`-XX:MaximumHeapSizePercent` **não ajusta o heap de um binário G1** — é opção do Serial (Oracle,
*Memory Management*). Consequência: trocar Serial→G1 sem tocar no k8s derruba o max heap de 80%→25%
do limite **silenciosamente** (sob 512Mi: 410Mi → 128Mi). `-Xmx` vale nos dois; `-Xms` não faz sentido
no AOT (não há warm-up de JIT). O SubstrateVM **lê o cgroup**; hoje não há `-Xmx` fixo e a medição não
indicou necessidade.

### PGO — procedimento de 3 passos (ADR-0036)

**+16,7% de throughput sob o envelope do pod, imagem −8,6%, memória menor.** Ganha no eixo **oposto**
ao do G1: reduz CPU *por request*, e a 0,5 CPU o app é CPU-bound.

**Não há `buildArgs` de PGO — de propósito.** O plugin acha o perfil pela convenção
`http_api/src/pgo-profiles/main/*.iprof` e só então passa `--pgo`; sem o diretório, build normal
(degradação suave é do plugin). O perfil é versionado **gzipado** (5,1 MB vs 39,6 MB crus, num repo de
8,9 MB) e o Dockerfile faz `gunzip` antes do `nativeCompile`.

Capturar **não roda dentro de `docker build`** — exige Postgres e k6:

```bash
# 1. instrumentar: buildArgs.add("--pgo-instrument") TEMPORÁRIO em named("main")
docker build -t kanban-vision-api:instr . && docker tag kanban-vision-api:instr kanban-vision-api:local
# 2. subir com o dump em /tmp — o appuser NÃO escreve em /app. Via `command:` no compose,
#    anexado ao ENTRYPOINT (exec form):  command: ["-XX:ProfilesDumpFile=/tmp/default.iprof"]
# 3. dirigir com workload REPRESENTATIVO (perfil não-representativo é contraproducente):
k6 run -e PROFILE=baseline load/simulation-journey.js
# 4. parar GRACIOSAMENTE — o dump só sai no shutdown; o default de 10s manda SIGKILL antes:
docker stop -t 60 kanban-vision-app
# 5. extrair, comprimir, versionar:
docker cp kanban-vision-app:/tmp/default.iprof . && gzip -9 default.iprof
mv default.iprof.gz http_api/src/pgo-profiles/main/
# 6. remover o --pgo-instrument e rebuildar — o plugin acha o perfil sozinho.
#    Sanidade: a imagem com PGO fica MENOR que a sem (296 vs 324 MB).
```

**Pegadinhas:** o perfil é capturado em **arm64** (Mac) e o CI builda **amd64** — o `.iprof` é baseado
em contadores (frequências de branch/chamada), então deve portar; conferir o log por warning de
rejeição. Se não portar, capturar sob `--platform linux/amd64` (QEMU): frequências são invariantes à
emulação, mas **nunca** medir *throughput* sob emulação. Perfil obsoleto **degrada suavemente** (o
native-image ignora métodos que não casam); recapturar é raro — cada recaptura soma ~5 MB ao histórico.

### Outras flags de build

`-O2` (default) vs `-Ob` (quick build — só custo de CI, ADR-0032) · `-march=native`/`-march=x86-64-v3`
(throughput vs portabilidade — o binário passa a ser por CPU-arch). **Onde ficam:**
`http_api/build.gradle.kts` (`graalvmNative.buildArgs`) — arquivo **imutável por política**; mexer
exige ADR + PR de execução.

### Loop de validação (obrigatório antes de cravar qualquer valor)

1. Medir com o **baseline k6** (`/load-testing`, ADR-0027) — **na mesma sessão** (números entre docs
   diferentes não são comparáveis) **e sob o envelope do pod**, memória **e** CPU
   (`docker compose -f docker-compose.yml -f docker-compose.limits.yml`). Host livre **não** autoriza
   decisão de runtime.
2. Coletar memória do **cgroup v2** — **duas métricas, duas perguntas, não as troque**:
   - **`memory.peak`** + **`memory.events`** (`oom_kill`/`max`) — provam que **o limite nunca foi
     encostado**. Cumulativo desde o start ⇒ ler **uma vez no fim**, impossível perder spike; e
     `memory.events` é mais forte que `docker inspect .State.OOMKilled`, que só dispara se o PID 1
     morrer. ⚠️ `memory.peak` **inclui page cache reclaimável** ⇒ **não** use para dimensionar.
   - **Working set** (`memory.current − inactive_file`) — é o que governa **eviction** e portanto
     **dimensiona `requests.memory`**. **Amostrar durante a carga**, não no fim.

   Trocá-los dá conclusões **opostas dos mesmos bytes**: dois runs da mesma config deram `memory.peak`
   de **93** e **152** MiB com working set estável em ~92 — e comparar o limite contra o peak de 152
   produziu a conclusão falsa de "folga 1,7×" (é 2,8×). Amostrar RSS post-hoc é pior ainda: foi o que
   produziu o impossível `41,5 < 73,6` dos baselines antigos.
3. Só então abrir a ADR fixando o parâmetro, com Confirmation amarrada ao baseline.
