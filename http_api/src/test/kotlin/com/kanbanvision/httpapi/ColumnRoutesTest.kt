package com.kanbanvision.httpapi

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Step
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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Suppress("LargeClass")
class ColumnRoutesTest {
    private val boardId = UUID.randomUUID().toString()
    private val columnId = UUID.randomUUID().toString()
    private val board = Board(id = boardId, name = "Test Board")
    private val column =
        Step(
            id = columnId,
            boardId = boardId,
            name = "To Do",
            position = 0,
            requiredAbility = AbilityName.PRODUCT_MANAGER,
        )

    private val boardRepository = mockk<BoardRepository>()
    private val cardRepository = mockk<CardRepository>()
    private val columnRepository = mockk<ColumnRepository>()

    private val testModule =
        module {
            single<BoardRepository> { boardRepository }
            single<CardRepository> { cardRepository }
            single<ColumnRepository> { columnRepository }
            single { CreateBoardUseCase(get()) }
            single { GetBoardUseCase(get()) }
            single { CreateCardUseCase(get(), get(), get()) }
            single { GetCardUseCase(get()) }
            single { MoveCardUseCase(get()) }
            single { CreateColumnUseCase(get(), get()) }
            single { GetColumnUseCase(get()) }
            single { ListColumnsByBoardUseCase(get()) }
            single { mockk<DomainMetrics>(relaxed = true) }
        }

    @Test
    fun `POST columns creates column and returns 201`() =
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
            coEvery { columnRepository.findByBoardId(any()) } returns emptyList<Step>().right()
            coEvery { columnRepository.save(any()) } answers { firstArg<Step>().right() }
            coEvery { columnRepository.findById(any()) } returns column.right()

            val response =
                client.post("/api/v1/columns") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"boardId":"$boardId","name":"To Do","requiredAbility":"PRODUCT_MANAGER"}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("To Do", body["name"]?.jsonPrimitive?.content)
            assertEquals("PRODUCT_MANAGER", body["requiredAbility"]?.jsonPrimitive?.content)
            assertNotNull(body["id"])
        }

    @Test
    fun `POST columns with blank name returns 400`() =
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
                client.post("/api/v1/columns") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"boardId":"$boardId","name":"","requiredAbility":"PRODUCT_MANAGER"}""")
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
    fun `POST columns without requiredAbility defaults to developer`() =
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
            coEvery { columnRepository.findByBoardId(any()) } returns emptyList<Step>().right()
            coEvery { columnRepository.save(any()) } answers { firstArg<Step>().right() }
            coEvery { columnRepository.findById(any()) } returns column.copy(requiredAbility = AbilityName.DEVELOPER).right()

            val response =
                client.post("/api/v1/columns") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"boardId":"$boardId","name":"To Do"}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("DEVELOPER", body["requiredAbility"]?.jsonPrimitive?.content)
        }

    @Test
    fun `GET columns by id returns column`() =
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

            coEvery { columnRepository.findById(columnId) } returns column.right()

            val response =
                client.get("/api/v1/columns/$columnId") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(columnId, body["id"]?.jsonPrimitive?.content)
            assertEquals("To Do", body["name"]?.jsonPrimitive?.content)
            assertEquals("PRODUCT_MANAGER", body["requiredAbility"]?.jsonPrimitive?.content)
        }

    @Test
    fun `GET columns by id returns 404 when not found`() =
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

            coEvery { columnRepository.findById(any()) } returns DomainError.ColumnNotFound("nonexistent-id").left()

            val response =
                client.get("/api/v1/columns/nonexistent-id") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["error"])
            assertNotNull(body["requestId"])
        }

    @Test
    fun `unexpected repository error in column creation returns 500`() =
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

            coEvery { columnRepository.findByBoardId(any()) } returns DomainError.PersistenceError("DB failure").left()

            val response =
                client.post("/api/v1/columns") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"boardId":"$boardId","name":"To Do","requiredAbility":"PRODUCT_MANAGER"}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Internal server error", body["error"]?.jsonPrimitive?.content)
            assertNotNull(body["requestId"])
        }

    @Test
    fun `GET boards boardId columns returns list`() =
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

            coEvery { columnRepository.findByBoardId(boardId) } returns listOf(column).right()

            val response =
                client.get("/api/v1/boards/$boardId/columns") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `GET boards boardId columns returns 404 when board not found`() =
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

            coEvery { columnRepository.findByBoardId(any()) } returns
                DomainError.BoardNotFound("nonexistent-id").left()

            val response =
                client.get("/api/v1/boards/nonexistent-id/columns") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["error"])
            assertNotNull(body["requestId"])
        }

    @Test
    fun `GET boards boardId columns returns 500 on persistence error`() =
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

            coEvery { columnRepository.findByBoardId(any()) } returns
                DomainError.PersistenceError("DB failure").left()

            val response =
                client.get("/api/v1/boards/$boardId/columns") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["error"])
            assertNotNull(body["requestId"])
        }
}
