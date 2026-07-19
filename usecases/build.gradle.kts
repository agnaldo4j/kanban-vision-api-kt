plugins {
    id("kanban.kotlin-common")
    id("info.solidsoft.pitest")
}

val jacocoExcludes =
    listOf(
        "**/*\$\$inlined\$*",
        "**/*\$Companion.class",
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

pitest {
    // PITest 1.25.3 uses ASM 9.9.1 which supports Java 25 class files (major version 69).
    // The Gradle plugin (1.19.0) is pinned but pitestVersion overrides the core JAR used.
    pitestVersion.set("1.25.3")
    junit5PluginVersion.set("1.2.3")
    targetClasses.set(setOf("com.kanbanvision.usecases.*"))
    targetTests.set(setOf("com.kanbanvision.usecases.*"))
    mutators.set(setOf("STRONGER"))
    // Baseline GAP-AP (2026-07-05): 60% PITest score (159/264; 51% KILLED puros no XML —
    // PITest conta timeouts como kill). Gate inicial 55% dá margem à variação de timeouts
    // entre máquinas; subida gradual em gaps futuros (mesmo caminho do domain: 38% → 58%).
    mutationThreshold.set(55)
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

dependencies {
    implementation(project(":domain-common"))
    api(project(":domain"))

    api("io.arrow-kt:arrow-core:2.2.3")
    implementation("org.slf4j:slf4j-api:2.0.18")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.10")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.2")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.kotest:kotest-property:6.2.2")
}
