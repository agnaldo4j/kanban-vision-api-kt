package com.kanbanvision.httpapi.contracts

import arrow.core.right
import au.com.dius.pact.provider.junit5.HttpTestTarget
import au.com.dius.pact.provider.junit5.PactVerificationContext
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider
import au.com.dius.pact.provider.junitsupport.Consumer
import au.com.dius.pact.provider.junitsupport.Provider
import au.com.dius.pact.provider.junitsupport.loader.PactFolder
import com.kanbanvision.httpapi.fixtureSimulation
import com.kanbanvision.httpapi.fixtureSnapshot
import com.kanbanvision.httpapi.routes.SimulationApiMocks
import com.kanbanvision.httpapi.routes.configureSimulationApi
import com.kanbanvision.usecases.Page
import com.kanbanvision.usecases.simulation.CfdDataPoint
import com.kanbanvision.usecases.simulation.CfdResult
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.mockk.coEvery
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith

@Provider("kanban-vision-api")
@Consumer("simulation-consumer")
@PactFolder("src/test/resources/pacts")
@ExtendWith(PactVerificationInvocationContextProvider::class)
class SimulationsPactProviderTest {
    companion object {
        private val mocks = SimulationApiMocks()
        private lateinit var server: io.ktor.server.engine.EmbeddedServer<NettyApplicationEngine, *>
        private var port: Int = 0

        @JvmStatic
        @BeforeAll
        fun startServer() {
            val fixture = fixtureSimulation("sim-1")
            val snapshot = fixtureSnapshot(simulationId = "sim-1", day = 1)
            val cfdResult =
                CfdResult(
                    simulationId = "sim-1",
                    series = listOf(CfdDataPoint(day = 1, throughputCumulative = 1, wipCount = 1, blockedCount = 0)),
                )
            val page = Page(data = listOf(fixture), page = 1, size = 10, total = 1L)

            coEvery { mocks.createSimulationUseCase.execute(any()) } returns "sim-123".right()
            coEvery { mocks.getSimulationUseCase.execute(any()) } returns fixture.right()
            coEvery { mocks.listSimulationsUseCase.execute(any()) } returns page.right()
            coEvery { mocks.getSimulationDaysUseCase.execute(any()) } returns listOf(snapshot).right()
            coEvery { mocks.getSimulationCfdUseCase.execute(any()) } returns cfdResult.right()
            coEvery { mocks.runDayUseCase.execute(any()) } returns snapshot.right()
            coEvery { mocks.getDailySnapshotUseCase.execute(any()) } returns snapshot.right()

            server = embeddedServer(Netty, port = 0) { configureSimulationApi(mocks) }
            server.start(wait = false)
            port =
                runBlocking {
                    (server.engine as NettyApplicationEngine).resolvedConnectors().first().port
                }
        }

        @JvmStatic
        @AfterAll
        fun stopServer() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 500)
        }
    }

    @BeforeEach
    fun configureTarget(context: PactVerificationContext) {
        context.target = HttpTestTarget("localhost", port)
    }

    @TestTemplate
    fun verifyPactInteraction(context: PactVerificationContext) {
        context.verifyInteraction()
    }
}
