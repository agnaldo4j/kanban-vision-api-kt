package com.kanbanvision.httpapi.plugins

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import kotlinx.coroutines.CancellationException

/**
 * Wraps a suspending [block] in an OpenTelemetry span named [spanName].
 *
 * The span is made current for the duration of [block] so that nested
 * auto-instrumented spans (HTTP, JDBC) become children of this span.
 *
 * [CancellationException] is re-thrown without being recorded as an error span,
 * since coroutine cancellations are not application errors.
 *
 * When the OTel Java Agent is present the span is exported automatically.
 * When the agent is absent (unit tests, local without agent) the call is a no-op
 * because [GlobalOpenTelemetry] returns a no-op tracer by default.
 *
 * Only use this helper in `http_api/` — never import it from `usecases/` or `domain/`.
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
    val scope = span.makeCurrent()
    return try {
        val result = block()
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
        scope.close()
        span.end()
    }
}
