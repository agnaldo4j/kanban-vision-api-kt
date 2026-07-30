package com.kanbanvision.httpapi.support

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

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
