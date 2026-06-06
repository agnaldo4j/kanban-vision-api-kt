package com.kanbanvision.httpapi.contracts

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.LambdaDsl
import au.com.dius.pact.consumer.dsl.PactBuilder
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.annotations.Pact
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(PactConsumerTestExt::class)
@PactTestFor(providerName = "kanban-vision-api")
class SimulationsQueriesConsumerPactTest {
    private val bearerToken = "Bearer test-jwt-consumer-pact"

    @Pact(consumer = "simulation-consumer")
    fun getSimulationPact(builder: PactBuilder): V4Pact =
        builder
            .expectsToReceiveHttpInteraction("GET /api/v1/simulations/sim-1 returns 200") { interaction ->
                interaction
                    .withRequest { req ->
                        req.method("GET")
                        req.path("/api/v1/simulations/sim-1")
                        req.header(HttpHeaders.Authorization, bearerToken)
                    }.willRespondWith { res ->
                        res.status(200)
                        res.body(
                            LambdaDsl
                                .newJsonBody { o ->
                                    o.stringType("simulationId", "sim-1")
                                    o.stringType("organizationId", "org-1")
                                    o.integerType("wipLimit", 2)
                                    o.integerType("teamSize", 3)
                                    o.integerType("seedValue", 42)
                                    o.`object`("state") { s ->
                                        s.integerType("currentDay", 1)
                                        s.integerType("wipLimit", 2)
                                        s.integerType("teamSize", 3)
                                        s.integerType("itemCount", 0)
                                    }
                                }.build(),
                        )
                    }
            }.toPact()

    @Test
    @PactTestFor(pactMethod = "getSimulationPact")
    fun `given existing simulation when GET simulations id then 200 with all contract fields`(mockServer: MockServer) =
        runTest {
            val client = HttpClient()
            val response =
                client.get("${mockServer.getUrl()}/api/v1/simulations/sim-1") {
                    header(HttpHeaders.Authorization, bearerToken)
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("simulationId"))
            assertTrue(body.contains("organizationId"))
            assertTrue(body.contains("state"))
            client.close()
        }

    @Pact(consumer = "simulation-consumer")
    fun getSimulationDaysPact(builder: PactBuilder): V4Pact =
        builder
            .expectsToReceiveHttpInteraction("GET /api/v1/simulations/sim-1/days returns 200") { interaction ->
                interaction
                    .withRequest { req ->
                        req.method("GET")
                        req.path("/api/v1/simulations/sim-1/days")
                        req.header(HttpHeaders.Authorization, bearerToken)
                    }.willRespondWith { res ->
                        res.status(200)
                        res.body(
                            LambdaDsl
                                .newJsonBody { o ->
                                    o.stringType("simulationId", "sim-1")
                                    o.eachLike("days") { d ->
                                        d.integerType("day", 1)
                                        d.integerType("throughput", 1)
                                        d.integerType("wipCount", 1)
                                        d.integerType("blockedCount", 0)
                                        d.decimalType("avgAgingDays", 0.0)
                                    }
                                }.build(),
                        )
                    }
            }.toPact()

    @Test
    @PactTestFor(pactMethod = "getSimulationDaysPact")
    fun `given existing simulation when GET simulations id days then 200 with time series fields`(mockServer: MockServer) =
        runTest {
            val client = HttpClient()
            val response =
                client.get("${mockServer.getUrl()}/api/v1/simulations/sim-1/days") {
                    header(HttpHeaders.Authorization, bearerToken)
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("simulationId"))
            assertTrue(body.contains("days"))
            assertTrue(body.contains("avgAgingDays"))
            client.close()
        }

    @Pact(consumer = "simulation-consumer")
    fun getSimulationCfdPact(builder: PactBuilder): V4Pact =
        builder
            .expectsToReceiveHttpInteraction("GET /api/v1/simulations/sim-1/cfd returns 200") { interaction ->
                interaction
                    .withRequest { req ->
                        req.method("GET")
                        req.path("/api/v1/simulations/sim-1/cfd")
                        req.header(HttpHeaders.Authorization, bearerToken)
                    }.willRespondWith { res ->
                        res.status(200)
                        res.body(
                            LambdaDsl
                                .newJsonBody { o ->
                                    o.stringType("simulationId", "sim-1")
                                    o.eachLike("series") { s ->
                                        s.integerType("day", 1)
                                        s.integerType("throughputCumulative", 1)
                                        s.integerType("wipCount", 1)
                                        s.integerType("blockedCount", 0)
                                    }
                                }.build(),
                        )
                    }
            }.toPact()

    @Test
    @PactTestFor(pactMethod = "getSimulationCfdPact")
    fun `given existing simulation when GET simulations id cfd then 200 with series fields`(mockServer: MockServer) =
        runTest {
            val client = HttpClient()
            val response =
                client.get("${mockServer.getUrl()}/api/v1/simulations/sim-1/cfd") {
                    header(HttpHeaders.Authorization, bearerToken)
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("simulationId"))
            assertTrue(body.contains("series"))
            assertTrue(body.contains("throughputCumulative"))
            client.close()
        }

    @Pact(consumer = "simulation-consumer")
    fun listSimulationsPact(builder: PactBuilder): V4Pact =
        builder
            .expectsToReceiveHttpInteraction("GET /api/v1/simulations returns 200 paginated") { interaction ->
                interaction
                    .withRequest { req ->
                        req.method("GET")
                        req.path("/api/v1/simulations")
                        req.header(HttpHeaders.Authorization, bearerToken)
                    }.willRespondWith { res ->
                        res.status(200)
                        res.body(
                            LambdaDsl
                                .newJsonBody { o ->
                                    o.eachLike("data") { d ->
                                        d.stringType("id", "sim-1")
                                        d.stringType("name", "Simulation")
                                        d.stringType("status", "DRAFT")
                                        d.integerType("currentDay", 1)
                                    }
                                    o.integerType("page", 1)
                                    o.integerType("size", 10)
                                    o.integerType("total", 1)
                                }.build(),
                        )
                    }
            }.toPact()

    @Test
    @PactTestFor(pactMethod = "listSimulationsPact")
    fun `given simulations exist when GET simulations then 200 with paginated list fields`(mockServer: MockServer) =
        runTest {
            val client = HttpClient()
            val response =
                client.get("${mockServer.getUrl()}/api/v1/simulations") {
                    header(HttpHeaders.Authorization, bearerToken)
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("data"))
            assertTrue(body.contains("page"))
            assertTrue(body.contains("total"))
            client.close()
        }
}
