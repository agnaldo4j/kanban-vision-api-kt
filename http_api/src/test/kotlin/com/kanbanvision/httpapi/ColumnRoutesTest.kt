package com.kanbanvision.httpapi

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
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
class ColumnRoutesTest {
    private val boardId = BoardId.generate()
    private val columnId = ColumnId.generate()
    private val column = Column(id = columnId, boardId = boardId, name = "To Do", position = 0)

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
            single { CreateCardUseCase(get()) }
            single { GetCardUseCase(get()) }
            single { MoveCardUseCase(get()) }
            single { CreateColumnUseCase(get()) }
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
                configureRouting()
            }

            coEvery { columnRepository.findByBoardId(any()) } returns emptyList<Column>().right()
            coEvery { columnRepository.save(any()) } answers { firstArg<Column>().right() }
            coEvery { columnRepository.findById(any()) } returns column.right()

            val response =
                client.post("/api/v1/columns") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"boardId":"${boardId.value}","name":"To Do"}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("To Do", body["name"]?.jsonPrimitive?.content)
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
                configureRouting()
            }

            val response =
                client.post("/api/v1/columns") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"boardId":"${boardId.value}","name":""}""")
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
            assertEquals("Column name must not be blank", firstError)
            assertNotNull(body["requestId"])
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
                configureRouting()
            }

            coEvery { columnRepository.findById(columnId) } returns column.right()

            val response =
                client.get("/api/v1/columns/${columnId.value}") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(columnId.value, body["id"]?.jsonPrimitive?.content)
            assertEquals("To Do", body["name"]?.jsonPrimitive?.content)
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
                configureRouting()
            }

            coEvery { columnRepository.findByBoardId(any()) } returns DomainError.PersistenceError("DB failure").left()

            val response =
                client.post("/api/v1/columns") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"boardId":"${boardId.value}","name":"To Do"}""")
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
                configureRouting()
            }

            coEvery { columnRepository.findByBoardId(boardId) } returns listOf(column).right()

            val response =
                client.get("/api/v1/boards/${boardId.value}/columns") {
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
                configureRouting()
            }

            coEvery { columnRepository.findByBoardId(any()) } returns
                DomainError.PersistenceError("DB failure").left()

            val response =
                client.get("/api/v1/boards/${boardId.value}/columns") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["error"])
            assertNotNull(body["requestId"])
        }
}
