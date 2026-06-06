package com.kanbanvision.httpapi.contracts

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.PactBuilder
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.annotations.Pact
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals

@ExtendWith(PactConsumerTestExt::class)
@PactTestFor(providerName = "kanban-vision-api")
class PactCompatibilitySpikeTest {
    @Pact(consumer = "pact-spike-consumer")
    fun healthPact(builder: PactBuilder): V4Pact =
        builder
            .expectsToReceiveHttpInteraction("a GET request to /health returns 200") { interaction ->
                interaction
                    .withRequest { req ->
                        req.method("GET")
                        req.path("/health")
                    }.willRespondWith { res ->
                        res.status(200)
                    }
            }.toPact()

    @Test
    @PactTestFor(pactMethod = "healthPact")
    fun `given pact mock server when requesting health then 200 is returned`(mockServer: MockServer) =
        runTest {
            HttpClient().use { client ->
                val response = client.get("${mockServer.getUrl()}/health")
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }
}
