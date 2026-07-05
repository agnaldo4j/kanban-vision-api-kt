# syntax=docker/dockerfile:1

# ── Stage 0: download OTel Java Agent ────────────────────────────────────────
FROM alpine:3.19 AS otel-agent

RUN apk add --no-cache curl && \
    curl -fsSL -o /opentelemetry-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.14.0/opentelemetry-javaagent.jar" && \
    echo "16f8e28fa1ddcd56ed85bf633bd1d1fbc78ea7c4cc50e8c5726b2a319f5058c8  /opentelemetry-javaagent.jar" | sha256sum -c -

# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS build
# Gradle daemon and toolchain share the image's JDK 25 (ADR-0024) — the
# jvmToolchain(25) requirement resolves to JAVA_HOME, so Foojay never downloads
# a (musl-incompatible) JDK inside Alpine.

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

# Copy sources and build fat JAR
COPY domain/src domain/src
COPY usecases/src usecases/src
COPY sql_persistence/src sql_persistence/src
COPY http_api/src http_api/src
RUN ./gradlew :http_api:buildFatJar --no-daemon -q

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS runtime

RUN addgroup -g 1000 -S appgroup && adduser -u 1000 -S appuser -G appgroup

WORKDIR /app

COPY --from=build /workspace/http_api/build/libs/kanban-vision-api.jar app.jar
COPY --from=otel-agent /opentelemetry-javaagent.jar opentelemetry-javaagent.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.io.tmpdir=/tmp", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
