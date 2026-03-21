package com.kanbanvision.httpapi.routes

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.httpapi.fixtureSnapshot
import com.kanbanvision.httpapi.withJwt
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationRoutesExecutionAndSnapshotTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `given unknown decision type when running day then api returns bad request without invoking use case`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response =
                client.post("/api/v1/simulations/sim-1/run") {
                    withJwt().invoke(this)
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[{"type":"unknown","payload":{}}]}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Unknown decision type"))
            coVerify(exactly = 0) { mocks.runDayUseCase.execute(any()) }
        }

    @Test
    fun `given valid run day request when posting run endpoint then api returns daily snapshot response`() =
        testApplication {
            val mocks = SimulationApiMocks()
            val snapshot = fixtureSnapshot(simulationId = "sim-1", day = 1)
            coEvery { mocks.runDayUseCase.execute(any()) } returns snapshot.right()

            application { configureSimulationApi(mocks) }

            val response =
                client.post("/api/v1/simulations/sim-1/run") {
                    withJwt().invoke(this)
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[{"type":"move_item","payload":{"cardId":"card-1"}}]}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val payload = json.decodeFromString<DailySnapshotResponse>(response.bodyAsText())
            assertEquals("sim-1", payload.simulationId)
            assertEquals(1, payload.day)
            coVerify(exactly = 1) { mocks.runDayUseCase.execute(any()) }
        }

    @Test
    fun `given malformed run day body when posting run endpoint then api returns internal server error from status pages`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response =
                client.post("/api/v1/simulations/sim-1/run") {
                    withJwt().invoke(this)
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions": }""")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertTrue(response.bodyAsText().contains("Internal server error"))
            coVerify(exactly = 0) { mocks.runDayUseCase.execute(any()) }
        }

    @Test
    fun `given invalid day path parameter when loading daily snapshot then api returns bad request`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response = client.get("/api/v1/simulations/sim-1/days/not-a-number/snapshot") { withJwt().invoke(this) }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Day must be an integer"))
            coVerify(exactly = 0) { mocks.getDailySnapshotUseCase.execute(any()) }
        }

    @Test
    fun `given snapshot not found when loading daily snapshot then api returns not found`() =
        testApplication {
            val mocks = SimulationApiMocks()
            coEvery {
                mocks.getDailySnapshotUseCase.execute(any())
            } returns DomainError.SimulationNotFound("sim-1").left()

            application { configureSimulationApi(mocks) }

            val response = client.get("/api/v1/simulations/sim-1/days/1/snapshot") { withJwt().invoke(this) }

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertTrue(response.bodyAsText().contains("Simulation not found"))
        }

    @Test
    fun `given run day conflict when executing current day then api returns conflict`() =
        testApplication {
            val mocks = SimulationApiMocks()
            coEvery { mocks.runDayUseCase.execute(any()) } returns DomainError.DayAlreadyExecuted(2).left()

            application { configureSimulationApi(mocks) }

            val response =
                client.post("/api/v1/simulations/sim-1/run") {
                    withJwt().invoke(this)
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[]}""")
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertTrue(response.bodyAsText().contains("already executed"))
        }

    @Test
    fun `given existing snapshot when loading daily snapshot then api returns snapshot payload`() =
        testApplication {
            val mocks = SimulationApiMocks()
            coEvery { mocks.getDailySnapshotUseCase.execute(any()) } returns fixtureSnapshot(simulationId = "sim-1", day = 3).right()
            application { configureSimulationApi(mocks) }

            val response = client.get("/api/v1/simulations/sim-1/days/3/snapshot") { withJwt().invoke(this) }

            assertEquals(HttpStatusCode.OK, response.status)
            val payload = json.decodeFromString<DailySnapshotResponse>(response.bodyAsText())
            assertEquals("sim-1", payload.simulationId)
            assertEquals(3, payload.day)
        }

    @Test
    fun `given missing auth token when calling protected endpoint then api returns unauthorized`() =
        testApplication {
            application { configureSimulationApi(SimulationApiMocks()) }

            val response = client.get("/api/v1/simulations/sim-1")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue(response.bodyAsText().contains("Token inválido ou ausente"))
        }
}
