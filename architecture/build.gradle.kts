plugins {
    id("kanban.kotlin-common")
}

// Módulo test-only (ADR-0026): fitness functions de arquitetura com Konsist.
// Não há src/main — o Konsist analisa as FONTES dos demais módulos por path
// (scopeFromProduction varre os src/main do projeto inteiro), então nenhuma
// dependência de produção é necessária.

dependencies {
    testImplementation("com.lemonappdev:konsist:0.17.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.2")
    // O Konsist REQUISITA JUnit Platform 1.x (linha JUnit 5); sem o launcher explícito
    // abaixo, o classpath misto impedia o Gradle de iniciar o runner ("Failed to load
    // JUnit Platform"). Com ele, a resolução de conflito alinha tudo em 6.1.1.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.2")
}

// Correção de cache (ADR-0026): o Konsist lê as fontes dos demais módulos em runtime,
// invisíveis aos inputs que o Gradle rastreia para esta task — sem isto, um PR que só
// muda domain/ reutilizaria um resultado verde stale do build cache e o gate não veria
// a violação. src/main (scopeFromProduction) E src/test (scopeFromTest — regra de
// nomenclatura de testes) são declarados como inputs.
tasks.test {
    // ProjectDependencyGraphTest (GAP-CL/ADR-0038) lê os build.gradle.kts em runtime para asserir o
    // grafo de `project` deps — passa a raiz explicitamente (workingDir do teste = projectDir do módulo).
    systemProperty("rootDir", rootProject.projectDir.absolutePath)

    // GAP-EX (Codex P1 no #397): o `settings.gradle.kts` é LIDO em runtime pelo
    // ProjectDependencyGraphTest e não era input — um PR que só acrescenta um `domain-*` ao settings
    // podia restaurar `:architecture:test` do build cache e a asserção nova nunca rodaria, que é
    // exatamente a regressão que ela existe para pegar.
    inputs
        .file(rootDir.resolve("settings.gradle.kts"))
        .withPropertyName("settingsScript")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // E a lista de módulos vem do settings, não de um literal: módulo novo entra como input sozinho.
    // Sem isso, as FONTES do módulo novo também ficariam de fora do rastreamento do Gradle.
    val modulosAnalisados =
        Regex("""":([A-Za-z0-9_\-]+)"""")
            .findAll(rootDir.resolve("settings.gradle.kts").readText())
            .map { it.groupValues[1] }
            .filter { it != "architecture" }
            .toList()
    require(modulosAnalisados.isNotEmpty()) { "nenhum módulo lido do settings.gradle.kts — o parser de inputs quebrou" }

    modulosAnalisados.forEach { module ->
        inputs
            .dir(rootDir.resolve("$module/src/main/kotlin"))
            .withPropertyName("analyzedSources_$module")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs
            .dir(rootDir.resolve("$module/src/test/kotlin"))
            .withPropertyName("analyzedTestSources_$module")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        // build.gradle.kts como input: o ProjectDependencyGraphTest o lê em runtime (invisível ao
        // Gradle), então uma mudança de deps deve re-rodar o gate em vez de servir verde stale do cache.
        inputs
            .file(rootDir.resolve("$module/build.gradle.kts"))
            .withPropertyName("buildScript_$module")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
