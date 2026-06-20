package com.kanbanvision.httpapi.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.contentLength
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("RequestLimits")

internal const val DEFAULT_MAX_BODY_SIZE = 1_048_576L // 1 MB

fun Application.configureRequestLimits(maxBodySize: Long = loadMaxBodySize()) {
    log.info("Request body size limit: {} bytes", maxBodySize)
    intercept(ApplicationCallPipeline.Plugins) {
        val contentLength = call.request.contentLength()
        if (contentLength != null && contentLength > maxBodySize) {
            call.respond(HttpStatusCode.PayloadTooLarge)
            finish()
        }
    }
}

internal fun loadMaxBodySize(env: (String) -> String? = System::getenv): Long =
    env("MAX_REQUEST_BODY_SIZE")?.toLongOrNull() ?: DEFAULT_MAX_BODY_SIZE
