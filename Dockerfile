# syntax=docker/dockerfile:1

# Native Image em produção (ADR-0032): o binário AOT substitui o fat JAR.
# Rollback: revert deste arquivo — o pipeline JVM (:http_api:buildFatJar) permanece
# funcional no Gradle para dev/testes (ADR-0030/0031/0032).

# ── Stage 1: build (GraalVM native-image) ─────────────────────────────────────
# Imagem oficial Oracle GraalVM com o compilador native-image (glibc/Oracle Linux).
# O daemon e o toolchain do Gradle usam o JDK GraalVM da imagem (mesma lógica da
# era Temurin: jvmToolchain(25) resolve para o JAVA_HOME do container).
FROM container-registry.oracle.com/graalvm/native-image:25 AS build

WORKDIR /workspace

# Copy Gradle wrapper and dependency manifests first (layer cache)
COPY gradlew gradlew
COPY gradle gradle
COPY gradle.properties gradle.properties
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts
COPY buildSrc buildSrc

COPY domain-common/build.gradle.kts domain-common/build.gradle.kts
COPY domain-kanban/build.gradle.kts domain-kanban/build.gradle.kts
COPY domain-simulation/build.gradle.kts domain-simulation/build.gradle.kts
COPY usecases/build.gradle.kts usecases/build.gradle.kts
COPY sql_persistence/build.gradle.kts sql_persistence/build.gradle.kts
COPY http_api/build.gradle.kts http_api/build.gradle.kts
# Módulo test-only (ADR-0026): sem src na imagem, mas o settings.gradle.kts o inclui —
# a fase de configuração do Gradle exige que o diretório do projeto exista.
COPY architecture/build.gradle.kts architecture/build.gradle.kts
COPY config config

# Remove local JDK path override — the container's JAVA_HOME is used instead
RUN sed -i '/^org\.gradle\.java\.home/d' gradle.properties && \
    chmod +x gradlew && ./gradlew dependencies --no-daemon -q

# Copy sources and compile both native binaries (app + migration Job — ADR-0013/0032).
# GRAALVM_HOME: o build.gradle.kts usa toolchainDetection=false e resolve o compilador
# pelo env. --no-configuration-cache: tasks nativas fora do cache de configuração.
COPY domain-common/src domain-common/src
COPY domain-kanban/src domain-kanban/src
COPY domain-simulation/src domain-simulation/src
COPY usecases/src usecases/src
COPY sql_persistence/src sql_persistence/src
COPY http_api/src http_api/src
# PGO (ADR-0036): o perfil é versionado GZIPADO — 39,6 MB crus contra um repo de 8,9 MB; a 5,1 MB
# comprimido cabe sem Git LFS. Descomprimir ANTES do nativeCompile: o plugin Gradle acha o perfil
# pela convenção src/pgo-profiles/main/*.iprof (sem buildArg) e só então passa --pgo. Se o .gz não
# existir, o build segue sem PGO — degradação suave, é semântica do plugin.
# Capturar o perfil exige app instrumentado + Postgres + carga k6, o que NÃO roda dentro de
# `docker build` — procedimento manual no skill /graalvm §8.
RUN gunzip -kf http_api/src/pgo-profiles/main/default.iprof.gz 2>/dev/null || \
    echo "AVISO: sem perfil PGO — build sem otimização guiada por perfil"
RUN GRAALVM_HOME="$JAVA_HOME" ./gradlew :http_api:nativeCompile :http_api:nativeMigrationCompile \
    --no-daemon --no-configuration-cache -q

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
# Base glibc mínima (mesma família do estágio de build): shadow-utils fornece
# useradd/groupadd (uid 1000 = securityContext do k8s); wget atende o healthcheck
# do docker-compose, que roda dentro do container.
FROM container-registry.oracle.com/os/oraclelinux:9-slim AS runtime

RUN microdnf install -y shadow-utils wget && \
    microdnf clean all && \
    groupadd -g 1000 appgroup && \
    useradd -u 1000 -g appgroup -M -s /usr/sbin/nologin appuser

WORKDIR /app

COPY --from=build /workspace/http_api/build/native/nativeCompile/kanban-vision-api /app/kanban-vision-api
COPY --from=build /workspace/http_api/build/native/nativeMigrationCompile/kanban-vision-migrate /app/kanban-vision-migrate
# Migrations como ARQUIVOS: o ClassPathScanner do Flyway não lê resources do binário
# (protocolo "resource" não suportado) — filesystem: dispensa o scanner de classpath.
COPY --from=build /workspace/sql_persistence/src/main/resources/db/migration /app/db/migration
ENV FLYWAY_LOCATIONS=filesystem:/app/db/migration

USER appuser

EXPOSE 8080

# disable.sfg (GAP-BC): no binário nativo o SuspendFunctionGun do Ktor retém o contexto
# OTel do request anterior na thread do event loop — spans SERVER são suprimidos e requests
# encadeiam num mesmo trace (na JVM o fix KTOR-9431 do Ktor 3.4.3 resolveu; no nativo não).
# Com a flag, o pipeline usa DebugPipelineContext: isolamento por request validado no
# docker local (5000/5000 traces com 1 span SERVER, rotas nomeadas, throughput preservado)
# — evidência em docs/quality/otel-context-leak-native-2026-07.md. Reavaliar a cada
# release do Ktor 3.x/opentelemetry-java-instrumentation 2.x.
ENTRYPOINT ["/app/kanban-vision-api", "-Djava.io.tmpdir=/tmp", "-Dio.ktor.internal.disable.sfg=true"]
