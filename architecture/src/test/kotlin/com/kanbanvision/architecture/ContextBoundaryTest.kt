package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * Fitness function das fronteiras de bounded context (docs/context-map.md, ADR-0026):
 * o Kanban Management BC (model.kanban + model.organization) é fornecedor e NÃO pode
 * depender do Simulation BC (model.simulation + domain.simulation). A dependência é
 * unidirecional simulation -> kanban; o inverso é acoplamento cross-context proibido.
 *
 * Cobre os dois vetores de acoplamento: `import` directives (caso idiomático) E
 * referências totalmente qualificadas no código (ex.: `com.kanbanvision.domain.simulation.X`
 * sem import) — que a resolução baseada em import do Konsist (import-check ou Layer API)
 * não pega. Comentários/KDoc são removidos para não gerar falso-positivo com `@link`s.
 */
class ContextBoundaryTest {
    private val kanbanManagementPackages =
        listOf(
            "com.kanbanvision.domain.model.kanban",
            "com.kanbanvision.domain.model.organization",
        )
    private val simulationPackages =
        listOf(
            "com.kanbanvision.domain.model.simulation",
            "com.kanbanvision.domain.simulation",
        )

    @Test
    fun `kanban management BC nao depende do simulation BC`() {
        Konsist
            .scopeFromProduction("domain")
            .files
            .filter { file -> kanbanManagementPackages.any { file.packagee?.name?.startsWith(it) == true } }
            .assertFalse { file ->
                val importsCrossBoundary =
                    file.imports.any { import -> simulationPackages.any { import.name.startsWith(it) } }
                val codeReferencesCrossBoundary =
                    simulationPackages.any { pkg -> stripComments(file.text).contains("$pkg.") }
                importsCrossBoundary || codeReferencesCrossBoundary
            }
    }

    private fun stripComments(source: String): String =
        source
            .replace(BLOCK_COMMENT, "")
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")

    private companion object {
        private val BLOCK_COMMENT = Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL))
    }
}
