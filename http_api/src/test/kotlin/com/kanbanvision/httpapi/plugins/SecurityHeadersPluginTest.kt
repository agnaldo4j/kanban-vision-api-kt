package com.kanbanvision.httpapi.plugins

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SecurityHeadersPluginTest {
    @Test
    fun `all security headers are present on GET response`() =
        testApplication {
            application {
                configureSecurityHeaders()
                routing { get("/test") { call.respondText("ok") } }
            }
            val response = client.get("/test")
            assertEquals(HttpStatusCode.OK, response.status)
            assertNotNull(response.headers["X-Frame-Options"])
            assertNotNull(response.headers["X-Content-Type-Options"])
            assertNotNull(response.headers["Referrer-Policy"])
            assertNotNull(response.headers["Content-Security-Policy"])
            assertNotNull(response.headers["X-XSS-Protection"])
        }

    @Test
    fun `X-Frame-Options is DENY`() =
        testApplication {
            application {
                configureSecurityHeaders()
                routing { get("/test") { call.respondText("ok") } }
            }
            assertEquals("DENY", client.get("/test").headers["X-Frame-Options"])
        }

    @Test
    fun `X-Content-Type-Options is nosniff`() =
        testApplication {
            application {
                configureSecurityHeaders()
                routing { get("/test") { call.respondText("ok") } }
            }
            assertEquals("nosniff", client.get("/test").headers["X-Content-Type-Options"])
        }

    @Test
    fun `Referrer-Policy is strict-origin-when-cross-origin`() =
        testApplication {
            application {
                configureSecurityHeaders()
                routing { get("/test") { call.respondText("ok") } }
            }
            assertEquals("strict-origin-when-cross-origin", client.get("/test").headers["Referrer-Policy"])
        }

    @Test
    fun `Content-Security-Policy is default-src self`() =
        testApplication {
            application {
                configureSecurityHeaders()
                routing { get("/test") { call.respondText("ok") } }
            }
            assertEquals("default-src 'self'", client.get("/test").headers["Content-Security-Policy"])
        }

    @Test
    fun `X-XSS-Protection is 1 mode block`() =
        testApplication {
            application {
                configureSecurityHeaders()
                routing { get("/test") { call.respondText("ok") } }
            }
            assertEquals("1; mode=block", client.get("/test").headers["X-XSS-Protection"])
        }

    @Test
    fun `security headers are present on POST response`() =
        testApplication {
            application {
                configureSecurityHeaders()
                routing { post("/test") { call.respondText("ok") } }
            }
            val response =
                client.post("/test") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("DENY", response.headers["X-Frame-Options"])
            assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        }

    @Test
    fun `security headers are present on 404 response`() =
        testApplication {
            application {
                configureSecurityHeaders()
                routing { get("/test") { call.respondText("ok") } }
            }
            val response = client.get("/nonexistent")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("DENY", response.headers["X-Frame-Options"])
            assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        }
}
