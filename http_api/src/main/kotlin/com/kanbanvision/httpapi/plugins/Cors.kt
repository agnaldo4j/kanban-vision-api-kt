package com.kanbanvision.httpapi.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Cors")

fun Application.configureCors(allowedOrigins: Set<String> = loadCorsOrigins()) {
    if (allowedOrigins.isEmpty()) {
        log.info("CORS_ALLOWED_ORIGINS is empty — cross-origin requests blocked by browser")
        return
    }
    log.info("CORS configured for {} origin(s)", allowedOrigins.size)
    install(CORS) {
        allowOrigins { origin -> origin in allowedOrigins }
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Request-ID")
        exposeHeader("X-Request-ID")
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }
}

internal fun loadCorsOrigins(env: (String) -> String? = System::getenv): Set<String> =
    env("CORS_ALLOWED_ORIGINS")
        ?.splitToSequence(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: emptySet()
