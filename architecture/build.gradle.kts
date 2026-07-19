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
    listOf("domain-common", "domain-kanban", "domain-simulation", "usecases", "sql_persistence", "http_api").forEach { module ->
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
