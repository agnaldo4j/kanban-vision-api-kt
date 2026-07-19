plugins {
    id("kanban.kotlin-common")
    id("info.solidsoft.pitest")
}

dependencies {
    // Sem arrow: os agregados kanban/organization não usam Either/Raise.
    implementation(project(":domain-common"))

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.10")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.2")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("io.kotest:kotest-property:6.2.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

pitest {
    // PITest 1.25.3 uses ASM 9.9.1 which supports Java 25 class files (major version 69).
    // The Gradle plugin (1.19.0) is pinned but pitestVersion overrides the core JAR used.
    pitestVersion.set("1.25.3")
    junit5PluginVersion.set("1.2.3")
    targetClasses.set(
        setOf(
            "com.kanbanvision.domain.model.kanban.*",
            "com.kanbanvision.domain.model.organization.*",
        ),
    )
    targetTests.set(
        setOf(
            "com.kanbanvision.domain.model.kanban.*",
            "com.kanbanvision.domain.model.organization.*",
        ),
    )
    mutators.set(setOf("STRONGER"))
    // GAP-CK (Fase 2.2): Kanban Management BC recém-isolado. Baseline 41% (144/352) — a verificação
    // comportamental profunda de kanban (movimentação de card, consumo de esforço, alocação de worker)
    // vivia nos testes do SimulationEngine, que ficaram no :domain e não contam mais (PITest não cruza
    // módulo). Gate inicial 38 (3pp de margem), a subir por J-Curve em gaps futuros — mesmo caminho do
    // :domain (38 → 54 → 63 → 68 → 69; gates 35 → 45 → 58 → 65).
    mutationThreshold.set(38)
    outputFormats.set(setOf("XML", "HTML"))
    timestampedReports.set(false)
    failWhenNoMutations.set(true)
    threads.set(minOf(4, Runtime.getRuntime().availableProcessors()))
}

// PitestTask extends JavaExec and uses the Gradle daemon JVM by default. With
// jvmToolchain(25), compiled bytecode targets Java 25 (major version 69) — both the
// orchestrator and forked mutation processes must run on Java 25.
tasks.withType<info.solidsoft.gradle.pitest.PitestTask>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) },
    )
}
