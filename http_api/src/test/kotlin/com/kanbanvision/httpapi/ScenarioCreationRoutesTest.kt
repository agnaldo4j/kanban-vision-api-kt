package com.kanbanvision.httpapi

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.scenario.Scenario
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.valueobjects.TenantId
import com.kanbanvision.httpapi.metrics.DomainMetrics
import com.kanbanvision.httpapi.plugins.configureObservability
import com.kanbanvision.httpapi.plugins.configureOpenApi
import com.kanbanvision.httpapi.plugins.configureRouting
import com.kanbanvision.httpapi.plugins.configureSerialization
import com.kanbanvision.httpapi.plugins.configureStatusPages
import com.kanbanvision.usecases.board.CreateBoardUseCase
import com.kanbanvision.usecases.board.GetBoardUseCase
import com.kanbanvision.usecases.card.CreateCardUseCase
import com.kanbanvision.usecases.card.GetCardUseCase
import com.kanbanvision.usecases.card.MoveCardUseCase
import com.kanbanvision.usecases.column.CreateColumnUseCase
import com.kanbanvision.usecases.column.GetColumnUseCase
import com.kanbanvision.usecases.column.ListColumnsByBoardUseCase
import com.kanbanvision.usecases.repositories.BoardRepository
import com.kanbanvision.usecases.repositories.CardRepository
import com.kanbanvision.usecases.repositories.ColumnRepository
import com.kanbanvision.usecases.repositories.ScenarioRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.repositories.TenantRepository
import com.kanbanvision.usecases.scenario.CreateScenarioUseCase
import com.kanbanvision.usecases.scenario.GetDailySnapshotUseCase
import com.kanbanvision.usecases.scenario.GetFlowMetricsRangeUseCase
import com.kanbanvision.usecases.scenario.GetMovementsByDayUseCase
import com.kanbanvision.usecases.scenario.GetScenarioUseCase
import com.kanbanvision.usecases.scenario.RunDayUseCase
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ScenarioCreationRoutesTest {
    private val tenantId = TenantId("tenant-test-id")

    private val tenantRepository = mockk<TenantRepository>()
    private val scenarioRepository = mockk<ScenarioRepository>()

    private val testModule =
        module {
            single<BoardRepository> { mockk(relaxed = true) }
            single<CardRepository> { mockk(relaxed = true) }
            single<ColumnRepository> { mockk(relaxed = true) }
            single<TenantRepository> { tenantRepository }
            single<ScenarioRepository> { scenarioRepository }
            single<SnapshotRepository> { mockk(relaxed = true) }
            single { CreateBoardUseCase(get()) }
            single { GetBoardUseCase(get()) }
            single { CreateCardUseCase(get()) }
            single { GetCardUseCase(get()) }
            single { MoveCardUseCase(get()) }
            single { CreateColumnUseCase(get()) }
            single { GetColumnUseCase(get()) }
            single { ListColumnsByBoardUseCase(get()) }
            single { CreateScenarioUseCase(get(), get()) }
            single { GetScenarioUseCase(get()) }
            single { RunDayUseCase(get(), get()) }
            single { GetDailySnapshotUseCase(get()) }
            single { GetMovementsByDayUseCase(get()) }
            single { GetFlowMetricsRangeUseCase(get()) }
            single { mockk<DomainMetrics>(relaxed = true) }
        }

    @Test
    fun `POST scenarios creates scenario and returns 201`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureObservability()
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureTestAuthentication()
                configureRouting()
            }

            coEvery { tenantRepository.findById(tenantId) } returns
                com.kanbanvision.domain.model.tenant
                    .Tenant(id = tenantId, name = "Test")
                    .right()
            coEvery { scenarioRepository.save(any()) } answers { firstArg<Scenario>().right() }
            coEvery { scenarioRepository.saveState(any(), any()) } answers { secondArg<SimulationState>().right() }

            val response =
                client.post("/api/v1/scenarios") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"tenantId":"${tenantId.value}","wipLimit":3,"teamSize":2,"seedValue":42}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["scenarioId"]?.jsonPrimitive?.content)
        }

    @Test
    fun `POST scenarios with blank tenantId returns 400`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureObservability()
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureTestAuthentication()
                configureRouting()
            }

            val response =
                client.post("/api/v1/scenarios") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"tenantId":"","wipLimit":3,"teamSize":2,"seedValue":42}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["errors"])
            assertNotNull(body["requestId"])
        }

    @Test
    fun `POST scenarios when tenant not found returns 404`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureObservability()
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureTestAuthentication()
                configureRouting()
            }

            coEvery { tenantRepository.findById(tenantId) } returns DomainError.TenantNotFound(tenantId.value).left()

            val response =
                client.post("/api/v1/scenarios") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"tenantId":"${tenantId.value}","wipLimit":3,"teamSize":2,"seedValue":42}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["error"])
            assertNotNull(body["requestId"])
        }

    @Test
    fun `POST scenarios returns 500 on persistence error`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureObservability()
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureTestAuthentication()
                configureRouting()
            }

            coEvery { tenantRepository.findById(tenantId) } returns DomainError.PersistenceError("DB down").left()

            val response =
                client.post("/api/v1/scenarios") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"tenantId":"${tenantId.value}","wipLimit":3,"teamSize":2,"seedValue":42}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Internal server error", body["error"]?.jsonPrimitive?.content)
            assertNotNull(body["requestId"])
        }
}
