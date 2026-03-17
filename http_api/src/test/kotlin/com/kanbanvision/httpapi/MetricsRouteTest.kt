package com.kanbanvision.httpapi

import com.kanbanvision.httpapi.metrics.DomainMetrics
import com.kanbanvision.httpapi.plugins.configureMetrics
import com.kanbanvision.httpapi.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsRouteTest {
    private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val testModule =
        module {
            single { registry }
            single { DomainMetrics(registry) }
        }

    @Test
    fun `GET metrics returns 200`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureSerialization()
                configureMetrics()
            }

            val response = client.get("/metrics")

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `GET metrics returns prometheus text format`() =
        testApplication {
            install(Koin) { modules(testModule) }
            application {
                configureSerialization()
                configureMetrics()
            }

            val body = client.get("/metrics").bodyAsText()

            assertTrue(body.isNotEmpty(), "Prometheus output should not be empty")
        }
}
