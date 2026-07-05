package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import org.junit.jupiter.api.Test

/**
 * Fitness function da Dependency Rule (ADR-0026, arquitetura hexagonal):
 * http_api -> usecases -> domain; sql_persistence -> domain + usecases.
 * A direção única das dependências também garante ausência de ciclos entre camadas.
 */
class HexagonalArchitectureTest {
    private val domain = Layer("Domain", "com.kanbanvision.domain..")
    private val useCases = Layer("UseCases", "com.kanbanvision.usecases..")
    private val persistence = Layer("Persistence", "com.kanbanvision.persistence..")
    private val httpApi = Layer("HttpApi", "com.kanbanvision.httpapi..")

    @Test
    fun `domain nao depende de nenhuma outra camada`() {
        Konsist.scopeFromProduction().assertArchitecture {
            domain.dependsOnNothing()
        }
    }

    @Test
    fun `usecases depende somente de domain`() {
        Konsist.scopeFromProduction().assertArchitecture {
            useCases.dependsOn(domain)
            useCases.doesNotDependOn(persistence, httpApi)
        }
    }

    @Test
    fun `persistence depende somente de domain e usecases`() {
        Konsist.scopeFromProduction().assertArchitecture {
            persistence.dependsOn(domain, useCases)
            persistence.doesNotDependOn(httpApi)
        }
    }
}
