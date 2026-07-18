package com.kanbanvision.httpapi.routes

import com.kanbanvision.domain.errors.CommonError
import com.kanbanvision.httpapi.adapters.respondWithDomainError
import com.kanbanvision.httpapi.plugins.configureSerialization
import com.kanbanvision.httpapi.support.REQUEST_ID_KEY
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceUnavailableErrorTest {
    @Test
    fun `given service unavailable error when responding from adapter then route returns 503 with generic message`() =
        testApplication {
            application {
                configureSerialization()
                routing {
                    get("/error/unavailable") {
                        call.attributes.put(REQUEST_ID_KEY, "req-503")
                        call.respondWithDomainError(
                            CommonError.ServiceUnavailable(service = "database", reason = "circuit breaker open"),
                        )
                    }
                }
            }

            val response = client.get("/error/unavailable")

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Service temporarily unavailable"))
            assertTrue(body.contains("req-503"))
            assertFalse(body.contains("circuit breaker"), "internal reason must not leak to the client")
        }
}
