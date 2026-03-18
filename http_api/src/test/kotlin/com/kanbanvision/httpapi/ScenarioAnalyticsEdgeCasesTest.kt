package com.kanbanvision.httpapi

import arrow.core.left
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.scenario.SimulationDay
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.httpapi.metrics.DomainMetrics
import com.kanbanvision.httpapi.plugins.configureObservability
import com.kanbanvision.httpapi.plugins.configureOpenApi
import com.kanbanvision.httpapi.plugins.configureRateLimit
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
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ScenarioAnalyticsEdgeCasesTest {
    private val scenarioId = ScenarioId("scenario-test-id")
    private val snapshotRepository = mockk<SnapshotRepository>()

    private val testModule =
        module {
            single<BoardRepository> { mockk(relaxed = true) }
            single<CardRepository> { mockk(relaxed = true) }
            single<ColumnRepository> { mockk(relaxed = true) }
            single<TenantRepository> { mockk(relaxed = true) }
            single<ScenarioRepository> { mockk(relaxed = true) }
            single<SnapshotRepository> { snapshotRepository }
            single { CreateBoardUseCase(get()) }
            single { GetBoardUseCase(get()) }
            single { CreateCardUseCase(get(), get(), get()) }
            single { GetCardUseCase(get()) }
            single { MoveCardUseCase(get()) }
            single { CreateColumnUseCase(get(), get()) }
            single { GetColumnUseCase(get()) }
            single { ListColumnsByBoardUseCase(get()) }
            single { CreateScenarioUseCase(get(), get()) }
            single { GetScenarioUseCase(get()) }
            single<com.kanbanvision.usecases.ports.SimulationEnginePort> {
                com.kanbanvision.usecases.ports
                    .DefaultSimulationEngine()
            }
            single { RunDayUseCase(get(), get(), get()) }
            single { GetDailySnapshotUseCase(get()) }
            single { GetMovementsByDayUseCase(get()) }
            single { GetFlowMetricsRangeUseCase(get()) }
            single { mockk<DomainMetrics>(relaxed = true) }
        }

    @Test
    fun `GET movements by day with non-integer day returns 400`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureObservability()
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureTestAuthentication()
                configureRateLimit()
                configureRouting()
            }

            val response =
                client.get("/api/v1/scenarios/${scenarioId.value}/days/abc/movements") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["errors"])
            assertNotNull(body["requestId"])
        }

    @Test
    fun `GET movements by day returns 500 on persistence error`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureObservability()
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureTestAuthentication()
                configureRateLimit()
                configureRouting()
            }

            coEvery { snapshotRepository.findByDay(scenarioId, SimulationDay(1)) } returns
                DomainError.PersistenceError("DB failure").left()

            val response =
                client.get("/api/v1/scenarios/${scenarioId.value}/days/1/movements") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["error"])
            assertNotNull(body["requestId"])
        }

    @Test
    fun `GET flow metrics range missing toDay returns 400`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureObservability()
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureTestAuthentication()
                configureRateLimit()
                configureRouting()
            }

            val response =
                client.get("/api/v1/scenarios/${scenarioId.value}/metrics?fromDay=1") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["errors"])
            assertNotNull(body["requestId"])
        }

    @Test
    fun `GET flow metrics range non-integer fromDay returns 400`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureObservability()
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureTestAuthentication()
                configureRateLimit()
                configureRouting()
            }

            val response =
                client.get("/api/v1/scenarios/${scenarioId.value}/metrics?fromDay=abc&toDay=5") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["errors"])
            assertNotNull(body["requestId"])
        }

    @Test
    fun `GET flow metrics range returns 500 on persistence error`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureObservability()
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureTestAuthentication()
                configureRateLimit()
                configureRouting()
            }

            coEvery { snapshotRepository.findAllByScenario(scenarioId) } returns
                DomainError.PersistenceError("DB failure").left()

            val response =
                client.get("/api/v1/scenarios/${scenarioId.value}/metrics?fromDay=1&toDay=5") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["error"])
            assertNotNull(body["requestId"])
        }
}
