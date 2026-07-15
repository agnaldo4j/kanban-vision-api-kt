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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RateLimitForwardedForTest {
    @Test
    fun `given default rate limit when configuring without explicit limit then routing works`() =
        testApplication {
            application {
                configureRateLimit() // exercises the Kotlin $default bridge (DEFAULT_RATE_LIMIT = 100)
                routing { get("/check") { call.respondText("ok") } }
            }

            assertEquals(HttpStatusCode.OK, client.get("/check").status)
        }

    @Test
    fun `given non-positive rate limit when configuring then require rejects invalid input`(): Unit =
        testApplication {
            var caught: IllegalArgumentException? = null
            application {
                try {
                    configureRateLimit(limit = 0)
                } catch (e: IllegalArgumentException) {
                    caught = e
                }
            }
            startApplication()
            val exception = assertNotNull(caught)
            val message = assertNotNull(exception.message)
            assertTrue(message.contains("positive"))
        }

    @Test
    fun `given one trusted proxy when forwarded ip changes then each client key has its own quota`() =
        testApplication {
            application {
                configureRateLimit(limit = 1, trustedProxyCount = 1)
                configureStatusPages()
                routing { get("/limited") { call.respondText("ok") } }
            }

            val first = client.get("/limited") { header(HttpHeaders.XForwardedFor, "10.1.0.1") }
            val second = client.get("/limited") { header(HttpHeaders.XForwardedFor, "10.1.0.2") }

            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(HttpStatusCode.OK, second.status)
        }

    @Test
    fun `given no trusted proxy when forwarded for is spoofed then quota is shared per socket peer`() =
        testApplication {
            application {
                configureRateLimit(limit = 1, trustedProxyCount = 0)
                configureStatusPages()
                routing { get("/limited") { call.respondText("ok") } }
            }

            // Different forged XFF values must NOT grant a fresh quota — keying ignores XFF at proxy count 0.
            val first = client.get("/limited") { header(HttpHeaders.XForwardedFor, "1.2.3.4") }
            val second = client.get("/limited") { header(HttpHeaders.XForwardedFor, "5.6.7.8") }

            assertEquals(HttpStatusCode.OK, first.status)
            assertTrue(
                second.status == HttpStatusCode.NotAcceptable || second.status == HttpStatusCode.TooManyRequests,
            )
        }

    @Test
    fun `clientRateLimitKey ignores spoofable X-Forwarded-For unless proxies are trusted`() {
        val peer = "203.0.113.9"
        // No trusted proxies: client-supplied XFF is ignored; key is the socket peer.
        assertEquals(peer, clientRateLimitKey(null, peer, trustedProxyCount = 0))
        assertEquals(peer, clientRateLimitKey("1.2.3.4", peer, trustedProxyCount = 0))
        assertEquals(peer, clientRateLimitKey("1.2.3.4, 5.6.7.8", peer, trustedProxyCount = 0))
        // One trusted proxy: the single appended entry is the genuine client.
        assertEquals("1.2.3.4", clientRateLimitKey("1.2.3.4", peer, trustedProxyCount = 1))
        // Attacker prepends a fake entry; the trusted proxy appends the real client — still resolved.
        assertEquals("1.2.3.4", clientRateLimitKey("9.9.9.9, 1.2.3.4", peer, trustedProxyCount = 1))
        // Two trusted proxies.
        assertEquals("1.2.3.4", clientRateLimitKey("1.2.3.4, 10.0.0.1", peer, trustedProxyCount = 2))
        // Blank/empty entries are filtered out.
        assertEquals(peer, clientRateLimitKey(" , ", peer, trustedProxyCount = 0))
        // Over-configured proxy count clamps to the left-most available entry.
        assertEquals("1.2.3.4", clientRateLimitKey("1.2.3.4", peer, trustedProxyCount = 9))
    }

    @Test
    fun `loadTrustedProxyCount parses env and defaults to zero`() {
        assertEquals(0, loadTrustedProxyCount { null })
        assertEquals(0, loadTrustedProxyCount { "not-a-number" })
        assertEquals(0, loadTrustedProxyCount { "-3" })
        assertEquals(2, loadTrustedProxyCount { "2" })
    }
}
