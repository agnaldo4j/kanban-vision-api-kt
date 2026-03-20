package com.kanbanvision.httpapi

import arrow.core.right
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.team.AbilityName
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.CardId
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
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
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

class CardRoutesTest {
    private val boardId = BoardId.generate()
    private val columnId = ColumnId.generate()
    private val cardId = CardId.generate()
    private val board = Board(id = boardId, name = "Test Board")
    private val column =
        Column(
            id = columnId,
            boardId = boardId,
            name = "To Do",
            position = 0,
            requiredAbility = AbilityName.PRODUCT_MANAGER,
        )
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
            single { CreateCardUseCase(get(), get(), get()) }
            single { GetCardUseCase(get()) }
            single { MoveCardUseCase(get()) }
            single { CreateColumnUseCase(get(), get()) }
            single { GetColumnUseCase(get()) }
            single { ListColumnsByBoardUseCase(get()) }
            single { mockk<DomainMetrics>(relaxed = true) }
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
                configureTestAuthentication()
                configureRateLimit()
                configureRouting()
            }

            coEvery { columnRepository.findById(columnId) } returns column.right()
            coEvery { boardRepository.findById(boardId) } returns board.right()
            coEvery { cardRepository.findByColumnId(any()) } returns emptyList<Card>().right()
            coEvery { cardRepository.save(any()) } answers { firstArg<Card>().right() }
            coEvery { cardRepository.findById(any()) } returns card.right()

            val response =
                client.post("/api/v1/cards") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"columnId":"${columnId.value}","title":"Task","description":"Do it"}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
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
                configureTestAuthentication()
                configureRateLimit()
                configureRouting()
            }

            val response =
                client.post("/api/v1/cards") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"columnId":"${columnId.value}","title":""}""")
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
            assertEquals("Card title must not be blank", firstError)
            assertNotNull(body["requestId"])
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
                configureTestAuthentication()
                configureRateLimit()
                configureRouting()
            }

            val targetColumnId = ColumnId.generate()
            val movedCard = card.moveTo(targetColumnId, 1)

            coEvery { cardRepository.updateCard(cardId, any()) } answers {
                secondArg<(Card) -> Card>()(card).right()
            }
            coEvery { cardRepository.findById(cardId) } returns movedCard.right()

            val response =
                client.patch("/api/v1/cards/${cardId.value}/move") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"columnId":"${targetColumnId.value}","position":1}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `GET cards by id returns card`() =
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

            coEvery { cardRepository.findById(cardId) } returns card.right()

            val response =
                client.get("/api/v1/cards/${cardId.value}") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(cardId.value, body["id"]?.jsonPrimitive?.content)
            assertEquals("Task", body["title"]?.jsonPrimitive?.content)
        }
}
