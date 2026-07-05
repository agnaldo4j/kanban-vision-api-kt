package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import org.junit.jupiter.api.Test

/**
 * Fitness function da Dependency Rule (ADR-0026, arquitetura hexagonal):
 * http_api -> usecases -> domain; sql_persistence -> domain + usecases;
 * http_api -> sql_persistence somente para wiring de DI (o Detekt ForbiddenImport
 * restringe imports de repositórios concretos ao AppModule). A direção única das
 * dependências também garante ausência de ciclos entre camadas.
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

    @Test
    fun `httpApi e a camada mais externa - pode depender das internas e ninguem depende dela`() {
        Konsist.scopeFromProduction().assertArchitecture {
            // persistence permitida apenas para wiring de DI (AppModule) —
            // granularidade de classe é garantida pelo Detekt ForbiddenImport.
            httpApi.dependsOn(domain, useCases, persistence)
        }
    }
}
