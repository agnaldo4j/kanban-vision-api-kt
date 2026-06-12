plugins {
    id("kanban.kotlin-common")
    id("info.solidsoft.pitest")
}

dependencies {
    implementation("io.arrow-kt:arrow-core:2.2.3")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.3")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("io.kotest:kotest-property:5.9.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

pitest {
    // PITest 1.25.3 uses ASM 9.9.1 which supports Java 25 class files (major version 69).
    // The Gradle plugin (1.15.0) is pinned but pitestVersion overrides the core JAR used.
    pitestVersion.set("1.25.3")
    junit5PluginVersion.set("1.2.3")
    targetClasses.set(setOf("com.kanbanvision.domain.simulation.*"))
    targetTests.set(setOf("com.kanbanvision.domain.simulation.*"))
    mutators.set(setOf("STRONGER"))
    // Baseline: 38% (70/182 killed). Round 2: 49% (90/182) after guard + metrics assertions.
    // Reinertsen: surviving mutants reveal weak assertions on queue/WIP logic.
    // Raise threshold progressively as assertions improve.
    mutationThreshold.set(45)
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
