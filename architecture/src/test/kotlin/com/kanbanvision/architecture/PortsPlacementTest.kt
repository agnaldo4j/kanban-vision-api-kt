package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fitness function do placement dos ports (ADR-0026): interfaces de repositório
 * vivem em usecases/repositories/ (NUNCA em domain — decisão de arquitetura), e
 * as implementações vivem em persistence/repositories/ implementando um port.
 */
class PortsPlacementTest {
    @Test
    fun `ports Repository residem em usecases repositories`() {
        Konsist
            .scopeFromProduction()
            .interfaces()
            .withNameEndingWith("Repository")
            .assertTrue { it.resideInPackage("com.kanbanvision.usecases.repositories") }
    }

    @Test
    fun `implementacoes de Repository residem em persistence repositories`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withNameEndingWith("Repository")
            .assertTrue { it.resideInPackage("com.kanbanvision.persistence.repositories") }
    }

    @Test
    fun `implementacoes de Repository implementam um port`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withNameEndingWith("Repository")
            .assertTrue { clazz ->
                clazz.parents().any { parent -> parent.name.endsWith("Repository") }
            }
    }
}
