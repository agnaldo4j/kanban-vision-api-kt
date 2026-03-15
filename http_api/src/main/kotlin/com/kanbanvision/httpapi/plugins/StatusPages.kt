package com.kanbanvision.httpapi.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("StatusPages")

@Suppress("TooGenericExceptionCaught")
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val requestId = call.attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown"
            log.error("Unhandled exception [requestId={}]", requestId, cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error", "requestId" to requestId))
        }
    }
}
