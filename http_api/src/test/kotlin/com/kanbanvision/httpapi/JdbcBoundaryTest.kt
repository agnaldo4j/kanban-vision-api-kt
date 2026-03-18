package com.kanbanvision.httpapi

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Architectural boundary test: enforces that Jdbc*Repository implementations
 * are only imported inside AppModule.kt (the DI wiring point).
 *
 * Any other file importing from com.kanbanvision.persistence.repositories.Jdbc*
 * violates the Ports-and-Adapters boundary — routes and use cases must depend
 * only on the repository interfaces in usecases/repositories/, never on JDBC adapters.
 */
class JdbcBoundaryTest {
    private val rootDir: File
        get() {
            var dir = File(".").canonicalFile
            while (!File(dir, "settings.gradle.kts").exists()) {
                dir = dir.parentFile ?: error("Root project directory not found")
            }
            return dir
        }

    private val jdbcImportPattern = Regex("""^import com\.kanbanvision\.persistence\.repositories\.Jdbc""")

    @Test
    fun `Jdbc repositories are only imported in AppModule`() {
        val violations =
            rootDir
                .walkTopDown()
                .filter { it.extension == "kt" && "src/main/kotlin" in it.path }
                .filter { it.name != "AppModule.kt" }
                .filter { file -> file.readLines().any { jdbcImportPattern.containsMatchIn(it) } }
                .map { it.relativeTo(rootDir).path }
                .toList()

        assertTrue(violations.isEmpty()) {
            "Forbidden: Jdbc*Repository imported outside AppModule.kt — " +
                "routes and use cases must depend only on repository interfaces:\n" +
                violations.joinToString("\n") { "  - $it" }
        }
    }
}
