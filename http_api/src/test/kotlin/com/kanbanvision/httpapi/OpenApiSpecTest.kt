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
import com.kanbanvision.usecases.column.CreateColumnUseCase
import com.kanbanvision.usecases.column.GetColumnUseCase
import com.kanbanvision.usecases.column.ListColumnsByBoardUseCase
import com.kanbanvision.usecases.repositories.BoardRepository
import com.kanbanvision.usecases.repositories.CardRepository
import com.kanbanvision.usecases.repositories.ColumnRepository
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
            single<ColumnRepository> { mockk(relaxed = true) }
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
    fun `openapi spec contains board and card routes`() =
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

            assertTrue(paths["/api/v1/boards"]?.jsonObject?.containsKey("post") == true)
            assertTrue(paths["/api/v1/boards/{id}"]?.jsonObject?.containsKey("get") == true)
            assertTrue(paths["/api/v1/cards"]?.jsonObject?.containsKey("post") == true)
            assertTrue(paths["/api/v1/cards/{id}/move"]?.jsonObject?.containsKey("patch") == true)
        }

    @Test
    fun `openapi spec contains column routes`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureRouting()
            }

            val paths =
                Json.parseToJsonElement(client.get("/api.json").bodyAsText()).jsonObject["paths"]?.jsonObject
            assertNotNull(paths)

            assertTrue(paths["/api/v1/columns"]?.jsonObject?.containsKey("post") == true)
            assertTrue(paths["/api/v1/columns/{id}"]?.jsonObject?.containsKey("get") == true)
            assertTrue(paths["/api/v1/boards/{boardId}/columns"]?.jsonObject?.containsKey("get") == true)
        }

    @Test
    fun `all routes have a non-blank description`() =
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
                    val description = operation.jsonObject["description"]?.jsonPrimitive?.content
                    assertTrue(
                        !description.isNullOrBlank(),
                        "Rota $method $path está sem descrição — toda rota deve ser documentada.",
                    )
                }
            }
        }
}
