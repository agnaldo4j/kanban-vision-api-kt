plugins {
    id("kanban.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.ktor.plugin")
}

application {
    mainClass.set("com.kanbanvision.httpapi.MainKt")
}

ktor {
    fatJar {
        archiveFileName.set("kanban-vision-api.jar")
    }
}

val jacocoExcludes =
    listOf(
        "com/kanbanvision/httpapi/MainKt.class",
        "com/kanbanvision/httpapi/MigrationMainKt.class",
        "com/kanbanvision/httpapi/di/**",
        "**/*\$\$inlined\$*",
        "**/*\$\$serializer.class",
        "**/*\$Companion.class",
        // Kotlin 2.4.0 + Ktor 3.5.0 DSL generates synthetic lambda classes for
        // routing builders and plugin configuration blocks. These are framework
        // infrastructure, not business logic — excluded analogously to $inlined$.
        "**/*RoutesKt\$*",
        "**/plugins/*Kt\$*",
        // Java 25 + kotlinx.serialization 1.11.0: write$Self$ and synthetic
        // SerializationConstructorMarker constructors are generated inside DTO
        // classes directly (not in separate $$serializer files as in older versions).
        // Requests: write$Self$ is unreachable — server never serializes request bodies.
        // Responses: SerializationConstructorMarker constructor unused at test client side.
        "**/routes/*Request.class",
        "**/routes/*Response.class",
        // Error DTO classes: same pattern as above for serialization-generated code.
        "**/dtos/**",
    )

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                minimum = "0.97".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(
        sourceSets.main
            .get()
            .output.asFileTree
            .matching { exclude(jacocoExcludes) },
    )
}

tasks.named<JacocoReport>("jacocoTestReport") {
    classDirectories.setFrom(
        sourceSets.main
            .get()
            .output.asFileTree
            .matching { exclude(jacocoExcludes) },
    )
}

dependencies {
    implementation(project(":usecases"))
    implementation(project(":sql_persistence"))

    implementation("io.arrow-kt:arrow-core:2.2.3")

    implementation("io.ktor:ktor-server-core-jvm:3.5.0")
    implementation("io.ktor:ktor-server-netty-jvm:3.5.0")
    implementation("io.ktor:ktor-server-auth-jvm:3.5.0")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:3.5.0")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.5.0")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.5.0")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.5.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.0")

    implementation("io.insert-koin:koin-core:4.1.1")
    implementation("io.insert-koin:koin-ktor:4.1.1")
    implementation("io.insert-koin:koin-logger-slf4j:4.1.1")

    implementation("io.github.smiley4:ktor-openapi:5.7.0")
    implementation("io.github.smiley4:ktor-swagger-ui:5.7.0")

    implementation("io.ktor:ktor-server-rate-limit-jvm:3.5.0")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:3.5.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.5")

    // OpenTelemetry API — spans manuais somente em http_api (agente v2.14.0 empacota API v1.47.0)
    implementation("io.opentelemetry:opentelemetry-api:1.47.0")
    // Kotlin coroutines extension: asContextElement() propagates OTel context across thread hops
    implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.47.0")

    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation("org.codehaus.janino:janino:3.1.12")

    testImplementation("io.ktor:ktor-server-test-host-jvm:3.5.0")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:3.5.0")
    testImplementation("io.insert-koin:koin-test-junit5:4.1.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.0")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("com.h2database:h2:2.4.240")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.kotest:kotest-property:5.9.1")
    // Pact JVM 4.6.17 — compatível com JUnit Jupiter 6.0.3 (GAP-K / ADR-0011)
    testImplementation("au.com.dius.pact.consumer:junit5:4.7.1")
    testImplementation("au.com.dius.pact.provider:junit5:4.7.1")
}
