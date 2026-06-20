package com.kanbanvision.httpapi.plugins

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.contentLength
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("RequestLimits")

internal const val DEFAULT_MAX_BODY_SIZE = 1_048_576L // 1 MB

private val BODY_METHODS = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)

fun Application.configureRequestLimits(maxBodySize: Long = loadMaxBodySize()) {
    log.info("Request body size limit: {} bytes", maxBodySize)
    intercept(ApplicationCallPipeline.Plugins) {
        val contentLength = call.request.contentLength()
        val isBodyMethod = call.request.httpMethod in BODY_METHODS

        val reject =
            (contentLength != null && contentLength > maxBodySize) ||
                (isBodyMethod && contentLength == null)

        if (reject) {
            call.respond(HttpStatusCode.PayloadTooLarge)
            finish()
        }
    }
}

internal fun loadMaxBodySize(env: (String) -> String? = System::getenv): Long {
    val parsed = env("MAX_REQUEST_BODY_SIZE")?.toLongOrNull() ?: return DEFAULT_MAX_BODY_SIZE
    return if (parsed > 0L) {
        parsed
    } else {
        log.warn("MAX_REQUEST_BODY_SIZE={} is not positive — using default 1MB", parsed)
        DEFAULT_MAX_BODY_SIZE
    }
}
