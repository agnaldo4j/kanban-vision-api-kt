# syntax=docker/dockerfile:1

# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build

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

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q

# Copy sources and build fat JAR
COPY domain/src domain/src
COPY usecases/src usecases/src
COPY sql_persistence/src sql_persistence/src
COPY http_api/src http_api/src
RUN ./gradlew :http_api:buildFatJar --no-daemon -q

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /workspace/http_api/build/libs/kanban-vision-api.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.io.tmpdir=/tmp", "-jar", "app.jar"]
