plugins {
    id("kanban.kotlin-common")
    id("info.solidsoft.pitest")
}

dependencies {
    // Sem arrow: Domain/Audit/DomainError/CommonError usam apenas stdlib + java.time.
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.10")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.2")
}

pitest {
    // PITest 1.25.3 uses ASM 9.9.1 which supports Java 25 class files (major version 69).
    // The Gradle plugin (1.19.0) is pinned but pitestVersion overrides the core JAR used.
    pitestVersion.set("1.25.3")
    junit5PluginVersion.set("1.2.3")
    targetClasses.set(setOf("com.kanbanvision.domain.common.*"))
    targetTests.set(setOf("com.kanbanvision.domain.common.*"))
    mutators.set(setOf("STRONGER"))
    // GAP-CJ (Fase 2.1): kernel neutro extraído do :domain. Superfície pequena e determinística
    // (Audit + CommonError) — 21 mutantes, 100% killed na medição inicial. Gate 90 = ~2 mutantes
    // de margem, mais apertado que o domain (pool grande com ruído de equivalentes) de propósito.
    mutationThreshold.set(90)
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
