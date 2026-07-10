package com.kanbanvision.httpapi.routes

import arrow.core.left
import arrow.core.right
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.httpapi.TEST_JWT_AUDIENCE
import com.kanbanvision.httpapi.TEST_JWT_ISSUER
import com.kanbanvision.httpapi.TEST_JWT_REALM
import com.kanbanvision.httpapi.TEST_JWT_SECRET
import com.kanbanvision.httpapi.fixtureSimulation
import com.kanbanvision.httpapi.issueTestJwt
import com.kanbanvision.httpapi.plugins.configureAuthentication
import com.kanbanvision.httpapi.plugins.configureSerialization
import com.kanbanvision.httpapi.withJwt
import com.kanbanvision.usecases.Page
import com.kanbanvision.usecases.simulation.queries.ListSimulationsQuery
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.slot
import kotlinx.serialization.json.Json
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationListRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `given valid organization id when listing simulations then api returns paginated list`() =
        testApplication {
            val mocks = SimulationApiMocks()
            val simulations = listOf(fixtureSimulation("s1"), fixtureSimulation("s2"))
            coEvery { mocks.listSimulationsUseCase.execute(any()) } returns
                Page(data = simulations, page = 1, size = 20, total = 2L).right()

            application { configureSimulationApi(mocks) }

            // page e size numéricos válidos exercitam os ramos não-nulos de toIntOrNull.
            val response =
                client.get("/api/v1/simulations?page=1&size=20") {
                    withJwt().invoke(this)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            val payload = json.decodeFromString<SimulationListResponse>(body)
            assertEquals(2, payload.data.size)
            assertEquals(1, payload.page)
            assertEquals(20, payload.size)
            assertEquals(2L, payload.total)
        }

    @Test
    fun `given no organization query param when listing simulations then scope comes from the token`() =
        testApplication {
            val mocks = SimulationApiMocks()
            val querySlot = slot<ListSimulationsQuery>()
            coEvery { mocks.listSimulationsUseCase.execute(capture(querySlot)) } returns
                Page(data = emptyList<Simulation>(), page = 1, size = 20, total = 0L).right()

            application { configureSimulationApi(mocks) }

            val response =
                client.get("/api/v1/simulations") {
                    withJwt(issueTestJwt(organizationId = "org-1")).invoke(this)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("org-1", querySlot.captured.organizationId)
        }

    @Test
    fun `given spoofed organization query param when listing simulations then scope still comes from the token`() =
        testApplication {
            val mocks = SimulationApiMocks()
            val querySlot = slot<ListSimulationsQuery>()
            coEvery { mocks.listSimulationsUseCase.execute(capture(querySlot)) } returns
                Page(data = emptyList<Simulation>(), page = 1, size = 20, total = 0L).right()

            application { configureSimulationApi(mocks) }

            val response =
                client.get("/api/v1/simulations?organizationId=org-attacker") {
                    withJwt(issueTestJwt(organizationId = "org-1")).invoke(this)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            // O parâmetro de query forjado é ignorado — tenancy vem sempre do JWT (GAP-BJ).
            assertEquals("org-1", querySlot.captured.organizationId)
        }

    @Test
    fun `given validation error when listing simulations then api returns bad request`() =
        testApplication {
            val mocks = SimulationApiMocks()
            coEvery { mocks.listSimulationsUseCase.execute(any()) } returns
                DomainError.ValidationError("Page must be at least 1").left()

            application { configureSimulationApi(mocks) }

            val response =
                client.get("/api/v1/simulations?page=0") {
                    withJwt().invoke(this)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `given non-integer page parameter when listing simulations then api returns bad request`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response =
                client.get("/api/v1/simulations?page=abc") {
                    withJwt().invoke(this)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Invalid page"))
        }

    @Test
    fun `given non-integer size parameter when listing simulations then api returns bad request`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response =
                client.get("/api/v1/simulations?size=xyz") {
                    withJwt().invoke(this)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Invalid size"))
        }

    @Test
    fun `given unauthenticated request when listing simulations then api returns unauthorized`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response = client.get("/api/v1/simulations")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `given no principal when listing simulations then handler fails closed with unauthorized`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application {
                configureSerialization()
                // Plugin de auth instalado, mas a rota fica FORA de authenticate: o handler roda sem
                // principal e exercita o fail-closed de callerOrganizationId() (security.md A10).
                configureAuthentication(TEST_JWT_SECRET, TEST_JWT_ISSUER, TEST_JWT_AUDIENCE, TEST_JWT_REALM)
                routing {
                    get("/test/list-no-principal") { call.handleListSimulations(mocks.listSimulationsUseCase) }
                }
            }

            val response = client.get("/test/list-no-principal")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue(response.bodyAsText().contains("Missing organization context"))
        }

    @Test
    fun `given principal without organizationId claim when listing then handler fails closed`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application {
                configureSerialization()
                // Provider custom que aceita o token SEM o claim organizationId (contorna o validate
                // real, que rejeitaria) — exercita o ramo principal-presente-claim-ausente do guard.
                authentication {
                    jwt("no-claim") {
                        verifier(JWT.require(Algorithm.HMAC256(TEST_JWT_SECRET)).withIssuer(TEST_JWT_ISSUER).build())
                        validate { JWTPrincipal(it.payload) }
                    }
                }
                routing {
                    authenticate("no-claim") {
                        get("/test/list-no-claim") { call.handleListSimulations(mocks.listSimulationsUseCase) }
                    }
                }
            }
            val tokenWithoutOrg =
                JWT
                    .create()
                    .withIssuer(TEST_JWT_ISSUER)
                    .withSubject("tester")
                    .withExpiresAt(Date(System.currentTimeMillis() + 60_000L))
                    .sign(Algorithm.HMAC256(TEST_JWT_SECRET))

            val response =
                client.get("/test/list-no-claim") { header(HttpHeaders.Authorization, "Bearer $tokenWithoutOrg") }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue(response.bodyAsText().contains("Missing organization context"))
        }
}
