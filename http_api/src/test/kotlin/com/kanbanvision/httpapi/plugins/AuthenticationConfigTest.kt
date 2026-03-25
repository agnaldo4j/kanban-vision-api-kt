package com.kanbanvision.httpapi.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.kanbanvision.httpapi.TEST_JWT_AUDIENCE
import com.kanbanvision.httpapi.TEST_JWT_ISSUER
import com.kanbanvision.httpapi.TEST_JWT_REALM
import com.kanbanvision.httpapi.TEST_JWT_SECRET
import com.kanbanvision.httpapi.withJwt
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthenticationConfigTest {
    @Test
    fun `given jwt settings in application config when configureAuthentication is called without arguments then jwt auth is enabled`() =
        testApplication {
            environment {
                config =
                    MapApplicationConfig(
                        "jwt.secret" to TEST_JWT_SECRET,
                        "jwt.issuer" to TEST_JWT_ISSUER,
                        "jwt.audience" to TEST_JWT_AUDIENCE,
                        "jwt.realm" to "kanban-vision-test-realm",
                    )
            }

            application {
                configureSerialization()
                configureStatusPages()
                configureAuthentication()
                routing {
                    authenticate("jwt-auth") {
                        get("/protected") { call.respondText("ok") }
                    }
                }
            }

            val unauthorized = client.get("/protected")
            assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)

            val authorized =
                client.get("/protected") {
                    withJwt().invoke(this)
                }
            assertEquals(HttpStatusCode.OK, authorized.status)
        }

    @Test
    fun `given default secret placeholder in config when configuring authentication then warning branch is exercised`() =
        testApplication {
            environment {
                config =
                    MapApplicationConfig(
                        "jwt.secret" to "dev-secret-change-in-production",
                        "jwt.issuer" to TEST_JWT_ISSUER,
                        "jwt.audience" to TEST_JWT_AUDIENCE,
                        "jwt.realm" to TEST_JWT_REALM,
                    )
            }

            application {
                configureSerialization()
                configureStatusPages()
                configureAuthentication()
                routing {
                    authenticate("jwt-auth") {
                        get("/check") { call.respondText("ok") }
                    }
                }
            }

            val response = client.get("/check")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `given jwt with wrong audience when authenticating then validate rejects credential`() =
        testApplication {
            environment {
                config =
                    MapApplicationConfig(
                        "jwt.secret" to TEST_JWT_SECRET,
                        "jwt.issuer" to TEST_JWT_ISSUER,
                        "jwt.audience" to TEST_JWT_AUDIENCE,
                        "jwt.realm" to TEST_JWT_REALM,
                    )
            }
            application {
                configureSerialization()
                configureStatusPages()
                configureAuthentication()
                routing { authenticate("jwt-auth") { get("/check") { call.respondText("ok") } } }
            }
            val response =
                client.get("/check") {
                    header(HttpHeaders.Authorization, "Bearer ${wrongAudienceJwt()}")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    private fun wrongAudienceJwt(): String =
        JWT
            .create()
            .withAudience("wrong-audience")
            .withIssuer(TEST_JWT_ISSUER)
            .withSubject("tester")
            .withExpiresAt(Date(System.currentTimeMillis() + 60_000L))
            .sign(Algorithm.HMAC256(TEST_JWT_SECRET))
}
