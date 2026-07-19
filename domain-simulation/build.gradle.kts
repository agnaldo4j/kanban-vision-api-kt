plugins {
    id("kanban.kotlin-common")
    id("info.solidsoft.pitest")
}

dependencies {
    implementation(project(":domain-common"))
    implementation(project(":domain-kanban"))

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
    // GAP-CL (Fase 2.3): este é o módulo :domain-simulation (o :domain foi renomeado ao ser aposentado).
    // GAP-CK: o Kanban Management BC saiu para :domain-kanban; o pool caiu para simulation-only (337
    // mutantes, 77%) — os testes do SimulationEngine que matavam mutantes de kanban não contam mais
    // (PITest não cruza módulo). Gate 78 → 73 (4pp). Histórico (módulo inteiro, GAP-AT): 715 mutantes, 82%.
    mutationThreshold.set(73)
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
