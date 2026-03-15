package com.kanbanvision.httpapi

import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.httpapi.plugins.configureOpenApi
import com.kanbanvision.httpapi.plugins.configureRouting
import com.kanbanvision.httpapi.plugins.configureSerialization
import com.kanbanvision.httpapi.plugins.configureStatusPages
import com.kanbanvision.usecases.board.CreateBoardUseCase
import com.kanbanvision.usecases.board.GetBoardUseCase
import com.kanbanvision.usecases.card.CreateCardUseCase
import com.kanbanvision.usecases.card.GetCardUseCase
import com.kanbanvision.usecases.card.MoveCardUseCase
import com.kanbanvision.usecases.repositories.BoardRepository
import com.kanbanvision.usecases.repositories.CardRepository
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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

class BoardRoutesTest {
    private val boardId = BoardId.generate()
    private val board = Board(id = boardId, name = "My Board")

    private val boardRepository = mockk<BoardRepository>()
    private val cardRepository = mockk<CardRepository>()

    private val testModule =
        module {
            single<BoardRepository> { boardRepository }
            single<CardRepository> { cardRepository }
            single { CreateBoardUseCase(get()) }
            single { GetBoardUseCase(get()) }
            single { CreateCardUseCase(get()) }
            single { GetCardUseCase(get()) }
            single { MoveCardUseCase(get()) }
        }

    @Test
    fun `POST boards creates board and returns 201`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureRouting()
            }

            coEvery { boardRepository.save(any()) } answers { firstArg() }
            coEvery { boardRepository.findById(any()) } returns board

            val response =
                client.post("/api/v1/boards") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"My Board"}""")
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
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureRouting()
            }

            val response =
                client.post("/api/v1/boards") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":""}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `GET boards by id returns board`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureRouting()
            }

            coEvery { boardRepository.findById(boardId) } returns board

            val response = client.get("/api/v1/boards/${boardId.value}")

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
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureRouting()
            }

            coEvery { boardRepository.findById(any()) } returns null

            val response = client.get("/api/v1/boards/nonexistent-id")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `unexpected repository exception returns 500`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureRouting()
            }

            coEvery { boardRepository.findById(any()) } throws RuntimeException("DB failure")

            val response = client.get("/api/v1/boards/some-id")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
}
