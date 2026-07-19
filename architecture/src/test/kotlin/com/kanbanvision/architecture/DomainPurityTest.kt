package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * Fitness function da pureza do domain (ADR-0026): src/main de domain/ não pode
 * importar frameworks nem infraestrutura — hoje o módulo usa apenas stdlib,
 * java.time e java.util (arrow-kt é permitido por ser tipo funcional puro).
 */
class DomainPurityTest {
    private val forbiddenImportPrefixes =
        listOf(
            "io.ktor.",
            "org.koin.",
            "org.jetbrains.exposed.",
            "java.sql.",
            "javax.",
            "jakarta.",
            "org.slf4j.",
            "kotlinx.serialization.",
            "com.zaxxer.",
            "org.flywaydb.",
            "io.micrometer.",
        )

    @Test
    fun `domain nao importa frameworks nem infraestrutura`() {
        // scopeFromProduction() + filtro por prefixo de pacote (não por nome de módulo): pós-extração
        // faseada (ADR-0038) o domínio se espalha por :domain-common/:domain-kanban/:domain. Só esses
        // três usam o prefixo com.kanbanvision.domain (usecases/persistence/httpapi têm prefixos próprios),
        // então o filtro cobre exatamente os módulos de domínio e sobrevive a CK/CL.
        Konsist
            .scopeFromProduction()
            .files
            .filter { file -> file.packagee?.name?.startsWith("com.kanbanvision.domain") == true }
            .assertFalse { file ->
                file.imports.any { import ->
                    forbiddenImportPrefixes.any { prefix -> import.name.startsWith(prefix) }
                }
            }
    }
}
