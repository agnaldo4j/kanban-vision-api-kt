package com.kanbanvision.httpapi.routes

import com.kanbanvision.domain.errors.DomainError
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

class FailClosedDomainErrorTest {
    // DomainError é interface aberta (ADR-0038): os grupos CommonError/KanbanError/SimulationError são
    // sealed, mas um erro fora deles precisa cair no `else` fail-closed do mapper — 500 genérico, sem
    // vazar detalhe nem escapar sem resposta (security.md §Fail Closed). Este erro só existe no teste.
    private object UnknownDomainError : DomainError

    @Test
    fun `given a domain error outside the known groups when responding then mapper fails closed with 500`() =
        testApplication {
            application {
                configureSerialization()
                routing {
                    get("/error/unknown") {
                        call.attributes.put(REQUEST_ID_KEY, "req-500")
                        call.respondWithDomainError(UnknownDomainError)
                    }
                }
            }

            val response = client.get("/error/unknown")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Internal server error"))
            assertTrue(body.contains("req-500"))
            assertFalse(body.contains("UnknownDomainError"), "error type must not leak to the client")
        }
}
