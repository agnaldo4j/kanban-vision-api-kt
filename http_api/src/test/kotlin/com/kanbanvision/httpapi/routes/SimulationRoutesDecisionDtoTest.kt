package com.kanbanvision.httpapi.routes

import arrow.core.right
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.httpapi.fixtureSnapshot
import com.kanbanvision.httpapi.withJwt
import com.kanbanvision.usecases.simulation.commands.RunDayCommand
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SimulationRoutesDecisionDtoTest {
    @Test
    fun `given block item decision with reason when running day then api accepts decision`() =
        testApplication {
            val mocks = SimulationApiMocks()
            coEvery { mocks.runDayUseCase.execute(any()) } returns fixtureSnapshot(simulationId = "sim-1").right()
            application { configureSimulationApi(mocks) }

            val response =
                client.post("/api/v1/simulations/sim-1/run") {
                    withJwt().invoke(this)
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[{"type":"BLOCK_ITEM","payload":{"cardId":"c-1","reason":"dep"}}]}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `given block item decision without reason when running day then command uses default reason`() =
        testApplication {
            val mocks = SimulationApiMocks()
            val commandSlot = slot<RunDayCommand>()
            coEvery { mocks.runDayUseCase.execute(capture(commandSlot)) } returns
                fixtureSnapshot(simulationId = "sim-1").right()
            application { configureSimulationApi(mocks) }

            client.post("/api/v1/simulations/sim-1/run") {
                withJwt().invoke(this)
                contentType(ContentType.Application.Json)
                setBody("""{"decisions":[{"type":"BLOCK_ITEM","payload":{"cardId":"c-1"}}]}""")
            }

            val decision = assertIs<Decision.BlockItem>(commandSlot.captured.decisions.first())
            assertEquals("blocked", decision.reason)
        }

    @Test
    fun `given unblock item decision when running day then api accepts decision`() =
        testApplication {
            val mocks = SimulationApiMocks()
            coEvery { mocks.runDayUseCase.execute(any()) } returns fixtureSnapshot(simulationId = "sim-1").right()
            application { configureSimulationApi(mocks) }

            val response =
                client.post("/api/v1/simulations/sim-1/run") {
                    withJwt().invoke(this)
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[{"type":"UNBLOCK_ITEM","payload":{"cardId":"c-1"}}]}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `given add item decision with service class when running day then api accepts decision`() =
        testApplication {
            val mocks = SimulationApiMocks()
            coEvery { mocks.runDayUseCase.execute(any()) } returns fixtureSnapshot(simulationId = "sim-1").right()
            application { configureSimulationApi(mocks) }

            val response =
                client.post("/api/v1/simulations/sim-1/run") {
                    withJwt().invoke(this)
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[{"type":"ADD_ITEM","payload":{"title":"T","serviceClass":"EXPEDITE"}}]}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `given add item decision without service class when running day then command defaults to standard`() =
        testApplication {
            val mocks = SimulationApiMocks()
            val commandSlot = slot<RunDayCommand>()
            coEvery { mocks.runDayUseCase.execute(capture(commandSlot)) } returns
                fixtureSnapshot(simulationId = "sim-1").right()
            application { configureSimulationApi(mocks) }

            client.post("/api/v1/simulations/sim-1/run") {
                withJwt().invoke(this)
                contentType(ContentType.Application.Json)
                setBody("""{"decisions":[{"type":"ADD_ITEM","payload":{"title":"T"}}]}""")
            }

            val decision = assertIs<Decision.AddItem>(commandSlot.captured.decisions.first())
            assertEquals(ServiceClass.STANDARD, decision.serviceClass)
        }

    @Test
    fun `given block item without cardId when running day then api returns bad request with field message`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response =
                client.post("/api/v1/simulations/sim-1/run") {
                    withJwt().invoke(this)
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[{"type":"BLOCK_ITEM","payload":{}}]}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("cardId"))
        }

    @Test
    fun `given add item without title when running day then api returns bad request with field message`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response =
                client.post("/api/v1/simulations/sim-1/run") {
                    withJwt().invoke(this)
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[{"type":"ADD_ITEM","payload":{}}]}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("title"))
        }

    @Test
    fun `given add item decision with invalid service class string when running day then command defaults to standard`() =
        testApplication {
            val mocks = SimulationApiMocks()
            val commandSlot = slot<RunDayCommand>()
            coEvery { mocks.runDayUseCase.execute(capture(commandSlot)) } returns
                fixtureSnapshot(simulationId = "sim-1").right()
            application { configureSimulationApi(mocks) }

            client.post("/api/v1/simulations/sim-1/run") {
                withJwt().invoke(this)
                contentType(ContentType.Application.Json)
                setBody("""{"decisions":[{"type":"ADD_ITEM","payload":{"title":"T","serviceClass":"INVALID_CLASS"}}]}""")
            }

            val decision = assertIs<Decision.AddItem>(commandSlot.captured.decisions.first())
            assertEquals(ServiceClass.STANDARD, decision.serviceClass)
        }

    @Test
    fun `given move item without cardId when running day then api returns bad request with field message`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response =
                client.post("/api/v1/simulations/sim-1/run") {
                    withJwt().invoke(this)
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[{"type":"MOVE_ITEM","payload":{}}]}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("cardId"))
        }

    @Test
    fun `given unblock item without cardId when running day then api returns bad request with field message`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response =
                client.post("/api/v1/simulations/sim-1/run") {
                    withJwt().invoke(this)
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[{"type":"UNBLOCK_ITEM","payload":{}}]}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("cardId"))
        }

    @Test
    fun `given unknown decision type when running day then api returns bad request with type message`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response =
                client.post("/api/v1/simulations/sim-1/run") {
                    withJwt().invoke(this)
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[{"type":"TOTALLY_UNKNOWN","payload":{}}]}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Unknown decision type"))
        }
}
