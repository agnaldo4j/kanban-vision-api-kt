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

COPY domain/build.gradle.kts domain/build.gradle.kts
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
COPY domain/src domain/src
COPY usecases/src usecases/src
COPY sql_persistence/src sql_persistence/src
COPY http_api/src http_api/src
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

ENTRYPOINT ["/app/kanban-vision-api", "-Djava.io.tmpdir=/tmp"]
