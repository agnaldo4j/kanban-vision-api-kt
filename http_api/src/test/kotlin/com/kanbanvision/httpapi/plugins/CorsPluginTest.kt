package com.kanbanvision.httpapi.plugins

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CorsPluginTest {
    @Test
    fun `allowed origin receives Access-Control-Allow-Origin header`() =
        testApplication {
            application {
                configureCors(setOf("http://localhost:3000"))
                routing { get("/test") { call.respondText("ok") } }
            }
            val response =
                client.get("/test") {
                    header(HttpHeaders.Origin, "http://localhost:3000")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("http://localhost:3000", response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun `disallowed origin does not receive CORS header`() =
        testApplication {
            application {
                configureCors(setOf("http://localhost:3000"))
                routing { get("/test") { call.respondText("ok") } }
            }
            val response =
                client.get("/test") {
                    header(HttpHeaders.Origin, "https://malicious.com")
                }
            assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun `empty origins set — cross-origin request is denied (fail-closed)`() =
        testApplication {
            application {
                configureCors(emptySet())
                routing { get("/test") { call.respondText("ok") } }
            }
            val response =
                client.get("/test") {
                    header(HttpHeaders.Origin, "http://localhost:3000")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun `OPTIONS preflight with allowed origin returns 200`() =
        testApplication {
            application {
                configureCors(setOf("http://localhost:3000"))
                routing { get("/test") { call.respondText("ok") } }
            }
            val response =
                client.options("/test") {
                    header(HttpHeaders.Origin, "http://localhost:3000")
                    header(HttpHeaders.AccessControlRequestMethod, "GET")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            assertNotNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun `preflight with X-Request-ID in requested headers is accepted`() =
        testApplication {
            application {
                configureCors(setOf("http://localhost:3000"))
                routing { get("/test") { call.respondText("ok") } }
            }
            val preflight =
                client.options("/test") {
                    header(HttpHeaders.Origin, "http://localhost:3000")
                    header(HttpHeaders.AccessControlRequestMethod, "GET")
                    header(HttpHeaders.AccessControlRequestHeaders, "X-Request-ID")
                }
            // 200 (not 403 Forbidden) means X-Request-ID is in the allow list
            assertEquals(HttpStatusCode.OK, preflight.status)
            assertNotNull(preflight.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun `X-Request-ID is exposed in actual CORS response headers`() =
        testApplication {
            application {
                configureCors(setOf("http://localhost:3000"))
                routing { get("/test") { call.respondText("ok") } }
            }
            // Access-Control-Expose-Headers is sent on actual responses, not preflights
            val response =
                client.get("/test") {
                    header(HttpHeaders.Origin, "http://localhost:3000")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val exposed = response.headers[HttpHeaders.AccessControlExposeHeaders].orEmpty()
            assert(exposed.contains("X-Request-ID", ignoreCase = true)) {
                "Expected X-Request-ID in Access-Control-Expose-Headers but got: '$exposed'"
            }
        }

    @Test
    fun `loadCorsOrigins parses comma-separated list and trims whitespace`() {
        val origins = loadCorsOrigins { "http://localhost:3000 , https://app.example.com" }
        assertEquals(setOf("http://localhost:3000", "https://app.example.com"), origins)
    }

    @Test
    fun `loadCorsOrigins skips blank entries from trailing commas`() {
        val origins = loadCorsOrigins { "http://localhost:3000," }
        assertEquals(setOf("http://localhost:3000"), origins)
    }

    @Test
    fun `loadCorsOrigins returns empty set when env var absent`() {
        val origins = loadCorsOrigins { null }
        assertEquals(emptySet(), origins)
    }

    @Test
    fun `configureCors default call — request without Origin header gets no CORS header`() =
        testApplication {
            application {
                configureCors()
                routing { get("/test") { call.respondText("ok") } }
            }
            val response = client.get("/test")
            assertEquals(HttpStatusCode.OK, response.status)
            assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
        }
}
