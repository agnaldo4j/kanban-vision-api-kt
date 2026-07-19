plugins {
    id("kanban.kotlin-common")
    id("info.solidsoft.pitest")
}

dependencies {
    implementation(project(":domain-common"))

    implementation("io.arrow-kt:arrow-core:2.2.3")

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
    targetClasses.set(setOf("com.kanbanvision.domain.*"))
    targetTests.set(setOf("com.kanbanvision.domain.*"))
    mutators.set(setOf("STRONGER"))
    // GAP-AT (2026-07-06): escopo ampliado para o módulo inteiro — 715 mutantes,
    // 82% (587) após rodada de kill (11 testes de violação de guard). Survivors
    // remanescentes são ruído estrutural: guards sombreados por validação redundante
    // (mutantes equivalentes) e bridges sintéticos de default args do Kotlin
    // (linhas além do fim do arquivo). Gate 78 = 4pp de margem.
    // Histórico de SCORE no escopo simulation-only: 38 → 54 → 63 → 68 → 69
    // (gates correspondentes: 35 → 45 → 58 → 65; ver skill kotlin-quality-pipeline).
    mutationThreshold.set(78)
    outputFormats.set(setOf("XML", "HTML"))
    timestampedReports.set(false)
    failWhenNoMutations.set(true)
    threads.set(minOf(4, Runtime.getRuntime().availableProcessors()))
}

// PitestTask extends JavaExec and uses the Gradle daemon JVM (Java 21 via org.gradle.java.home)
// by default. With jvmToolchain(25), compiled bytecode targets Java 25 (major version 69).
// Java 21 cannot read Java 25 class files — both the orchestrator and forked mutation
// processes must run on Java 25.
tasks.withType<info.solidsoft.gradle.pitest.PitestTask>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) },
    )
}
