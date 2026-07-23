plugins {
    id("kanban.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.ktor.plugin")
    id("info.solidsoft.pitest")
    id("org.graalvm.buildtools.native")
}

// Classpath mínimo do binário nativo de migração (ADR-0032): só persistência + logging.
val migrationRuntime: Configuration by configurations.creating

// Resources do binário de migração SEM a reachability metadata do app (review do PR):
// a metadata gerada pelo agent registra tipos Ktor/Swagger/Netty que não existem no
// classpath enxuto da migração — o builder do native-image não deve nem parseá-la.
// A metadata MANUAL (http_api-manual) permanece: registra o reflection do HikariConfig
// e os resources de logging, necessários aos DOIS binários.
val migrationResources =
    tasks.register<Sync>("migrationResources") {
        from(sourceSets.main.get().resources) {
            exclude("META-INF/native-image/com.kanbanvision/http_api/**")
        }
        into(layout.buildDirectory.dir("migration-resources"))
    }

// Native Image de produção (ADR-0032): o Dockerfile compila os dois binários no estágio
// de build. Local: exige GRAALVM_HOME apontando para um GraalVM (SDKMAN — skill /graalvm §3).
// As tasks nativas NUNCA entram em check/testAll (testes e gates seguem na JVM Temurin).
graalvmNative {
    // Sem nativeTest: o plugin o religaria ao check, contaminando testAll/CI.
    testSupport.set(false)
    // Foojay não provisiona GraalVM — o toolchain nativo vem de GRAALVM_HOME.
    toolchainDetection.set(false)
    metadataRepository {
        enabled.set(true)
    }
    binaries {
        named("main") {
            imageName.set("kanban-vision-api")
            // PGO (ADR-0036): +16,7% de throughput sob o envelope do pod, imagem −8,6%. NÃO há
            // buildArg aqui de propósito — o plugin acha o perfil pela convenção
            // src/pgo-profiles/main/*.iprof e só então passa --pgo; sem o diretório, build normal.
            // O Dockerfile descomprime o .gz para lá antes do nativeCompile.
            // G1 NÃO entra: medido −22,4% sob cpus=0.5 (Effective CPU Count=1 ⇒ 1 thread de GC).
        }
        // Binário dedicado do Job k8s de migração (ADR-0032): preserva o procedimento
        // pré-rollout em duas fases da ADR-0013 no mundo nativo. Classpath ENXUTO
        // (migrationRuntime, não o runtimeClasspath completo): os jars do Netty trazem
        // native-image.properties próprios que forçam init em build time e capturam um
        // Logger logback no image heap — sem Netty/Ktor no classpath o problema não existe.
        create("migration") {
            imageName.set("kanban-vision-migrate")
            mainClass.set("com.kanbanvision.httpapi.MigrationMainKt")
            classpath(
                sourceSets.main
                    .get()
                    .output.classesDirs,
                migrationResources,
                configurations["migrationRuntime"],
            )
            // O static init do Flyway monta URLs https (links de docs) — no binário main
            // o protocolo vem habilitado pela metadata do exporter OTLP.
            buildArgs.add("--enable-url-protocols=https")
            // ADR-0036: epsilon (no-op) — a migração é bounded e curta, o processo sai e o SO
            // recupera tudo. Validado em DB virgem sob 256Mi/0.5cpu: 2 migrações, exit 0, sem OOM.
            // ATENÇÃO: epsilon NUNCA libera, então o pico é função da alocação TOTAL do Job, não
            // do live set — um backfill de dados grande pode estourar. Reavaliar ao adicionar
            // migração que mova volume.
            buildArgs.add("--gc=epsilon")
        }
    }
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
        // Lettuce/Redis IO glue (ADR-0041): the real EVALSHA gateway and the RedisClient bootstrap
        // can only run against a live Redis (no embedded Redis in the repo — cf. zonky for postgres),
        // so they are exercised by the CI native smoke probe, not JVM unit tests. The pure limiter
        // logic (RedisRateLimiter fallback/breaker, result mapping) stays fully covered.
        "**/ratelimit/redis/**",
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
        implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1") {
            because("GHSA-j3rv-43j4-c7qm, GHSA-rmj7-2vxq-3g9f e outras corrigidas em 2.21.4")
        }
        implementation("org.mozilla:rhino:1.9.1") {
            because(
                "GHSA-3w8q-xq97-5j7x: ranges afetados [0,1.7.14.1), [1.7.15,1.7.15.1) e [1.8.0,1.8.1); " +
                    "1.7.14.1 não foi publicada no Maven Central e 1.7.15/1.8.0 são vulneráveis — " +
                    "1.7.15.1 é a menor versão não vulnerável disponível",
            )
        }
    }

    // Lettuce (rate limit distribuído, GAP-BZ/ADR-0041) é compilada contra Netty 4.1.x e puxa módulos
    // que o Ktor NÃO usa — netty-resolver-dns / netty-codec-dns (com CVEs na 4.1.118, GHSA-cm33/5pvg/
    // 676x/xmv7). O BOM alinha TODA a família io.netty a 4.2.16.Final, fechando o gate de SCA (ADR-0025)
    // e evitando drift módulo-a-módulo. `platform` participa da resolução com a maior versão exigida
    // (4.2.16 > 4.1.118 da Lettuce e > 4.2.15 do Ktor 3.5.1).
    // 4.2.15 → 4.2.16: lote de CVEs em netty-codec-http/http2/compression (GHSA-558v/4mp9/6cqp/6jqx/gcjf/
    // jppx/mvh2/q4f6/c69g, High/Medium), fix 4.2.16.Final. Não introduzido por nenhum PR — bump transitivo
    // do OSV; bump quando o Ktor puxar >= 4.2.16 nativamente.
    implementation(platform("io.netty:netty-bom:4.2.16.Final"))

    // logstash-logback-encoder 9.0 migrou para Jackson 3.x (coordenadas `tools.jackson`, distintas do
    // Jackson 2.x `com.fasterxml.jackson` pinado acima) e puxa a família em 3.1.4 — vulnerável a
    // GHSA-5gvw-p9qm-jgwh (jackson-databind, corrigida em 3.1.5), gate de SCA (ADR-0025). O BOM alinha
    // TODA a família tools.jackson (core/databind/module-kotlin) a 3.1.5 numa linha, evitando drift
    // módulo-a-módulo (mesmo idioma do netty-bom acima). Remover quando logstash-logback-encoder puxar
    // >= 3.1.5 nativamente.
    implementation(platform("tools.jackson:jackson-bom:3.1.5"))

    implementation(project(":domain-common"))
    implementation(project(":domain-kanban"))
    implementation(project(":usecases"))
    implementation(project(":sql_persistence"))

    // Binário de migração (ADR-0032): persistência + logback para logs do Job.
    // O logback.xml referencia o OpenTelemetryAppender — o jar precisa estar presente.
    // O jackson-bom precisa ser repetido aqui: `migrationRuntime` é uma configuração própria e NÃO
    // herda o `platform` do `implementation`, então o logstash-logback-encoder abaixo puxaria a família
    // tools.jackson 3.1.4 (CVE GHSA-5gvw-p9qm-jgwh) de volta ao SBOM do binário de migração.
    migrationRuntime(platform("tools.jackson:jackson-bom:3.1.5"))
    migrationRuntime(project(":sql_persistence"))
    migrationRuntime("ch.qos.logback:logback-classic:1.5.38")
    migrationRuntime("net.logstash.logback:logstash-logback-encoder:9.0")
    migrationRuntime("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.29.0-alpha")

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
    // Rate limit distribuído (GAP-BZ/ADR-0041): contador compartilhado em Redis via Lettuce,
    // com circuit-breaker resilience4j (mesmo idioma do DbCircuitBreaker do sql_persistence).
    implementation("io.lettuce:lettuce-core:6.7.1.RELEASE")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.4.0")
    implementation("io.github.resilience4j:resilience4j-micrometer:2.4.0")
    implementation("io.github.resilience4j:resilience4j-kotlin:2.4.0")
    implementation("io.ktor:ktor-server-cors-jvm:3.5.1")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:3.5.1")
    implementation("io.micrometer:micrometer-registry-prometheus:1.17.0")

    // OpenTelemetry API — spans manuais somente em http_api
    implementation("io.opentelemetry:opentelemetry-api:1.64.0")
    // Kotlin coroutines extension: asContextElement() propagates OTel context across thread hops
    implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.64.0")
    // ADR-0031: traces em build time (sem javaagent) — SDK autoconfigure lê as envs OTEL_*;
    // instrumentações de biblioteca na linha 2.29.0(-alpha), alinhada ao SDK/API 1.63.0.
    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.64.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.64.0")
    implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-3.0:2.29.0-alpha")
    // implementation (não runtimeOnly): OpenTelemetryDriver.install() é chamado em código —
    // o driver nasce com noop() e não lê o GlobalOpenTelemetry.
    implementation("io.opentelemetry.instrumentation:opentelemetry-jdbc:2.29.0-alpha")
    // Referenciada só pelo logback*.xml — runtime only.
    runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.29.0-alpha")

    implementation("ch.qos.logback:logback-classic:1.5.38")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    // janino removido: existia só para o <if> condicional do logback.xml,
    // suporte que o logback 1.5.x eliminou (seleção agora via <include>).

    testImplementation("io.ktor:ktor-server-test-host-jvm:3.5.1")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:3.5.1")
    testImplementation("io.insert-koin:koin-test-junit5:4.2.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.10")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.2")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("com.h2database:h2:2.4.240")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // ADR-0031: InMemorySpanExporter para o teste de integração de exportação de spans
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.64.0")
    testImplementation("io.kotest:kotest-property:6.2.2")
    // Pact JVM 4.6.17 — compatível com JUnit Jupiter 6.0.3 (GAP-K / ADR-0011)
    testImplementation("au.com.dius.pact.consumer:junit5:4.7.3")
    testImplementation("au.com.dius.pact.provider:junit5:4.7.3")
}
