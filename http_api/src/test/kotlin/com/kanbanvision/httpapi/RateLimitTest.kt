package com.kanbanvision.httpapi

import com.kanbanvision.httpapi.plugins.configureRateLimit
import com.kanbanvision.httpapi.plugins.configureSerialization
import com.kanbanvision.httpapi.plugins.configureStatusPages
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RateLimitTest {
    @Test
    fun `requests within limit are allowed`() =
        testApplication {
            application {
                configureSerialization()
                configureStatusPages()
                configureRateLimit(limit = 2)
                routing {
                    get("/test") { call.respond(HttpStatusCode.OK) }
                }
            }

            assertEquals(HttpStatusCode.OK, client.get("/test").status)
            assertEquals(HttpStatusCode.OK, client.get("/test").status)
        }

    @Test
    fun `requests exceeding limit return 429 with json body`() =
        testApplication {
            application {
                configureSerialization()
                configureStatusPages()
                configureRateLimit(limit = 1)
                routing {
                    get("/test") { call.respond(HttpStatusCode.OK) }
                }
            }

            assertEquals(HttpStatusCode.OK, client.get("/test").status)
            val response = client.get("/test")
            assertEquals(HttpStatusCode.TooManyRequests, response.status)
            assertTrue(response.bodyAsText().contains("requestId"))
        }

    @Test
    fun `X-Forwarded-For header is used as rate limit key`() =
        testApplication {
            application {
                configureSerialization()
                configureStatusPages()
                configureRateLimit(limit = 1)
                routing {
                    get("/test") { call.respond(HttpStatusCode.OK) }
                }
            }

            assertEquals(
                HttpStatusCode.OK,
                client.get("/test") { header(HttpHeaders.XForwardedFor, "203.0.113.1") }.status,
            )
            assertEquals(
                HttpStatusCode.TooManyRequests,
                client.get("/test") { header(HttpHeaders.XForwardedFor, "203.0.113.1") }.status,
            )
        }
}
