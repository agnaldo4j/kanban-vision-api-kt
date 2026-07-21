package com.kanbanvision.httpapi.plugins

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RateLimitTokenBucketTest {
    @Test
    fun `given the token bucket when the quota is spent then further requests are throttled`() =
        testApplication {
            application {
                configureRateLimit(limit = 2, trustedProxyCount = 0)
                configureStatusPages()
                routing { get("/limited") { call.respondText("ok") } }
            }

            // Same socket peer (no XFF) ⇒ shared bucket of 2 tokens.
            assertEquals(HttpStatusCode.OK, client.get("/limited").status)
            assertEquals(HttpStatusCode.OK, client.get("/limited").status)
            val throttled = client.get("/limited")
            assertTrue(
                throttled.status == HttpStatusCode.NotAcceptable ||
                    throttled.status == HttpStatusCode.TooManyRequests,
            )
        }

    @Test
    fun `given an allowed request when responding then the rate-limit headers are present`() =
        testApplication {
            application {
                configureRateLimit(limit = 5, trustedProxyCount = 0)
                configureStatusPages()
                routing { get("/limited") { call.respondText("ok") } }
            }

            val response = client.get("/limited")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("5", response.headers["X-RateLimit-Limit"])
            assertNotNull(response.headers["X-RateLimit-Remaining"])
            assertNotNull(response.headers["X-RateLimit-Reset"])
        }
}
