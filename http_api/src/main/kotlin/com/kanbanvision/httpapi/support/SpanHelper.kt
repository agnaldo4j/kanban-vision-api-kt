package com.kanbanvision.httpapi.support

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Wraps a suspending [block] in an OpenTelemetry span named [spanName].
 *
 * Uses [asContextElement] to attach the OTel context to the coroutine context so the
 * span remains current across suspension points and thread hops (e.g., [kotlinx.coroutines.Dispatchers.IO]
 * used by JDBC repositories). This ensures nested auto-instrumented spans (HTTP, JDBC)
 * become children of this span regardless of which thread the coroutine resumes on.
 *
 * [CancellationException] is re-thrown without being recorded as an error span,
 * since coroutine cancellations are not application errors.
 *
 * When the OTel SDK is registered as global by `configureTelemetry` (ADR-0031) the span
 * is exported. When no SDK is registered (unit tests, `OTEL_TRACES_EXPORTER` unset/none)
 * the call is a no-op because [GlobalOpenTelemetry] returns a no-op tracer by default.
 *
 * Lives in the neutral `support` package so both `plugins` and `routes` can use it without
 * creating a package cycle (enforced by `PackageCycleTest`). Only use it in `http_api/`.
 */
suspend fun <T> withSpan(
    spanName: String,
    block: suspend () -> T,
): T {
    val tracer = GlobalOpenTelemetry.getTracer("kanban-vision-api")
    val span =
        tracer
            .spanBuilder(spanName)
            .setSpanKind(SpanKind.INTERNAL)
            .setParent(Context.current())
            .startSpan()
    val otelContext = span.storeInContext(Context.current())
    return try {
        val result =
            withContext(otelContext.asContextElement()) {
                block()
            }
        span.setStatus(StatusCode.OK)
        result
    } catch (ex: CancellationException) {
        // Coroutine cancellation is not an application error — do not record on span.
        throw ex
    } catch (
        // TooGenericExceptionCaught: intentional — this helper wraps any suspending block and must
        // record all exception types in the span before re-throwing. A specific type cannot be used.
        @Suppress("TooGenericExceptionCaught")
        ex: Exception,
    ) {
        span.setStatus(StatusCode.ERROR, ex.message ?: "error")
        span.recordException(ex)
        throw ex
    } finally {
        span.end()
    }
}
