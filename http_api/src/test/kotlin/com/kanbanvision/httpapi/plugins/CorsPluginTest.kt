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
    fun `empty origins set — request works without CORS header`() =
        testApplication {
            application {
                configureCors(emptySet())
                routing { get("/test") { call.respondText("ok") } }
            }
            val response = client.get("/test")
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
    fun `no-arg configureCors uses system env — no CORS header when var not set`() =
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
