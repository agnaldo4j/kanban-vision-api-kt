package com.kanbanvision.httpapi

import com.kanbanvision.httpapi.plugins.configureRateLimit
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class RateLimitTest {
    @Test
    fun `requests within limit are allowed`() =
        testApplication {
            application {
                configureRateLimit(limit = 2)
                routing {
                    get("/test") { call.respond(HttpStatusCode.OK) }
                }
            }

            assertEquals(HttpStatusCode.OK, client.get("/test").status)
            assertEquals(HttpStatusCode.OK, client.get("/test").status)
        }

    @Test
    fun `requests exceeding limit return 429`() =
        testApplication {
            application {
                configureRateLimit(limit = 1)
                routing {
                    get("/test") { call.respond(HttpStatusCode.OK) }
                }
            }

            assertEquals(HttpStatusCode.OK, client.get("/test").status)
            assertEquals(HttpStatusCode.TooManyRequests, client.get("/test").status)
        }
}
