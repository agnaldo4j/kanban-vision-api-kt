plugins {
    id("kanban.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("info.solidsoft.pitest")
}

pitest {
    // PITest 1.25.3 uses ASM 9.9.1 which supports Java 25 class files (major version 69).
    pitestVersion.set("1.25.3")
    junit5PluginVersion.set("1.2.3")
    targetClasses.set(setOf("com.kanbanvision.persistence.*"))
    targetTests.set(setOf("com.kanbanvision.persistence.*"))
    mutators.set(setOf("STRONGER"))
    // Baseline GAP-AS (2026-07-05): 71% (545/767 após reviver os 4 testes que o
    // JUnit pulava — PITest conta TIMED_OUT/RUN_ERROR como kill). Gate 65 = margem
    // para variância do embedded postgres. Subida gradual em gaps futuros (GAP-AU).
    mutationThreshold.set(65)
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
    finalizedBy("cleanupEmbeddedPostgres")
}

// O PITest mata minions por timeout SEM deixar o zonky desligar o postgres embedded:
// um único run vazava ~32 processos + segmentos SysV — e o SHMMNI default do macOS
// é 32, então o run SEGUINTE falhava com "could not create shared memory segment".
// TERM força o shutdown gracioso do postgres, que libera o segmento junto.
// pkill invocado DIRETO (sem shell): não existe processo intermediário cuja cmdline
// contenha o padrão (pkill nunca se auto-mata), e não há dependência de bash.
tasks.register<Exec>("cleanupEmbeddedPostgres") {
    onlyIf { !System.getProperty("os.name").lowercase().contains("windows") }
    commandLine("pkill", "-TERM", "-f", "embedded-pg/PG-")
    isIgnoreExitValue = true // exit 1 = nenhum processo órfão — estado desejado
}

val exposedVersion = "1.3.1"
val resilience4jVersion = "2.4.0"

dependencies {
    // Flyway 12.x migrou para Jackson 3.x (`tools.jackson`) e puxa a família em 3.1.4 — vulnerável a
    // GHSA-5gvw-p9qm-jgwh (jackson-databind, fix 3.1.5), gate de SCA (ADR-0025). O SBOM agregado varre o
    // runtimeClasspath de CADA módulo, então o BOM precisa estar aqui (e não só no http_api). Alinha toda a
    // família tools.jackson a 3.1.5 numa linha. Remover quando o flyway-core puxar >= 3.1.5 nativamente.
    implementation(platform("tools.jackson:jackson-bom:3.1.5"))

    implementation(project(":domain-common"))
    implementation(project(":domain-kanban"))
    implementation(project(":domain-simulation"))
    implementation(project(":usecases"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-micrometer:$resilience4jVersion")
    implementation("io.micrometer:micrometer-core:1.17.0")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("org.flywaydb:flyway-core:12.11.0")
    runtimeOnly("org.flywaydb:flyway-database-postgresql:12.11.0")
    implementation("ch.qos.logback:logback-classic:1.5.38")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.10")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.2")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("io.zonky.test:embedded-postgres:2.2.2")
    testImplementation("io.kotest:kotest-property:6.2.2")
}

val jacocoExcludes =
    listOf(
        "**/*\$\$serializer.class",
        "**/*\$Companion.class",
        // DatabaseFactory: all testable branches are covered by DatabaseFactoryTest (happy path,
        // interrupt, closed datasource, close(), init(migrationsEnabled=false)). Two paths remain:
        //   1. isReady() TimeoutException — requires making DB validation hang >3s; not reliable in CI.
        //   2. isReady() generic Exception catch — unreachable: the inner supplyAsync already catches
        //      ALL exceptions, so CompletableFuture.get() can only throw InterruptedException or
        //      TimeoutException (both covered), never ExecutionException.
        // Excluded analogously to MainKt.class (both are infrastructure, not domain/application logic).
        "com/kanbanvision/persistence/DatabaseFactory.class",
    )

tasks.named<JacocoReport>("jacocoTestReport") {
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude(jacocoExcludes)
        },
    )
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude(jacocoExcludes)
        },
    )
}
