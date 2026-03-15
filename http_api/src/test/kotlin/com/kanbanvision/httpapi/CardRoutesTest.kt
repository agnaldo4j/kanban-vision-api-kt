package com.kanbanvision.httpapi

import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
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
import io.ktor.client.request.patch
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

class CardRoutesTest {
    private val columnId = ColumnId.generate()
    private val cardId = CardId.generate()
    private val card = Card(id = cardId, columnId = columnId, title = "Task", description = "Do it", position = 0)

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
        }

    @Test
    fun `POST cards creates card and returns 201`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureObservability()
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureRouting()
            }

            coEvery { cardRepository.findByColumnId(any()) } returns emptyList()
            coEvery { cardRepository.save(any()) } answers { firstArg() }
            coEvery { cardRepository.findById(any()) } returns card

            val response =
                client.post("/api/v1/cards") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"columnId":"${columnId.value}","title":"Task","description":"Do it"}""")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Task", body["title"]?.jsonPrimitive?.content)
            assertEquals(columnId.value, body["columnId"]?.jsonPrimitive?.content)
        }

    @Test
    fun `POST cards with blank title returns 400`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureObservability()
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureRouting()
            }

            val response =
                client.post("/api/v1/cards") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"columnId":"${columnId.value}","title":""}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `PATCH cards move returns updated card`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureObservability()
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureRouting()
            }

            val targetColumnId = ColumnId.generate()
            val movedCard = card.moveTo(targetColumnId, 1)

            coEvery { cardRepository.findById(cardId) } returns card
            coEvery { cardRepository.save(any()) } answers { firstArg() }
            coEvery { cardRepository.findById(cardId) } returnsMany listOf(card, movedCard)

            val response =
                client.patch("/api/v1/cards/${cardId.value}/move") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"columnId":"${targetColumnId.value}","position":1}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `PATCH cards move returns 404 when card not found`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureObservability()
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureRouting()
            }

            coEvery { cardRepository.findById(any()) } returns null

            val response =
                client.patch("/api/v1/cards/nonexistent/move") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"columnId":"${columnId.value}","position":0}""")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `unexpected repository exception in card creation returns 500`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureObservability()
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureRouting()
            }

            coEvery { cardRepository.findByColumnId(any()) } throws RuntimeException("DB failure")

            val response =
                client.post("/api/v1/cards") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"columnId":"${columnId.value}","title":"Task"}""")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
}
