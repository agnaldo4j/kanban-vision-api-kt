package com.kanbanvision.httpapi

import arrow.core.right
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Card
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
import com.kanbanvision.usecases.repositories.BoardRepository
import com.kanbanvision.usecases.repositories.CardRepository
import com.kanbanvision.usecases.repositories.StepRepository
import com.kanbanvision.usecases.step.CreateStepUseCase
import com.kanbanvision.usecases.step.GetStepUseCase
import com.kanbanvision.usecases.step.ListStepsByBoardUseCase
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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CardRoutesTest {
    private val boardId = UUID.randomUUID().toString()
    private val stepId = UUID.randomUUID().toString()
    private val cardId = UUID.randomUUID().toString()
    private val board = Board(id = boardId, name = "Test Board")
    private val step =
        Step(
            id = stepId,
            boardId = boardId,
            name = "To Do",
            position = 0,
            requiredAbility = AbilityName.PRODUCT_MANAGER,
        )
    private val card = Card(id = cardId, stepId = stepId, title = "Task", description = "Do it", position = 0)

    private val boardRepository = mockk<BoardRepository>()
    private val cardRepository = mockk<CardRepository>()
    private val stepRepository = mockk<StepRepository>()

    private val testModule =
        module {
            single<BoardRepository> { boardRepository }
            single<CardRepository> { cardRepository }
            single<StepRepository> { stepRepository }
            single { CreateBoardUseCase(get()) }
            single { GetBoardUseCase(get()) }
            single { CreateCardUseCase(get(), get(), get()) }
            single { GetCardUseCase(get()) }
            single { MoveCardUseCase(get()) }
            single { CreateStepUseCase(get(), get()) }
            single { GetStepUseCase(get()) }
            single { ListStepsByBoardUseCase(get(), get()) }
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

            coEvery { stepRepository.findById(stepId) } returns step.right()
            coEvery { boardRepository.findById(boardId) } returns board.right()
            coEvery { cardRepository.findByStepId(any()) } returns emptyList<Card>().right()
            coEvery { cardRepository.save(any()) } answers { firstArg<Card>().right() }
            coEvery { cardRepository.findById(any()) } returns card.right()

            val response =
                client.post("/api/v1/cards") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"stepId":"$stepId","title":"Task","description":"Do it"}""")
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Task", body["title"]?.jsonPrimitive?.content)
            assertEquals(stepId, body["stepId"]?.jsonPrimitive?.content)
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
                    setBody("""{"stepId":"$stepId","title":""}""")
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

            val targetStepId = UUID.randomUUID().toString()
            val movedCard = card.moveTo(targetStepId, 1)

            coEvery { cardRepository.updateCard(cardId, any()) } answers {
                secondArg<(Card) -> Card>()(card).right()
            }
            coEvery { cardRepository.findById(cardId) } returns movedCard.right()

            val response =
                client.patch("/api/v1/cards/$cardId/move") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"stepId":"$targetStepId.value","position":1}""")
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
                client.get("/api/v1/cards/$cardId") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(cardId, body["id"]?.jsonPrimitive?.content)
            assertEquals("Task", body["title"]?.jsonPrimitive?.content)
        }
}
