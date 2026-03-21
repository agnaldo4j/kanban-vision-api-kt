package com.kanbanvision.httpapi.routes

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
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

class SimulationRoutesCreateAndGetTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `given valid create simulation request when posting to simulations endpoint then api returns created response`() =
        testApplication {
            val mocks = SimulationApiMocks()
            coEvery { mocks.createSimulationUseCase.execute(any()) } returns "sim-123".right()

            application { configureSimulationApi(mocks) }

            val response =
                client.post("/api/v1/simulations") {
                    withJwt().invoke(this)
                    contentType(ContentType.Application.Json)
                    setBody("""{"organizationId":"org-1","wipLimit":2,"teamSize":3,"seedValue":10}""")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val payload = json.decodeFromString<SimulationCreatedResponse>(response.bodyAsText())
            assertEquals("sim-123", payload.simulationId)
            coVerify(exactly = 1) { mocks.createSimulationUseCase.execute(any()) }
        }

    @Test
    fun `given invalid create simulation request when posting to simulations endpoint then api returns validation error`() =
        testApplication {
            val mocks = SimulationApiMocks()
            coEvery {
                mocks.createSimulationUseCase.execute(any())
            } returns DomainError.ValidationError("Organization id must not be blank").left()

            application { configureSimulationApi(mocks) }

            val response =
                client.post("/api/v1/simulations") {
                    withJwt().invoke(this)
                    contentType(ContentType.Application.Json)
                    setBody("""{"organizationId":"","wipLimit":1,"teamSize":1,"seedValue":10}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Organization id must not be blank"))
        }

    @Test
    fun `given malformed create request body when posting to simulations endpoint then api returns internal error`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response =
                client.post("/api/v1/simulations") {
                    withJwt().invoke(this)
                    contentType(ContentType.Application.Json)
                    setBody("""{"organizationId":"org-1","wipLimit":}""")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertTrue(response.bodyAsText().contains("Internal server error"))
            coVerify(exactly = 0) { mocks.createSimulationUseCase.execute(any()) }
        }

    @Test
    fun `given existing simulation when requesting by id then api returns simulation response`() =
        testApplication {
            val mocks = SimulationApiMocks()
            coEvery { mocks.getSimulationUseCase.execute(any()) } returns
                com.kanbanvision.httpapi
                    .fixtureSimulation("sim-1")
                    .right()

            application { configureSimulationApi(mocks) }

            val response = client.get("/api/v1/simulations/sim-1") { withJwt().invoke(this) }

            assertEquals(HttpStatusCode.OK, response.status)
            val payload = json.decodeFromString<SimulationResponse>(response.bodyAsText())
            assertEquals("sim-1", payload.simulationId)
            assertEquals("org-1", payload.organizationId)
        }

    @Test
    fun `given missing simulation when requesting by id then api returns not found response`() =
        testApplication {
            val mocks = SimulationApiMocks()
            coEvery {
                mocks.getSimulationUseCase.execute(any())
            } returns DomainError.SimulationNotFound("sim-missing").left()

            application { configureSimulationApi(mocks) }

            val response = client.get("/api/v1/simulations/sim-missing") { withJwt().invoke(this) }

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertTrue(response.bodyAsText().contains("Simulation not found"))
        }
}
