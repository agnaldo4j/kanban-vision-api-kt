package com.kanbanvision.httpapi.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("StatusPages")

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            val requestId = call.attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown"
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Bad request"), "requestId" to requestId))
        }
        exception<NoSuchElementException> { call, cause ->
            val requestId = call.attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown"
            call.respond(HttpStatusCode.NotFound, mapOf("error" to (cause.message ?: "Not found"), "requestId" to requestId))
        }
        exception<Throwable> { call, cause ->
            val requestId = call.attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown"
            log.error("Unhandled exception [requestId={}]", requestId, cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error", "requestId" to requestId))
        }
    }
}
