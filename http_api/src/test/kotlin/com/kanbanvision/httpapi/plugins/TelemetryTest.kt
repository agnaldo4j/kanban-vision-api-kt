package com.kanbanvision.httpapi.plugins

import com.kanbanvision.persistence.DatabaseConfig
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TelemetryTest {
    @Test
    fun `given exporter unset when building sdk then telemetry is disabled`() {
        assertNull(autoConfiguredSdk(tracesExporter = null, setAsGlobal = false))
    }

    @Test
    fun `given blank exporter when building sdk then telemetry is disabled`() {
        assertNull(autoConfiguredSdk(tracesExporter = "  ", setAsGlobal = false))
    }

    @Test
    fun `given exporter none in any case when building sdk then telemetry is disabled`() {
        assertNull(autoConfiguredSdk(tracesExporter = "none", setAsGlobal = false))
        assertNull(autoConfiguredSdk(tracesExporter = "NONE", setAsGlobal = false))
    }

    @Test
    fun `given otlp exporter when building sdk then autoconfigure creates a real sdk`() {
        val sdk = autoConfiguredSdk(tracesExporter = "otlp", setAsGlobal = false)

        assertNotNull(sdk)
        sdk.use {
            assertNotNull(it.tracerProvider.get("telemetry-test"))
        }
    }

    @Test
    fun `given telemetry disabled when instrumenting database config then config is unchanged`() {
        val config = postgresConfig()

        assertSame(config, instrumentDatabaseConfig(config, telemetryEnabled = false))
    }

    @Test
    fun `given telemetry enabled when instrumenting database config then otel jdbc driver wraps the url`() {
        val instrumented = instrumentDatabaseConfig(postgresConfig(), telemetryEnabled = true)

        assertEquals("jdbc:otel:postgresql://localhost:5432/kanbanvision", instrumented.url)
        assertEquals(OTEL_JDBC_DRIVER, instrumented.driver)
    }

    @Test
    fun `given no sdk when configuring telemetry then nothing is installed and null is returned`() =
        testApplication {
            application {
                assertNull(configureTelemetry(openTelemetry = null))
            }
            routing()
            assertEquals(HttpStatusCode.OK, client.get("/ping").status)
        }

    @Test
    fun `given sdk when handling a request then a server span is exported`() {
        val exporter = InMemorySpanExporter.create()

        testApplication {
            application {
                configureTelemetry(openTelemetry = inMemorySdk(exporter))
            }
            routing()

            assertEquals(HttpStatusCode.OK, client.get("/ping").status)

            // Assert DENTRO do bloco: o close() do SDK no ApplicationStopped faz o
            // InMemorySpanExporter descartar a lista ao encerrar o testApplication.
            val serverSpan = exporter.finishedSpanItems.single { it.kind == SpanKind.SERVER }
            assertTrue(serverSpan.name.contains("/ping"), "expected route in span name, got ${serverSpan.name}")
        }
    }

    @Test
    fun `given sdk when application stops then sdk is closed`() {
        val exporter = InMemorySpanExporter.create()
        val sdk = inMemorySdk(exporter)
        var exportedDuringRun = 0

        testApplication {
            application {
                assertSame(sdk, configureTelemetry(openTelemetry = sdk))
            }
            routing()
            assertEquals(HttpStatusCode.OK, client.get("/ping").status)
            exportedDuringRun = exporter.finishedSpanItems.size
        }

        assertTrue(exportedDuringRun > 0, "spans should have been exported before shutdown")

        // testApplication dispara ApplicationStopped ao encerrar. Com o SDK fechado,
        // um span tardio não pode ser exportado — a lista não cresce (assert robusto,
        // independe de o exporter limpar ou não a lista no shutdown).
        val sizeAfterStop = exporter.finishedSpanItems.size
        sdk.tracerProvider
            .get("after-stop")
            .spanBuilder("late")
            .startSpan()
            .end()
        assertEquals(sizeAfterStop, exporter.finishedSpanItems.size, "sdk should be closed after ApplicationStopped")
    }

    @Test
    fun `given system property when resolving exporter then property takes precedence over env`() {
        try {
            System.setProperty("otel.traces.exporter", "otlp")
            assertEquals("otlp", resolvedTracesExporter())
        } finally {
            System.clearProperty("otel.traces.exporter")
        }
    }

    @Test
    fun `given no system property when resolving exporter then env var value is used`() {
        // Sem property setada, cai na env OTEL_TRACES_EXPORTER — ausente no ambiente
        // de teste, logo null (telemetria desligada por default).
        assertNull(resolvedTracesExporter())
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.routing() {
        application {
            routing {
                get("/ping") { call.respondText("pong") }
            }
        }
    }

    private fun inMemorySdk(exporter: InMemorySpanExporter): OpenTelemetrySdk =
        OpenTelemetrySdk
            .builder()
            .setTracerProvider(
                SdkTracerProvider
                    .builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build(),
            ).build()

    private fun postgresConfig(): DatabaseConfig =
        DatabaseConfig(
            url = "jdbc:postgresql://localhost:5432/kanbanvision",
            driver = "org.postgresql.Driver",
            user = "kanban",
            password = "kanban",
        )
}
