package com.kanbanvision.httpapi.plugins

import com.kanbanvision.httpapi.ratelimit.RateLimiterFactory
import com.kanbanvision.httpapi.ratelimit.defaultRateLimiterFactory
import com.kanbanvision.httpapi.ratelimit.redis.redisBackedFactory
import com.kanbanvision.httpapi.support.AUTH_RATE_LIMIT_NAME
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import kotlin.time.Duration.Companion.minutes

private const val DEFAULT_RATE_LIMIT = 100
private const val AUTH_RATE_LIMIT = 5
private const val RATE_LIMIT_WINDOW_MINUTES = 1

fun Application.configureRateLimit(
    limit: Int = DEFAULT_RATE_LIMIT,
    trustedProxyCount: Int = loadTrustedProxyCount(),
    factory: RateLimiterFactory = defaultRateLimiterFactory(redisFactory = ::redisBackedFactory),
) {
    require(limit > 0) { "Rate limit must be positive, was: $limit" }
    val windowMillis = RATE_LIMIT_WINDOW_MINUTES.minutes.inWholeMilliseconds
    install(RateLimit) {
        global {
            rateLimiter(factory.provider("global", limit, windowMillis))
            requestKey { call -> call.clientKey(trustedProxyCount) }
        }
        register(AUTH_RATE_LIMIT_NAME) {
            rateLimiter(factory.provider("auth", AUTH_RATE_LIMIT, windowMillis))
            requestKey { call -> call.clientKey(trustedProxyCount) }
        }
    }
    monitor.subscribe(ApplicationStopped) { factory.close() }
}

private fun ApplicationCall.clientKey(trustedProxyCount: Int): String =
    clientRateLimitKey(
        xffHeader = request.headers[HttpHeaders.XForwardedFor],
        remoteHost = request.local.remoteHost,
        trustedProxyCount = trustedProxyCount,
    )

internal fun loadTrustedProxyCount(env: (String) -> String? = System::getenv): Int =
    env("TRUSTED_PROXY_COUNT")?.toIntOrNull()?.coerceAtLeast(0) ?: 0

internal fun clientRateLimitKey(
    xffHeader: String?,
    remoteHost: String,
    trustedProxyCount: Int,
): String {
    val xff = xffHeader?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    val chain = xff + remoteHost
    val idx = (chain.size - 1 - trustedProxyCount).coerceAtLeast(0)
    return chain[idx]
}
