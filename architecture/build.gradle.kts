plugins {
    id("kanban.kotlin-common")
}

// Módulo test-only (ADR-0026): fitness functions de arquitetura com Konsist.
// Não há src/main — o Konsist analisa as FONTES dos demais módulos por path
// (scopeFromProject), então nenhuma dependência de produção é necessária.

dependencies {
    testImplementation("com.lemonappdev:konsist:0.17.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.1")
    // Konsist puxa JUnit Platform 1.x (linha JUnit 5); o launcher explícito na
    // versão do projeto evita classpath misto que impede o Gradle de iniciar o runner.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.1")
}

// Correção de cache (ADR-0026): o Konsist lê as fontes dos demais módulos em runtime,
// invisíveis aos inputs que o Gradle rastreia para esta task — sem isto, um PR que só
// muda domain/ reutilizaria um resultado verde stale do build cache e o gate não veria
// a violação. Declarar os src/main analisados como inputs invalida o cache corretamente.
tasks.test {
    listOf("domain", "usecases", "sql_persistence", "http_api").forEach { module ->
        inputs
            .dir(rootDir.resolve("$module/src/main/kotlin"))
            .withPropertyName("analyzedSources_$module")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
