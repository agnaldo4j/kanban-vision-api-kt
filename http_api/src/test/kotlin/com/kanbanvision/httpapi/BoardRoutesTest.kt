package com.kanbanvision.httpapi

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.valueobjects.BoardId
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("LargeClass")
class BoardRoutesTest {
    private val boardId = BoardId.generate()
    private val board = Board(id = boardId, name = "My Board")

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
    fun `POST boards creates board and returns 201`() =
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

            coEvery { boardRepository.save(any()) } answers { firstArg<Board>().right() }
            coEvery { boardRepository.findById(any()) } returns board.right()

            val response =
                client.post("/api/v1/boards") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"My Board"}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("My Board", body["name"]?.jsonPrimitive?.content)
            assertNotNull(body["id"])
        }

    @Test
    fun `POST boards with blank name returns 400`() =
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
                client.post("/api/v1/boards") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":""}""")
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
            assertEquals("Board name must not be blank", firstError)
            assertNotNull(body["requestId"])
        }

    @Test
    fun `GET boards by id returns board`() =
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

            coEvery { boardRepository.findById(boardId) } returns board.right()

            val response =
                client.get("/api/v1/boards/${boardId.value}") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(boardId.value, body["id"]?.jsonPrimitive?.content)
            assertEquals("My Board", body["name"]?.jsonPrimitive?.content)
        }

    @Test
    fun `GET boards by id returns 404 when not found`() =
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

            coEvery { boardRepository.findById(any()) } returns DomainError.BoardNotFound("nonexistent-id").left()

            val response =
                client.get("/api/v1/boards/nonexistent-id") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["error"])
            assertNotNull(body["requestId"])
        }

    @Test
    fun `response contains X-Request-ID header`() =
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

            coEvery { boardRepository.save(any()) } answers { firstArg<Board>().right() }
            coEvery { boardRepository.findById(any()) } returns board.right()

            val response =
                client.post("/api/v1/boards") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"My Board"}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            val requestId = response.headers["X-Request-ID"]
            assertNotNull(requestId)
            assertTrue(requestId.isNotBlank())
        }

    @Test
    fun `X-Request-ID from request is propagated to response`() =
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

            coEvery { boardRepository.save(any()) } answers { firstArg<Board>().right() }
            coEvery { boardRepository.findById(any()) } returns board.right()

            val correlationId = "test-correlation-id-123"
            val response =
                client.post("/api/v1/boards") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"My Board"}""")
                    headers.append("X-Request-ID", correlationId)
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(correlationId, response.headers["X-Request-ID"])
        }

    @Test
    fun `request to api v1 without token returns 401`() =
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

            val response = client.get("/api/v1/boards/any-id")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Token inválido ou ausente", body["error"]?.jsonPrimitive?.content)
            assertNotNull(body["requestId"])
        }

    @Test
    fun `unexpected repository error returns 500`() =
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

            coEvery { boardRepository.findById(any()) } returns DomainError.PersistenceError("DB failure").left()

            val response =
                client.get("/api/v1/boards/some-id") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Internal server error", body["error"]?.jsonPrimitive?.content)
            assertNotNull(body["requestId"])
        }
}
