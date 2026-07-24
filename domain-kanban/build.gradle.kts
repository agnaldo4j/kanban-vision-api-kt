plugins {
    id("kanban.kotlin-common")
    id("info.solidsoft.pitest")
}

dependencies {
    // ADR-0044: erros tipados nas OPERAÇÕES de agregado (Either/Raise via Arrow — FP pura, permitida
    // pelo DomainPurityTest). Precondições de construção/argumento seguem `require` (fail-fast em bug).
    // `api`: o `Either<KanbanError, _>` das operações de agregado (Board.addStep/addCard) é ABI
    // pública do módulo — expõe arrow-core transitivamente aos consumidores (padrão de usecases).
    api("io.arrow-kt:arrow-core:2.2.3")
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
    // Os testes comportamentais movidos vivem no pacote-pai flat `com.kanbanvision.domain.model`
    // (não em `model.kanban`); o glob precisa incluí-lo, senão o pitest roda só os testes novos.
    targetTests.set(setOf("com.kanbanvision.domain.model.*"))
    mutators.set(setOf("STRONGER"))
    // GAP-CK (Fase 2.2): Kanban Management BC isolado. 352 mutantes, 82% (288) — as suítes
    // comportamentais/property movidas (94 testes) cobrem card/step/worker/organization. Gate 78 (4pp),
    // mesmo patamar do :domain no escopo do módulo inteiro.
    mutationThreshold.set(78)
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
