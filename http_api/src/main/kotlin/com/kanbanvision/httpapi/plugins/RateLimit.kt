package com.kanbanvision.httpapi.plugins

import com.kanbanvision.httpapi.support.AUTH_RATE_LIMIT_NAME
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import kotlin.time.Duration.Companion.minutes

private const val DEFAULT_RATE_LIMIT = 100
private const val AUTH_RATE_LIMIT = 5
private const val RATE_LIMIT_WINDOW_MINUTES = 1

fun Application.configureRateLimit(
    limit: Int = DEFAULT_RATE_LIMIT,
    trustedProxyCount: Int = loadTrustedProxyCount(),
) {
    require(limit > 0) { "Rate limit must be positive, was: $limit" }
    install(RateLimit) {
        global {
            rateLimiter(limit = limit, refillPeriod = RATE_LIMIT_WINDOW_MINUTES.minutes)
            requestKey { call -> call.clientKey(trustedProxyCount) }
        }
        register(AUTH_RATE_LIMIT_NAME) {
            rateLimiter(limit = AUTH_RATE_LIMIT, refillPeriod = RATE_LIMIT_WINDOW_MINUTES.minutes)
            requestKey { call -> call.clientKey(trustedProxyCount) }
        }
    }
}

private fun ApplicationCall.clientKey(trustedProxyCount: Int): String =
    clientRateLimitKey(
        xffHeader = request.headers[HttpHeaders.XForwardedFor],
        remoteHost = request.local.remoteHost,
        trustedProxyCount = trustedProxyCount,
    )

/**
 * Number of trusted reverse proxies in front of the app (`TRUSTED_PROXY_COUNT`, default 0).
 * With 0, `X-Forwarded-For` is ignored entirely and keying falls back to the socket peer —
 * spoof-proof by default. Set it to the proxy chain length (Ingress/LB/mesh) in production.
 * The `env` seam keeps this unit-testable (same idiom as `Cors.loadCorsOrigins`).
 */
internal fun loadTrustedProxyCount(env: (String) -> String? = System::getenv): Int =
    env("TRUSTED_PROXY_COUNT")?.toIntOrNull()?.coerceAtLeast(0) ?: 0

/**
 * Resolves the rate-limit key from the client-IP chain, resistant to `X-Forwarded-For` spoofing.
 *
 * The real chain is `XFF entries ++ [socket peer]`. With `trustedProxyCount` trusted proxies in
 * front, the genuine client sits at index `size - 1 - trustedProxyCount` — dropping the trusted
 * (right-most) hops. Client-supplied left-most entries can be forged but are never selected unless
 * `trustedProxyCount` is (mis)configured to reach them.
 */
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
