package com.kanbanvision.httpapi.plugins

import com.kanbanvision.httpapi.TEST_JWT_AUDIENCE
import com.kanbanvision.httpapi.TEST_JWT_ISSUER
import com.kanbanvision.httpapi.TEST_JWT_SECRET
import com.kanbanvision.httpapi.withJwt
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
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
}
