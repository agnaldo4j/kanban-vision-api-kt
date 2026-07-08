package com.kanbanvision.httpapi.plugins

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Safety net do GAP-BC (follow-up ADR-0032): o pipeline do Ktor pode deixar o contexto
 * OTel do request anterior preso na thread do event loop (KTOR-9431, corrigido no Ktor
 * 3.4.3; opentelemetry-java-instrumentation#16430). Quando isso acontece o Instrumenter
 * suprime novos spans SERVER (suppression por SpanKind) e requests encadeiam num mesmo
 * trace. Estes testes garantem o isolamento por request na JVM — test host sequencial e
 * Netty real sob carga concorrente (o cenário do leak observado no GAP-BB).
 *
 * InjectDispatcher suprimido: o hop para Dispatchers.IO é o próprio gatilho do leak em
 * teste — injetar o dispatcher descaracterizaria o repro (#16430 usa este padrão).
 */
@Suppress("InjectDispatcher")
class TelemetryContextIsolationTest {
    @Test
    fun `given sequential requests suspending on io dispatcher then each request gets its own root server span`() {
        val exporter = InMemorySpanExporter.create()

        testApplication {
            application {
                configureTelemetry(openTelemetry = inMemorySdk(exporter))
                routing {
                    get("/io-hop") {
                        // withContext com dispatcher externo é o gatilho do leak (#16430):
                        // a retomada pode ocorrer noutra thread e o scope OTel não fecha.
                        withContext(Dispatchers.IO) {}
                        call.respondText("ok")
                    }
                }
            }

            repeat(SEQUENTIAL_REQUESTS) {
                assertEquals(HttpStatusCode.OK, client.get("/io-hop").status)
            }

            // Asserts DENTRO do bloco: o close() do SDK no ApplicationStopped descarta
            // a lista do InMemorySpanExporter ao encerrar o testApplication.
            assertServerSpansIsolated(exporter, SEQUENTIAL_REQUESTS)
        }
    }

    @Test
    fun `given concurrent requests on netty engine then every request gets its own root server span`() {
        val exporter = InMemorySpanExporter.create()
        val server =
            embeddedServer(Netty, port = 0) {
                configureTelemetry(openTelemetry = inMemorySdk(exporter))
                routing {
                    get("/io-hop") {
                        withContext(Dispatchers.IO) {}
                        call.respondText("ok")
                    }
                }
            }.start(wait = false)

        try {
            fireConcurrentRequests(server)
            awaitExportedServerSpans(exporter, CONCURRENT_REQUESTS)
            assertServerSpansIsolated(exporter, CONCURRENT_REQUESTS)
        } finally {
            server.stop(gracePeriodMillis = 500, timeoutMillis = 1000)
        }
    }

    private fun fireConcurrentRequests(server: EmbeddedServer<*, *>) {
        val port =
            runBlocking {
                server.engine
                    .resolvedConnectors()
                    .first()
                    .port
            }
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/io-hop")).GET().build()

        repeat(CONCURRENT_REQUESTS / CONCURRENCY) {
            (1..CONCURRENCY)
                .map { client.sendAsync(request, HttpResponse.BodyHandlers.ofString()) }
                .forEach { response -> assertEquals(200, response.join().statusCode()) }
        }
    }

    private fun assertServerSpansIsolated(
        exporter: InMemorySpanExporter,
        expectedRequests: Int,
    ) {
        val serverSpans = exporter.finishedSpanItems.filter { it.kind == SpanKind.SERVER }
        assertEquals(expectedRequests, serverSpans.size, "leak suprime spans SERVER: esperado 1 por request")
        assertEquals(expectedRequests, serverSpans.map(SpanData::getTraceId).toSet().size, "requests encadeados num mesmo trace")
        serverSpans.forEach { span ->
            assertFalse(
                span.parentSpanContext.isValid,
                "span SERVER deve ser raiz; parent herdado indica contexto retido de request anterior",
            )
        }
    }

    /**
     * O span SERVER fecha no postSend, ligeiramente após o cliente receber a resposta —
     * aguarda a exportação completar (com teto) em vez de dormir um valor fixo.
     */
    private fun awaitExportedServerSpans(
        exporter: InMemorySpanExporter,
        expected: Int,
    ) {
        val deadline = System.nanoTime() + EXPORT_TIMEOUT_NANOS
        while (System.nanoTime() < deadline &&
            exporter.finishedSpanItems.count { it.kind == SpanKind.SERVER } < expected
        ) {
            Thread.sleep(EXPORT_POLL_MILLIS)
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

    private companion object {
        // Acima do limiar (~12 requests) em que o leak se manifestava no repro do #16430.
        const val SEQUENTIAL_REQUESTS = 24
        const val CONCURRENT_REQUESTS = 512
        const val CONCURRENCY = 16
        const val EXPORT_TIMEOUT_NANOS = 5_000_000_000L
        const val EXPORT_POLL_MILLIS = 20L
    }
}
