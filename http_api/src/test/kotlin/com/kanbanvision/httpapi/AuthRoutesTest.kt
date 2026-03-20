package com.kanbanvision.httpapi

import com.kanbanvision.httpapi.plugins.configureAuthentication
import com.kanbanvision.httpapi.plugins.configureObservability
import com.kanbanvision.httpapi.plugins.configureOpenApi
import com.kanbanvision.httpapi.plugins.configureSerialization
import com.kanbanvision.httpapi.plugins.configureStatusPages
import com.kanbanvision.httpapi.routes.authRoutes
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.auth.authenticate
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthRoutesTest {
    private val secret = JwtTestHelper.TEST_SECRET
    private val issuer = JwtTestHelper.TEST_ISSUER
    private val audience = JwtTestHelper.TEST_AUDIENCE
    private val realm = JwtTestHelper.TEST_REALM
    private val ttlMs = 3_600_000L

    @Test
    fun `POST auth token returns 200 with token`() =
        testApplication {
            application {
                configureObservability()
                configureSerialization()
                configureStatusPages()
                configureAuthentication(secret, issuer, audience, realm)
                routing {
                    authRoutes(secret, issuer, audience, ttlMs)
                }
            }

            val response =
                client.post("/auth/token") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"subject":"dev","organizationId":"00000000-0000-0000-0000-000000000001"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["token"])
            assertTrue(body["token"]?.jsonPrimitive?.content?.isNotBlank() == true)
        }

    @Test
    fun `valid token grants access to protected route`() =
        testApplication {
            application {
                configureObservability()
                configureSerialization()
                configureStatusPages()
                configureAuthentication(secret, issuer, audience, realm)
                routing {
                    authenticate("jwt-auth") {
                        get("/protected") {
                            call.respond(HttpStatusCode.OK, "ok")
                        }
                    }
                }
            }

            val token = JwtTestHelper.generateToken()
            val response =
                client.get("/protected") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `missing token returns 401`() =
        testApplication {
            application {
                configureObservability()
                configureSerialization()
                configureStatusPages()
                configureAuthentication(secret, issuer, audience, realm)
                routing {
                    authenticate("jwt-auth") {
                        get("/protected") {
                            call.respond(HttpStatusCode.OK, "ok")
                        }
                    }
                }
            }

            val response = client.get("/protected")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Token inválido ou ausente", body["error"]?.jsonPrimitive?.content)
            assertNotNull(body["requestId"])
        }

    @Test
    fun `expired token returns 401`() =
        testApplication {
            application {
                configureObservability()
                configureSerialization()
                configureStatusPages()
                configureAuthentication(secret, issuer, audience, realm)
                routing {
                    authenticate("jwt-auth") {
                        get("/protected") {
                            call.respond(HttpStatusCode.OK, "ok")
                        }
                    }
                }
            }

            val expiredToken = JwtTestHelper.generateExpiredToken()
            val response =
                client.get("/protected") {
                    header(HttpHeaders.Authorization, "Bearer $expiredToken")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `token with wrong signature returns 401`() =
        testApplication {
            application {
                configureObservability()
                configureSerialization()
                configureStatusPages()
                configureAuthentication(secret, issuer, audience, realm)
                routing {
                    authenticate("jwt-auth") {
                        get("/protected") {
                            call.respond(HttpStatusCode.OK, "ok")
                        }
                    }
                }
            }

            val wrongToken = JwtTestHelper.generateToken().dropLast(5) + "xxxxx"
            val response =
                client.get("/protected") {
                    header(HttpHeaders.Authorization, "Bearer $wrongToken")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `configureAuthentication no-arg reads jwt config from environment`() =
        testApplication {
            environment {
                config =
                    MapApplicationConfig(
                        "jwt.secret" to secret,
                        "jwt.issuer" to issuer,
                        "jwt.audience" to audience,
                        "jwt.realm" to realm,
                    )
            }
            application {
                configureObservability()
                configureSerialization()
                configureStatusPages()
                configureAuthentication()
                routing {
                    authenticate("jwt-auth") {
                        get("/protected") { call.respond(HttpStatusCode.OK, "ok") }
                    }
                }
            }

            val response =
                client.get("/protected") {
                    header(HttpHeaders.Authorization, "Bearer ${JwtTestHelper.generateToken()}")
                }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `auth route is documented in openapi spec`() =
        testApplication {
            application {
                configureOpenApi()
                configureSerialization()
                configureStatusPages()
                configureAuthentication(secret, issuer, audience, realm)
                routing { authRoutes(secret, issuer, audience, ttlMs) }
            }

            val response = client.get("/api.json")
            assertEquals(HttpStatusCode.OK, response.status)
            val paths = Json.parseToJsonElement(response.bodyAsText()).jsonObject["paths"]?.jsonObject
            assertNotNull(paths)
            assertTrue(paths["/auth/token"]?.jsonObject?.containsKey("post") == true)
        }
}
