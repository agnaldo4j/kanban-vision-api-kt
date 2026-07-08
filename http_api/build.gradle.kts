plugins {
    id("kanban.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.ktor.plugin")
    id("info.solidsoft.pitest")
}

pitest {
    // PITest 1.25.3 uses ASM 9.9.1 which supports Java 25 class files (major version 69).
    pitestVersion.set("1.25.3")
    junit5PluginVersion.set("1.2.3")
    // Escopo = lógica destilada (plugins/adapters/events), espírito do recorte do
    // domain (só simulation). Rotas ficam FORA por decisão de custo medida: são 74%
    // dos mutantes (1078/1462) e a fábrica de hangs — mutar respond deixa o test
    // client esperando até o timeout do PITest (62 TIMED_OUT; o runner do CI passou
    // de 24min e o job foi cancelado). Rotas seguem cobertas por JaCoCo 97% +
    // testApplication + Pact; mutação de rotas = dívida consciente para gap futuro.
    targetClasses.set(
        setOf(
            "com.kanbanvision.httpapi.plugins.*",
            "com.kanbanvision.httpapi.adapters.*",
            "com.kanbanvision.httpapi.events.*",
        ),
    )
    targetTests.set(setOf("com.kanbanvision.httpapi.*"))
    mutators.set(setOf("STRONGER"))
    // Baseline GAP-AS (2026-07-05, escopo plugins/adapters/events): 50% (192/384,
    // 54s local). Gate 45 = 5pp de margem. Subida gradual em gaps futuros.
    mutationThreshold.set(45)
    outputFormats.set(setOf("XML", "HTML"))
    timestampedReports.set(false)
    failWhenNoMutations.set(true)
    threads.set(minOf(4, Runtime.getRuntime().availableProcessors()))
}

// PitestTask extends JavaExec: bytecode Java 25 exige orquestrador e forks no mesmo JDK.
tasks.withType<info.solidsoft.gradle.pitest.PitestTask>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) },
    )
}

application {
    mainClass.set("com.kanbanvision.httpapi.MainKt")
}

ktor {
    fatJar {
        archiveFileName.set("kanban-vision-api.jar")
    }
}

// O fat JAR perdia META-INF/services duplicados: no shadow 9 o duplicatesStrategy
// EXCLUDE (default) descarta duplicados ANTES dos transformers rodarem (KTOR-8987) —
// o Flyway 12 ficava só com o service file do driver PostgreSQL e quebrava em
// runtime com NPE no PluginRegister. INCLUDE entrega os duplicados ao
// ServiceFileTransformer, que concatena (descoberto no baseline do GAP-AR;
// testes não pegam porque rodam do classpath, não do fat JAR).
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
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
                minimum = "0.98".toBigDecimal() // ADR-0029
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
    constraints {
        // Transitivas de java-jwt/ktor-openapi com CVE conhecida — gate de SCA (ADR-0025).
        // Remover cada constraint quando a dependência direta passar a puxar versão >= fix.
        implementation("com.fasterxml.jackson.core:jackson-databind:2.21.4") {
            because("GHSA-j3rv-43j4-c7qm, GHSA-rmj7-2vxq-3g9f e outras corrigidas em 2.21.4")
        }
        implementation("org.mozilla:rhino:1.7.15.1") {
            because(
                "GHSA-3w8q-xq97-5j7x: ranges afetados [0,1.7.14.1), [1.7.15,1.7.15.1) e [1.8.0,1.8.1); " +
                    "1.7.14.1 não foi publicada no Maven Central e 1.7.15/1.8.0 são vulneráveis — " +
                    "1.7.15.1 é a menor versão não vulnerável disponível",
            )
        }
    }

    implementation(project(":usecases"))
    implementation(project(":sql_persistence"))

    implementation("io.arrow-kt:arrow-core:2.2.3")

    implementation("io.ktor:ktor-server-core-jvm:3.5.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.5.1")
    implementation("io.ktor:ktor-server-auth-jvm:3.5.1")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:3.5.1")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.5.1")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.5.1")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.5.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.1")

    implementation("io.insert-koin:koin-core:4.2.2")
    implementation("io.insert-koin:koin-ktor:4.2.2")
    implementation("io.insert-koin:koin-logger-slf4j:4.2.2")

    implementation("io.github.smiley4:ktor-openapi:5.7.0")
    implementation("io.github.smiley4:ktor-swagger-ui:5.7.0")

    implementation("io.ktor:ktor-server-rate-limit-jvm:3.5.1")
    implementation("io.ktor:ktor-server-cors-jvm:3.5.1")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:3.5.1")
    implementation("io.micrometer:micrometer-registry-prometheus:1.17.0")

    // OpenTelemetry API — spans manuais somente em http_api
    implementation("io.opentelemetry:opentelemetry-api:1.63.0")
    // Kotlin coroutines extension: asContextElement() propagates OTel context across thread hops
    implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.63.0")
    // ADR-0031: traces em build time (sem javaagent) — SDK autoconfigure lê as envs OTEL_*;
    // instrumentações de biblioteca na linha 2.29.0(-alpha), alinhada ao SDK/API 1.63.0.
    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.63.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.63.0")
    implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-3.0:2.29.0-alpha")
    // implementation (não runtimeOnly): OpenTelemetryDriver.install() é chamado em código —
    // o driver nasce com noop() e não lê o GlobalOpenTelemetry.
    implementation("io.opentelemetry.instrumentation:opentelemetry-jdbc:2.29.0-alpha")
    // Referenciada só pelo logback*.xml — runtime only.
    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.29.0-alpha")

    implementation("ch.qos.logback:logback-classic:1.5.37")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    // janino removido: existia só para o <if> condicional do logback.xml,
    // suporte que o logback 1.5.x eliminou (seleção agora via <include>).

    testImplementation("io.ktor:ktor-server-test-host-jvm:3.5.1")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:3.5.1")
    testImplementation("io.insert-koin:koin-test-junit5:4.2.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.1")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("com.h2database:h2:2.4.240")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // ADR-0031: InMemorySpanExporter para o teste de integração de exportação de spans
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.63.0")
    testImplementation("io.kotest:kotest-property:6.2.1")
    // Pact JVM 4.6.17 — compatível com JUnit Jupiter 6.0.3 (GAP-K / ADR-0011)
    testImplementation("au.com.dius.pact.consumer:junit5:4.7.3")
    testImplementation("au.com.dius.pact.provider:junit5:4.7.3")
}
