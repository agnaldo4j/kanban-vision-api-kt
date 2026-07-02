package com.kanbanvision.httpapi.plugins

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class VersioningHeadersTest {
    @Test
    fun `given versioning plugin when a route responds then api version header is present`() =
        testApplication {
            application {
                configureVersioningHeaders()
                configureSerialization()
                routing {
                    get("/ping") { call.respond(mapOf("ok" to true)) }
                }
            }

            val response = client.get("/ping")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(CURRENT_API_VERSION, response.headers[API_VERSION_HEADER])
        }

    @Test
    fun `given versioning plugin when route does not exist then 404 response still carries the header`() =
        testApplication {
            application { configureVersioningHeaders() }

            val response = client.get("/does-not-exist")

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(CURRENT_API_VERSION, response.headers[API_VERSION_HEADER])
        }
}
