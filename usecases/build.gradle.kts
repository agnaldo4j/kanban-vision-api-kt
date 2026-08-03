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
    // PITest conta timeouts como kill). Gate inicial 55% dava margem à variação de timeouts
    // entre máquinas; a subida gradual prevista (mesmo caminho do domain: 38% → 58%) não é
    // alcançável neste módulo, e a medição do GAP-EZ (2026-08-03) diz por quê.
    //
    // 21% do DENOMINADOR é bytecode sintético, insensível a teste: dos 294 mutantes, 62 são
    // NO_COVERAGE e 54 deles são o lowering da state machine de coroutine — `throwOnFailure`
    // (VoidMethodCallMutator) e `invokeSuspend` (NullReturnValsMutator), um par por função
    // `suspend`. Sobre o código killable o módulo marca 166/232 = 71,6%, não 56,5%.
    //
    // Com isso o gate PUNIA a extração de HOF, que é a classe de refactor que domina o board:
    // ela remove mutantes KILLED dos call sites (saem do numerador E do denominador — efeito
    // de denominador do #387) e ainda adiciona andaime novo no arquivo extraído. Medido no
    // #401/GAP-DX: 62,1% → 56,5% com 5 sítios, deixando 10 mutantes de folga; o GAP-EB tem 7.
    // 50 tolera remover 38 KILLED — `(166-K)/(294-K) ≥ 0,50` — e cobre a fila de HOFs do Todo.
    //
    // Não é licença para testar menos: a queda que este número absorve é a de refactor que
    // DELETA duplicação já coberta. Perda em SURVIVED ou NO_COVERAGE nos arquivos tocados
    // continua sendo defeito, e a aritmética por arquivo (base vs head) é o que separa os dois.
    mutationThreshold.set(50)
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
    implementation(project(":domain-kanban"))
    api(project(":domain-simulation"))

    api("io.arrow-kt:arrow-core:2.2.3")
    implementation("org.slf4j:slf4j-api:2.0.18")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.10")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.2")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.kotest:kotest-property:6.2.3")
}
