package com.kanbanvision.httpapi.plugins

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RateLimitForwardedForTest {
    @Test
    fun `given one request per minute when forwarded ip changes then each client key has its own quota`() =
        testApplication {
            application {
                configureRateLimit(limit = 1)
                configureStatusPages()
                routing { get("/limited") { call.respondText("ok") } }
            }

            val first =
                client.get("/limited") {
                    header(HttpHeaders.XForwardedFor, "10.1.0.1")
                }
            val second =
                client.get("/limited") {
                    header(HttpHeaders.XForwardedFor, "10.1.0.2")
                }

            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(HttpStatusCode.OK, second.status)
        }

    @Test
    fun `given forwarded for chain when request key is extracted from header then server answers without unhandled failure`() =
        testApplication {
            application {
                configureRateLimit(limit = 1)
                configureStatusPages()
                routing { get("/limited") { call.respondText("ok") } }
            }

            val first =
                client.get("/limited") {
                    header(HttpHeaders.XForwardedFor, "10.1.0.10, 172.16.0.2")
                }
            val second =
                client.get("/limited") {
                    header(HttpHeaders.XForwardedFor, "10.1.0.10, 172.16.0.3")
                }

            assertEquals(HttpStatusCode.OK, first.status)
            assertTrue(
                second.status == HttpStatusCode.NotAcceptable || second.status == HttpStatusCode.TooManyRequests,
            )
        }
}
