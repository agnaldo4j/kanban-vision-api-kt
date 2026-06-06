package com.kanbanvision.httpapi.contracts

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.LambdaDsl
import au.com.dius.pact.consumer.dsl.PactBuilder
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.annotations.Pact
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(PactConsumerTestExt::class)
@PactTestFor(providerName = "kanban-vision-api")
class SimulationsCommandsConsumerPactTest {
    private val bearerToken = "Bearer test-jwt-consumer-pact"

    @Pact(consumer = "simulation-consumer")
    fun createSimulationPact(builder: PactBuilder): V4Pact =
        builder
            .expectsToReceiveHttpInteraction("POST /api/v1/simulations returns 201") { interaction ->
                interaction
                    .withRequest { req ->
                        req.method("POST")
                        req.path("/api/v1/simulations")
                        req.header(HttpHeaders.Authorization, bearerToken)
                        req.header(HttpHeaders.ContentType, "application/json")
                        req.body(
                            """{"organizationId":"org-1","wipLimit":2,"teamSize":3,"seedValue":0}""",
                            "application/json",
                        )
                    }.willRespondWith { res ->
                        res.status(201)
                        res.body(
                            LambdaDsl
                                .newJsonBody { o ->
                                    o.stringType("simulationId", "sim-123")
                                }.build(),
                        )
                    }
            }.toPact()

    @Test
    @PactTestFor(pactMethod = "createSimulationPact")
    fun `given valid body when POST simulations then 201 with simulationId field`(mockServer: MockServer) =
        runTest {
            val client = HttpClient()
            val response =
                client.post("${mockServer.getUrl()}/api/v1/simulations") {
                    header(HttpHeaders.Authorization, bearerToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"organizationId":"org-1","wipLimit":2,"teamSize":3,"seedValue":0}""")
                }
            assertEquals(HttpStatusCode.Created, response.status)
            assertTrue(response.bodyAsText().contains("simulationId"))
            client.close()
        }
}
