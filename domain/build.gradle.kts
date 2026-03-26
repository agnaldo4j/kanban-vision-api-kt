plugins {
    id("kanban.kotlin-common")
    id("info.solidsoft.pitest") version "1.15.0"
}

dependencies {
    implementation("io.arrow-kt:arrow-core:2.0.1")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.20")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.3")
    testImplementation("io.mockk:mockk:1.14.2")
    testImplementation("io.kotest:kotest-property:5.9.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

pitest {
    junit5PluginVersion.set("1.2.1")
    targetClasses.set(setOf("com.kanbanvision.domain.simulation.*"))
    targetTests.set(setOf("com.kanbanvision.domain.simulation.*"))
    mutators.set(setOf("STRONGER"))
    // Baseline: 38% (70/182 mutants killed). 97% line coverage ≠ assertion quality.
    // Reinertsen: surviving mutants reveal weak assertions on queue/WIP logic.
    // Raise threshold progressively as assertions improve.
    mutationThreshold.set(35)
    outputFormats.set(setOf("XML", "HTML"))
    timestampedReports.set(false)
    failWhenNoMutations.set(true)
    threads.set(Runtime.getRuntime().availableProcessors())
}
