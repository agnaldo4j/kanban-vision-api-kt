package com.kanbanvision.httpapi

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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenApiSpecTest {
    private val testModule =
        module {
            single<BoardRepository> { mockk(relaxed = true) }
            single<CardRepository> { mockk(relaxed = true) }
            single { CreateBoardUseCase(get()) }
            single { GetBoardUseCase(get()) }
            single { CreateCardUseCase(get()) }
            single { GetCardUseCase(get()) }
            single { MoveCardUseCase(get()) }
        }

    @Test
    fun `openapi spec is served and contains all api routes`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureRouting()
            }

            val response = client.get("/api.json")
            assertEquals(HttpStatusCode.OK, response.status)

            val paths = Json.parseToJsonElement(response.bodyAsText()).jsonObject["paths"]?.jsonObject
            assertNotNull(paths, "OpenAPI spec deve definir paths")

            assertTrue("/api/v1/boards" in paths, "POST /api/v1/boards deve estar documentado")
            assertTrue("/api/v1/boards/{id}" in paths, "GET /api/v1/boards/{id} deve estar documentado")
            assertTrue("/api/v1/cards" in paths, "POST /api/v1/cards deve estar documentado")
            assertTrue("/api/v1/cards/{id}/move" in paths, "PATCH /api/v1/cards/{id}/move deve estar documentado")
        }

    @Test
    fun `all routes have a description`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureRouting()
            }

            val response = client.get("/api.json")
            val paths = Json.parseToJsonElement(response.bodyAsText()).jsonObject["paths"]?.jsonObject
            assertNotNull(paths)

            paths.forEach { (path, pathItem) ->
                pathItem.jsonObject.forEach { (method, operation) ->
                    val description = operation.jsonObject["description"]
                    assertNotNull(
                        description,
                        "Rota $method $path está sem descrição — toda rota deve ser documentada.",
                    )
                }
            }
        }
}
