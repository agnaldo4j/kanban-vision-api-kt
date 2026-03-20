package com.kanbanvision.httpapi.plugins

import com.kanbanvision.httpapi.TEST_JWT_AUDIENCE
import com.kanbanvision.httpapi.TEST_JWT_ISSUER
import com.kanbanvision.httpapi.TEST_JWT_REALM
import com.kanbanvision.httpapi.TEST_JWT_SECRET
import com.kanbanvision.httpapi.metrics.DomainMetrics
import com.kanbanvision.httpapi.routes.SimulationApiMocks
import com.kanbanvision.usecases.simulation.CreateSimulationUseCase
import com.kanbanvision.usecases.simulation.GetDailySnapshotUseCase
import com.kanbanvision.usecases.simulation.GetSimulationUseCase
import com.kanbanvision.usecases.simulation.RunDayUseCase
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutingPluginTest {
    @Test
    fun `given routing plugin when application starts then health route is public and api route is protected`() =
        testApplication {
            application {
                installRoutingDependencies(SimulationApiMocks())
                configureRouting()
            }

            val health = client.get("/health")
            val protected = client.get("/api/v1/simulations/sim-1")

            assertEquals(HttpStatusCode.OK, health.status)
            assertEquals(HttpStatusCode.Unauthorized, protected.status)
        }

    private fun Application.installRoutingDependencies(mocks: SimulationApiMocks) {
        install(Koin) {
            modules(
                module {
                    single<CreateSimulationUseCase> { mocks.createSimulationUseCase }
                    single<GetSimulationUseCase> { mocks.getSimulationUseCase }
                    single<RunDayUseCase> { mocks.runDayUseCase }
                    single<GetDailySnapshotUseCase> { mocks.getDailySnapshotUseCase }
                    single { DomainMetrics(SimpleMeterRegistry()) }
                },
            )
        }
        install(RequestIdPlugin)
        configureSerialization()
        configureStatusPages()
        configureAuthentication(
            TEST_JWT_SECRET,
            TEST_JWT_ISSUER,
            TEST_JWT_AUDIENCE,
            TEST_JWT_REALM,
        )
    }
}
