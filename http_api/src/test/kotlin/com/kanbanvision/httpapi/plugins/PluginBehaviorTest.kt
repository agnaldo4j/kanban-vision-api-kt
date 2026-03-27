package com.kanbanvision.httpapi.plugins

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginBehaviorTest {
    @Test
    fun `given low rate limit when same client performs two requests then second request is throttled`() =
        testApplication {
            application {
                configureRateLimit(limit = 1)
                configureSerialization()
                configureStatusPages()
                routing { get("/limited") { call.respondText("ok") } }
            }

            val first = client.get("/limited")
            val second = client.get("/limited")

            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(HttpStatusCode.TooManyRequests, second.status)
        }

    @Test
    fun `given malformed json body when route expects typed body then status pages returns internal server error`() =
        testApplication {
            application {
                install(RequestIdPlugin)
                configureSerialization()
                configureStatusPages()
                routing {
                    post("/typed") {
                        call.receive<PluginPayload>()
                        call.respondText("ok")
                    }
                }
            }

            val response =
                client.post("/typed") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"invalid":}""")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertTrue(response.bodyAsText().contains("Internal server error"))
        }

    @Test
    fun `given content transformation exception when route fails conversion then status pages returns bad request`() =
        testApplication {
            application {
                install(RequestIdPlugin)
                configureSerialization()
                configureStatusPages()
                routing {
                    get("/cte") {
                        throw object : ContentTransformationException("invalid") {}
                    }
                }
            }

            val response = client.get("/cte")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Invalid request body"))
        }

    @Test
    fun `given unhandled exception in route then status pages returns internal server error`() =
        testApplication {
            application {
                install(RequestIdPlugin)
                configureSerialization()
                configureStatusPages()
                routing {
                    get("/boom") {
                        error("boom")
                    }
                }
            }

            val response = client.get("/boom")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertTrue(response.bodyAsText().contains("Internal server error"))
        }

    @Test
    fun `given observability plugin when request is processed then response includes request id header`() =
        testApplication {
            application {
                configureObservability()
                routing { get("/obs") { call.respondText("ok") } }
            }

            val response = client.get("/obs")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers.contains("X-Request-ID"))
        }

    @Test
    fun `given client provided request id when observability plugin processes request then same request id is echoed`() =
        testApplication {
            application {
                configureObservability()
                routing { get("/obs") { call.respondText("ok") } }
            }

            val response =
                client.get("/obs") {
                    header("X-Request-ID", "req-client-123")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("req-client-123", response.headers["X-Request-ID"])
        }

    @Test
    fun `given metrics plugin when requesting metrics endpoint then prometheus payload is returned`() =
        testApplication {
            application {
                install(Koin) {
                    modules(module { single { PrometheusMeterRegistry(io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT) } })
                }
                configureMetrics()
            }

            val response = client.get("/metrics")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("#"))
        }

    @Test
    fun `given openapi plugin when application starts then openapi routes are registered without startup errors`() =
        testApplication {
            application { configureOpenApi() }
        }

    @Test
    fun `given content transformation exception without request id when status pages handles it then bad request is returned`() =
        testApplication {
            application {
                configureSerialization()
                configureStatusPages()
                routing {
                    get("/cte-no-id") {
                        throw object : ContentTransformationException("test") {}
                    }
                }
            }

            val response = client.get("/cte-no-id")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Invalid request body"))
        }

    @Test
    fun `given unhandled exception without request id when status pages handles it then internal error is returned`() =
        testApplication {
            application {
                configureSerialization()
                configureStatusPages()
                routing { get("/boom-no-id") { error("test") } }
            }

            val response = client.get("/boom-no-id")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertTrue(response.bodyAsText().contains("Internal server error"))
        }

    @Test
    fun `given rate limited request without request id when rate limit is exceeded then too many requests is returned`() =
        testApplication {
            application {
                configureRateLimit(limit = 1)
                configureSerialization()
                configureStatusPages()
                routing {
                    get("/slow") { call.respondText("ok") }
                }
            }

            client.get("/slow")
            val second = client.get("/slow")

            assertEquals(HttpStatusCode.TooManyRequests, second.status)
        }

    @kotlinx.serialization.Serializable
    private data class PluginPayload(
        val value: String,
    )
}
