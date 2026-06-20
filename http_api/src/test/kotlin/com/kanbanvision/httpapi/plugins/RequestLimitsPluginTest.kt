package com.kanbanvision.httpapi.plugins

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestLimitsPluginTest {
    @Test
    fun `POST body exceeding limit returns 413 with error body`() =
        testApplication {
            application {
                configureSerialization()
                configureStatusPages()
                configureRequestLimits(maxBodySize = 10L)
                routing { post("/test") { call.respondText("ok") } }
            }
            val response =
                client.post("/test") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"hello": "world is definitely more than 10 bytes"}""")
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertTrue(response.bodyAsText().contains("Request body too large"))
        }

    @Test
    fun `POST body within limit returns 200`() =
        testApplication {
            application {
                configureRequestLimits(maxBodySize = 1_000L)
                routing { post("/test") { call.respondText("ok") } }
            }
            val response =
                client.post("/test") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"hello": "world"}""")
                }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `GET without body passes through even with minimal limit`() =
        testApplication {
            application {
                configureRequestLimits(maxBodySize = 1L)
                routing { get("/test") { call.respondText("ok") } }
            }
            assertEquals(HttpStatusCode.OK, client.get("/test").status)
        }

    @Test
    fun `loadMaxBodySize parses custom value from env var`() {
        assertEquals(2_097_152L, loadMaxBodySize { "2097152" })
    }

    @Test
    fun `loadMaxBodySize returns 1MB default when env var absent`() {
        assertEquals(DEFAULT_MAX_BODY_SIZE, loadMaxBodySize { null })
    }

    @Test
    fun `loadMaxBodySize falls back to default when env var is not a number`() {
        assertEquals(DEFAULT_MAX_BODY_SIZE, loadMaxBodySize { "not-a-number" })
    }

    @Test
    fun `configureRequestLimits with default limit allows normal GET requests`() =
        testApplication {
            application {
                configureRequestLimits()
                routing { get("/test") { call.respondText("ok") } }
            }
            assertEquals(HttpStatusCode.OK, client.get("/test").status)
        }

    @Test
    fun `POST without Content-Length header returns 413 regardless of body size`() =
        testApplication {
            application {
                configureRequestLimits(maxBodySize = 100_000L)
                routing { post("/test") { call.respondText("ok") } }
            }
            // Body sent without Content-Length (e.g. chunked transfer) is always rejected
            val response =
                client.post("/test") {
                    setBody(
                        object : OutgoingContent.WriteChannelContent() {
                            override val contentType = ContentType.Application.Json
                            override val contentLength: Long? = null

                            override suspend fun writeTo(channel: ByteWriteChannel) {
                                channel.writeStringUtf8("""{"x": "y"}""")
                            }
                        },
                    )
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `loadMaxBodySize falls back to default when env var is zero or negative`() {
        assertEquals(DEFAULT_MAX_BODY_SIZE, loadMaxBodySize { "0" })
        assertEquals(DEFAULT_MAX_BODY_SIZE, loadMaxBodySize { "-512" })
    }
}
