package com.kanbanvision.httpapi.plugins

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import kotlin.time.Duration.Companion.minutes

private const val DEFAULT_RATE_LIMIT = 100
private const val RATE_LIMIT_WINDOW_MINUTES = 1

fun Application.configureRateLimit(limit: Int = DEFAULT_RATE_LIMIT) {
    require(limit > 0) { "Rate limit must be positive, was: $limit" }
    install(RateLimit) {
        global {
            rateLimiter(limit = limit, refillPeriod = RATE_LIMIT_WINDOW_MINUTES.minutes)
            requestKey { call ->
                call.request.headers[HttpHeaders.XForwardedFor]
                    ?.split(",")
                    ?.firstOrNull()
                    ?.trim()
                    ?: call.request.local.remoteHost
            }
        }
    }
}
