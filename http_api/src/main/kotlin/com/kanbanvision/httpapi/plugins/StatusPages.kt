package com.kanbanvision.httpapi.plugins

import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("StatusPages")

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ContentTransformationException> { call, cause ->
            val requestId = call.attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown"
            log.warn("Invalid request body [requestId={}]: {}", requestId, cause.message)
            call.respond(HttpStatusCode.BadRequest, DomainErrorResponse(error = "Invalid request body", requestId = requestId))
        }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            val requestId = call.attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown"
            log.error("Unhandled exception [requestId={}]", requestId, cause)
            call.respond(HttpStatusCode.InternalServerError, DomainErrorResponse(error = "Internal server error", requestId = requestId))
        }
        status(HttpStatusCode.TooManyRequests) { call, _ ->
            val requestId = call.attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown"
            call.respond(
                HttpStatusCode.TooManyRequests,
                DomainErrorResponse(error = "Too Many Requests", requestId = requestId),
            )
        }
    }
}
