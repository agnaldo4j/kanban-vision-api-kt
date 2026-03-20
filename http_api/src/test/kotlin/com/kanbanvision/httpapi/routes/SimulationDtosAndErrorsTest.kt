package com.kanbanvision.httpapi.routes

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Movement
import com.kanbanvision.domain.model.MovementType
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.httpapi.adapters.respondWithDomainError
import com.kanbanvision.httpapi.fixtureSimulation
import com.kanbanvision.httpapi.fixtureSnapshot
import com.kanbanvision.httpapi.plugins.REQUEST_ID_KEY
import com.kanbanvision.httpapi.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationDtosAndErrorsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `given simulation aggregate when mapping to response then dto fields mirror domain state`() {
        val simulation = fixtureSimulation(id = "sim-42")

        val payload = simulation.toSimulationResponse()

        assertEquals("sim-42", payload.simulationId)
        assertEquals("org-1", payload.organizationId)
        assertEquals(2, payload.wipLimit)
        assertEquals(2, payload.teamSize)
        assertEquals(42L, payload.seedValue)
    }

    @Test
    fun `given daily snapshot when mapping to response then movements and metrics are serialized consistently`() {
        val movement = Movement(type = MovementType.MOVED, cardId = "card-1", day = SimulationDay(1), reason = "move")
        val snapshot = fixtureSnapshot(simulationId = "sim-1", day = 1).copy(movements = listOf(movement))

        val payload = snapshot.toResponse()

        assertEquals("sim-1", payload.simulationId)
        assertEquals(1, payload.day)
        assertEquals(1, payload.metrics.throughput)
        assertEquals(MovementType.MOVED.name, payload.movements.first().type)
    }

    @Test
    fun `given not found domain error when responding from adapter then route returns 404 with domain message`() =
        testApplication {
            application { configureErrorRoute("/error/not-found", DomainError.SimulationNotFound("sim-1"), "req-1") }

            val response = client.get("/error/not-found")

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertTrue(response.bodyAsText().contains("Simulation not found"))
        }

    @Test
    fun `given validation domain error when responding from adapter then route returns 400 with validation message`() =
        testApplication {
            application { configureErrorRoute("/error/validation", DomainError.ValidationError("invalid"), "req-2") }

            val response = client.get("/error/validation")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("invalid"))
        }

    @Test
    fun `given conflict and internal domain errors when responding from adapter then status codes are mapped correctly`() =
        testApplication {
            application {
                configureSerialization()
                routing {
                    get("/error/conflict") {
                        call.attributes.put(REQUEST_ID_KEY, "req-3")
                        call.respondWithDomainError(DomainError.DayAlreadyExecuted(1))
                    }
                    get("/error/internal") {
                        call.attributes.put(REQUEST_ID_KEY, "req-4")
                        call.respondWithDomainError(DomainError.PersistenceError("db"))
                    }
                }
            }

            val conflict = client.get("/error/conflict")
            val internal = client.get("/error/internal")

            assertEquals(HttpStatusCode.Conflict, conflict.status)
            assertTrue(conflict.bodyAsText().contains("already executed"))
            assertEquals(HttpStatusCode.InternalServerError, internal.status)
            assertTrue(internal.bodyAsText().contains("Internal server error"))
        }

    @Test
    fun `given board not found error when responding from adapter then fallback not found message is returned`() =
        testApplication {
            application { configureErrorRoute("/error/fallback", DomainError.BoardNotFound("b-1"), "req-5") }

            val response = client.get("/error/fallback")

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertTrue(response.bodyAsText().contains("Board not found"))
        }

    @Test
    fun `given step and card not found errors when responding from adapter then specific not found messages are returned`() =
        testApplication {
            application {
                configureSerialization()
                routing {
                    get("/error/step-not-found") {
                        call.attributes.put(REQUEST_ID_KEY, "req-6")
                        call.respondWithDomainError(DomainError.StepNotFound("s-1"))
                    }
                    get("/error/card-not-found") {
                        call.attributes.put(REQUEST_ID_KEY, "req-7")
                        call.respondWithDomainError(DomainError.CardNotFound("c-1"))
                    }
                }
            }

            val step = client.get("/error/step-not-found")
            val card = client.get("/error/card-not-found")

            assertEquals(HttpStatusCode.NotFound, step.status)
            assertTrue(step.bodyAsText().contains("Step not found"))
            assertEquals(HttpStatusCode.NotFound, card.status)
            assertTrue(card.bodyAsText().contains("Card not found"))
        }

    @Test
    fun `given organization not found and invalid decision errors when responding from adapter then status mapping remains correct`() =
        testApplication {
            application {
                configureSerialization()
                routing {
                    get("/error/organization-not-found") {
                        call.attributes.put(REQUEST_ID_KEY, "req-8")
                        call.respondWithDomainError(DomainError.OrganizationNotFound("o-1"))
                    }
                    get("/error/invalid-decision") {
                        call.attributes.put(REQUEST_ID_KEY, "req-9")
                        call.respondWithDomainError(DomainError.InvalidDecision("decision is invalid"))
                    }
                }
            }

            val org = client.get("/error/organization-not-found")
            val invalidDecision = client.get("/error/invalid-decision")

            assertEquals(HttpStatusCode.NotFound, org.status)
            assertTrue(org.bodyAsText().contains("Organization not found"))
            assertEquals(HttpStatusCode.BadRequest, invalidDecision.status)
            assertTrue(invalidDecision.bodyAsText().contains("decision is invalid"))
        }

    @Test
    fun `given normal json route when serializing response then serialization plugin writes payload`() =
        testApplication {
            application {
                configureSerialization()
                routing {
                    get("/error/plain") {
                        call.respond(HttpStatusCode.OK, mapOf("ok" to true))
                    }
                }
            }

            val plain = client.get("/error/plain")
            val plainBody = json.parseToJsonElement(plain.bodyAsText()).toString()
            assertTrue(plainBody.contains("ok"))
        }

    private fun Application.configureErrorRoute(
        path: String,
        error: DomainError,
        requestId: String,
    ) {
        configureSerialization()
        routing {
            get(path) {
                call.attributes.put(REQUEST_ID_KEY, requestId)
                call.respondWithDomainError(error)
            }
        }
    }
}
