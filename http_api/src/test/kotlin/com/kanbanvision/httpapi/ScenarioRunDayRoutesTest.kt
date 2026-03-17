package com.kanbanvision.httpapi

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.metrics.FlowMetrics
import com.kanbanvision.domain.model.policy.PolicySet
import com.kanbanvision.domain.model.scenario.DailySnapshot
import com.kanbanvision.domain.model.scenario.Scenario
import com.kanbanvision.domain.model.scenario.ScenarioConfig
import com.kanbanvision.domain.model.scenario.SimulationDay
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.domain.model.valueobjects.TenantId
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
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Suppress("LargeClass")
class ScenarioRunDayRoutesTest {
    private val scenarioId = ScenarioId("scenario-test-id")
    private val tenantId = TenantId("tenant-test-id")
    private val config = ScenarioConfig(wipLimit = 3, teamSize = 2, seedValue = 42L)
    private val scenario = Scenario(id = scenarioId, tenantId = tenantId, config = config)
    private val state =
        SimulationState(
            currentDay = SimulationDay(1),
            policySet = PolicySet(wipLimit = 3),
            items = emptyList(),
        )
    private val snapshot =
        DailySnapshot(
            scenarioId = scenarioId,
            day = SimulationDay(1),
            metrics = FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0),
            movements = emptyList(),
        )

    private val scenarioRepository = mockk<ScenarioRepository>()
    private val snapshotRepository = mockk<SnapshotRepository>()

    private val testModule =
        module {
            single<BoardRepository> { mockk(relaxed = true) }
            single<CardRepository> { mockk(relaxed = true) }
            single<ColumnRepository> { mockk(relaxed = true) }
            single<TenantRepository> { mockk(relaxed = true) }
            single<ScenarioRepository> { scenarioRepository }
            single<SnapshotRepository> { snapshotRepository }
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
        }

    @Test
    fun `POST scenarios run day returns 200 with snapshot`() =
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

            coEvery { scenarioRepository.findById(scenarioId) } returns scenario.right()
            coEvery { scenarioRepository.findState(scenarioId) } returns state.right()
            coEvery { snapshotRepository.findByDay(scenarioId, SimulationDay(1)) } returns null.right()
            coEvery { scenarioRepository.saveState(scenarioId, any()) } answers { secondArg<SimulationState>().right() }
            coEvery { snapshotRepository.save(any()) } answers { firstArg<DailySnapshot>().right() }

            val response =
                client.post("/api/v1/scenarios/${scenarioId.value}/run") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[]}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["day"])
            assertNotNull(body["metrics"])
        }

    @Test
    fun `POST scenarios run day returns 409 when day already executed`() =
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

            coEvery { scenarioRepository.findById(scenarioId) } returns scenario.right()
            coEvery { scenarioRepository.findState(scenarioId) } returns state.right()
            coEvery { snapshotRepository.findByDay(scenarioId, SimulationDay(1)) } returns snapshot.right()

            val response =
                client.post("/api/v1/scenarios/${scenarioId.value}/run") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[]}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["error"])
            assertNotNull(body["requestId"])
        }

    @Test
    fun `POST scenarios run day with unknown decision type returns 400`() =
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
                client.post("/api/v1/scenarios/${scenarioId.value}/run") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[{"type":"INVALID_TYPE","payload":{}}]}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["errors"])
            assertNotNull(body["requestId"])
        }

    @Test
    fun `POST scenarios run day returns 404 when scenario not found`() =
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

            coEvery { scenarioRepository.findById(scenarioId) } returns
                DomainError.ScenarioNotFound(scenarioId.value).left()

            val response =
                client.post("/api/v1/scenarios/${scenarioId.value}/run") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[]}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["error"])
            assertNotNull(body["requestId"])
        }

    @Test
    fun `POST scenarios run day returns 400 when use case returns InvalidDecision`() =
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

            coEvery { scenarioRepository.findById(scenarioId) } returns scenario.right()
            coEvery { scenarioRepository.findState(scenarioId) } returns state.right()
            coEvery { snapshotRepository.findByDay(scenarioId, SimulationDay(1)) } returns null.right()
            coEvery { scenarioRepository.saveState(scenarioId, any()) } answers {
                secondArg<SimulationState>().right()
            }
            coEvery { snapshotRepository.save(any()) } returns
                DomainError.InvalidDecision("Cannot apply decision in current state").left()

            val response =
                client.post("/api/v1/scenarios/${scenarioId.value}/run") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[]}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["error"])
            assertNotNull(body["requestId"])
        }

    @Test
    fun `POST scenarios run day returns 500 on persistence error`() =
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

            coEvery { scenarioRepository.findById(scenarioId) } returns
                DomainError.PersistenceError("DB failure").left()

            val response =
                client.post("/api/v1/scenarios/${scenarioId.value}/run") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"decisions":[]}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["error"])
            assertNotNull(body["requestId"])
        }
}
