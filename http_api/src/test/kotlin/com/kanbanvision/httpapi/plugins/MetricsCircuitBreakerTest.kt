package com.kanbanvision.httpapi.plugins

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsCircuitBreakerTest {
    @Test
    fun `given metrics plugin when scraping then resilience4j circuit breaker state gauge is exported`() =
        testApplication {
            application {
                install(Koin) {
                    modules(module { single { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) } })
                }
                configureMetrics()
            }

            val response = client.get("/metrics")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(
                response.bodyAsText().contains("resilience4j_circuitbreaker_state"),
                "expected resilience4j_circuitbreaker_state gauge in the Prometheus scrape",
            )
        }
}
