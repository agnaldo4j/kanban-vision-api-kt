package com.kanbanvision.httpapi.routes

import com.kanbanvision.httpapi.TEST_JWT_AUDIENCE
import com.kanbanvision.httpapi.TEST_JWT_ISSUER
import com.kanbanvision.httpapi.TEST_JWT_SECRET
import com.kanbanvision.httpapi.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HealthAndAuthRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `given healthy dependencies when calling health endpoints then api returns ok responses`() =
        testApplication {
            application {
                configureSerialization()
                routing { healthRoutes { true } }
            }

            val health = client.get("/health")
            val live = client.get("/health/live")
            val ready = client.get("/health/ready")

            assertEquals(HttpStatusCode.OK, health.status)
            assertEquals(HttpStatusCode.OK, live.status)
            assertEquals(HttpStatusCode.OK, ready.status)

            val payload = json.decodeFromString<HealthResponse>(ready.bodyAsText())
            assertEquals("ok", payload.status)
        }

    @Test
    fun `given unavailable database when calling readiness endpoint then api returns service unavailable`() =
        testApplication {
            application {
                configureSerialization()
                routing { healthRoutes { false } }
            }

            val ready = client.get("/health/ready")

            assertEquals(HttpStatusCode.ServiceUnavailable, ready.status)
            val payload = json.decodeFromString<HealthResponse>(ready.bodyAsText())
            assertEquals("unavailable", payload.status)
        }

    @Test
    fun `given valid auth request when posting token endpoint then api returns jwt token`() =
        testApplication {
            application {
                configureSerialization()
                routing {
                    authRoutes(
                        secret = TEST_JWT_SECRET,
                        issuer = TEST_JWT_ISSUER,
                        audience = TEST_JWT_AUDIENCE,
                        ttlMs = 60_000L,
                    )
                }
            }

            val response =
                client.post("/auth/token") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"subject":"dev-user","organizationId":"org-1"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val payload = json.decodeFromString<TokenResponse>(response.bodyAsText())
            assertNotNull(payload.token)
        }
}
