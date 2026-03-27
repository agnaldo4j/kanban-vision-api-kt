package com.kanbanvision.httpapi.routes

import com.kanbanvision.httpapi.TEST_JWT_AUDIENCE
import com.kanbanvision.httpapi.TEST_JWT_ISSUER
import com.kanbanvision.httpapi.TEST_JWT_REALM
import com.kanbanvision.httpapi.TEST_JWT_SECRET
import com.kanbanvision.httpapi.metrics.DomainMetrics
import com.kanbanvision.httpapi.plugins.RequestIdPlugin
import com.kanbanvision.httpapi.plugins.configureAuthentication
import com.kanbanvision.httpapi.plugins.configureSerialization
import com.kanbanvision.httpapi.plugins.configureStatusPages
import com.kanbanvision.usecases.simulation.CreateSimulationUseCase
import com.kanbanvision.usecases.simulation.GetDailySnapshotUseCase
import com.kanbanvision.usecases.simulation.GetSimulationCfdUseCase
import com.kanbanvision.usecases.simulation.GetSimulationDaysUseCase
import com.kanbanvision.usecases.simulation.GetSimulationUseCase
import com.kanbanvision.usecases.simulation.ListSimulationsUseCase
import com.kanbanvision.usecases.simulation.RunDayUseCase
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

internal data class SimulationApiMocks(
    val createSimulationUseCase: CreateSimulationUseCase = mockk(),
    val getSimulationUseCase: GetSimulationUseCase = mockk(),
    val runDayUseCase: RunDayUseCase = mockk(),
    val getDailySnapshotUseCase: GetDailySnapshotUseCase = mockk(),
    val listSimulationsUseCase: ListSimulationsUseCase = mockk(),
    val getSimulationDaysUseCase: GetSimulationDaysUseCase = mockk(),
    val getSimulationCfdUseCase: GetSimulationCfdUseCase = mockk(),
)

internal fun Application.configureSimulationApi(mocks: SimulationApiMocks) {
    install(Koin) {
        modules(
            module {
                single { mocks.createSimulationUseCase }
                single { mocks.getSimulationUseCase }
                single { mocks.runDayUseCase }
                single { mocks.getDailySnapshotUseCase }
                single { mocks.listSimulationsUseCase }
                single { mocks.getSimulationDaysUseCase }
                single { mocks.getSimulationCfdUseCase }
                single { DomainMetrics(SimpleMeterRegistry()) }
            },
        )
    }

    install(RequestIdPlugin)
    configureSerialization()
    configureStatusPages()
    configureAuthentication(TEST_JWT_SECRET, TEST_JWT_ISSUER, TEST_JWT_AUDIENCE, TEST_JWT_REALM)
    routing {
        authenticate("jwt-auth") {
            route("/api/v1") {
                simulationRoutes()
            }
        }
    }
}
