package com.kanbanvision.httpapi

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.team.AbilityName
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
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
import com.kanbanvision.usecases.repositories.StepRepository
import com.kanbanvision.usecases.step.CreateStepUseCase
import com.kanbanvision.usecases.step.GetStepUseCase
import com.kanbanvision.usecases.step.ListStepsByBoardUseCase
import io.ktor.client.request.get
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Suppress("LargeClass")
class StepRoutesTest {
    private val boardId = BoardId.generate()
    private val stepId = ColumnId.generate()
    private val board = Board(id = boardId, name = "Delivery Board")
    private val step =
        Step(
            id = stepId,
            boardId = boardId,
            name = "Analysis",
            position = 0,
            requiredAbility = AbilityName.PRODUCT_MANAGER,
        )

    private val boardRepository = mockk<BoardRepository>()
    private val cardRepository = mockk<CardRepository>()
    private val columnRepository = mockk<ColumnRepository>()
    private val stepRepository = mockk<StepRepository>()

    private val testModule =
        module {
            single<BoardRepository> { boardRepository }
            single<CardRepository> { cardRepository }
            single<ColumnRepository> { columnRepository }
            single<StepRepository> { stepRepository }
            single { CreateBoardUseCase(get()) }
            single { GetBoardUseCase(get()) }
            single { CreateCardUseCase(get(), get(), get()) }
            single { GetCardUseCase(get()) }
            single { MoveCardUseCase(get()) }
            single { CreateColumnUseCase(get(), get()) }
            single { GetColumnUseCase(get()) }
            single { ListColumnsByBoardUseCase(get()) }
            single { CreateStepUseCase(get(), get()) }
            single { GetStepUseCase(get()) }
            single { ListStepsByBoardUseCase(get(), get()) }
            single { mockk<DomainMetrics>(relaxed = true) }
        }

    @Test
    fun `POST steps creates step and returns 201`() =
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

            coEvery { boardRepository.findById(any()) } returns board.right()
            coEvery { stepRepository.findByBoardId(any()) } returns emptyList<Step>().right()
            coEvery { stepRepository.save(any()) } answers { firstArg<Step>().right() }
            coEvery { stepRepository.findById(any()) } returns step.right()

            val response =
                client.post("/api/v1/steps") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"boardId":"${boardId.value}","name":"Analysis","requiredAbility":"PRODUCT_MANAGER"}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Analysis", body["name"]?.jsonPrimitive?.content)
            assertEquals("PRODUCT_MANAGER", body["requiredAbility"]?.jsonPrimitive?.content)
            assertNotNull(body["id"])
        }

    @Test
    fun `GET steps by id returns step`() =
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

            coEvery { stepRepository.findById(stepId) } returns step.right()

            val response =
                client.get("/api/v1/steps/${stepId.value}") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(stepId.value, body["id"]?.jsonPrimitive?.content)
            assertEquals("Analysis", body["name"]?.jsonPrimitive?.content)
            assertEquals("PRODUCT_MANAGER", body["requiredAbility"]?.jsonPrimitive?.content)
        }

    @Test
    fun `GET boards boardId steps returns list`() =
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

            coEvery { stepRepository.findByBoardId(boardId) } returns listOf(step).right()
            coEvery { boardRepository.findById(boardId) } returns board.right()

            val response =
                client.get("/api/v1/boards/${boardId.value}/steps") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `GET steps by id returns 404 when not found`() =
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

            coEvery { stepRepository.findById(any()) } returns DomainError.StepNotFound("missing-step").left()

            val response =
                client.get("/api/v1/steps/missing-step") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["error"])
            assertNotNull(body["requestId"])
        }

    @Test
    fun `GET boards boardId steps returns 500 on persistence error`() =
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

            coEvery { stepRepository.findByBoardId(any()) } returns DomainError.PersistenceError("DB error").left()
            coEvery { boardRepository.findById(boardId) } returns board.right()

            val response =
                client.get("/api/v1/boards/${boardId.value}/steps") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["error"])
            assertNotNull(body["requestId"])
        }

    @Test
    fun `POST steps with blank name returns 400`() =
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
                client.post("/api/v1/steps") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"boardId":"${boardId.value}","name":"","requiredAbility":"PRODUCT_MANAGER"}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val firstError =
                body["errors"]
                    ?.jsonArray
                    ?.get(0)
                    ?.jsonPrimitive
                    ?.content
            assertEquals("Step name must not be blank", firstError)
            assertNotNull(body["requestId"])
        }

    @Test
    fun `POST steps returns 500 on persistence error`() =
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

            coEvery { boardRepository.findById(any()) } returns board.right()
            coEvery { stepRepository.findByBoardId(any()) } returns DomainError.PersistenceError("DB failure").left()

            val response =
                client.post("/api/v1/steps") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"boardId":"${boardId.value}","name":"Analysis","requiredAbility":"PRODUCT_MANAGER"}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Internal server error", body["error"]?.jsonPrimitive?.content)
            assertNotNull(body["requestId"])
        }

    @Test
    fun `GET boards boardId steps returns 404 when board not found`() =
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

            coEvery { boardRepository.findById(any()) } returns DomainError.BoardNotFound("missing-board").left()

            val response =
                client.get("/api/v1/boards/missing-board/steps") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["error"])
            assertNotNull(body["requestId"])
        }
}
