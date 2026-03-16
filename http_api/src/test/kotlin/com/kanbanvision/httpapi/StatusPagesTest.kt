package com.kanbanvision.httpapi

import com.kanbanvision.httpapi.plugins.configureObservability
import com.kanbanvision.httpapi.plugins.configureSerialization
import com.kanbanvision.httpapi.plugins.configureStatusPages
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private class TestContentTransformationException(
    message: String,
) : ContentTransformationException(message)

class StatusPagesTest {
    @Test
    fun `unhandled exception returns 500 with requestId`() =
        testApplication {
            application {
                configureObservability()
                configureSerialization()
                configureStatusPages()
                routing {
                    @Suppress("TooGenericExceptionThrown")
                    get("/boom") { throw RuntimeException("intentional error") }
                }
            }
            val response = client.get("/boom")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Internal server error", body["error"]?.jsonPrimitive?.content)
            assertNotNull(body["requestId"])
        }

    @Test
    fun `ContentTransformationException returns 400 with requestId`() =
        testApplication {
            application {
                configureObservability()
                configureSerialization()
                configureStatusPages()
                routing {
                    @Suppress("TooGenericExceptionThrown")
                    get("/bad-body") { throw TestContentTransformationException("malformed input") }
                }
            }
            val response = client.get("/bad-body")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Invalid request body", body["error"]?.jsonPrimitive?.content)
            assertNotNull(body["requestId"])
        }
}
