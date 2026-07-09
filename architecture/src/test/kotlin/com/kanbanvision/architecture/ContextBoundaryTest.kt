package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * Fitness function das fronteiras de bounded context (docs/context-map.md, ADR-0026):
 * o Kanban Management BC (model.kanban + model.organization) é fornecedor e NÃO pode
 * depender do Simulation BC (model.simulation + domain.simulation). A dependência é
 * unidirecional simulation -> kanban; o inverso é acoplamento cross-context proibido.
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
                file.imports.any { import -> simulationPackages.any { import.name.startsWith(it) } }
            }
    }
}
