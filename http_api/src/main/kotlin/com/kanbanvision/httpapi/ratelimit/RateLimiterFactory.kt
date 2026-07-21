package com.kanbanvision.httpapi.ratelimit

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.RateLimiter
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.kanbanvision.httpapi.ratelimit.RateLimiterFactory")

/**
 * Builds the provider lambda plugged into Ktor's RateLimit plugin via the custom-provider overload.
 *
 * In this slice it always yields the in-memory [LocalTokenBucketRateLimiter]; a follow-up adds a
 * Redis-backed limiter behind this same seam (distinct key namespaces per limiter, a shared store).
 * [close] is the shutdown hook for that future Redis client — a no-op until it exists.
 *
 * Ktor already partitions its limiter registry by `RateLimitName` (global vs `auth`), so the two
 * `configureRateLimit` limiters never share an instance even for the same client key.
 */
class RateLimiterFactory : AutoCloseable {
    fun provider(
        limit: Int,
        windowMillis: Long,
    ): (ApplicationCall, Any) -> RateLimiter = { _, _ -> LocalTokenBucketRateLimiter(limit, windowMillis) }

    override fun close() {
        // No resource to release until the Redis client arrives; kept so the wiring
        // (monitor.subscribe(ApplicationStopped) { factory.close() }) is already in place.
    }
}

/**
 * Reads the optional Redis URL for the distributed limiter (`RATE_LIMIT_REDIS_URL`). Absent or blank
 * ⇒ `null` ⇒ in-memory fallback. Mirrors the `loadTrustedProxyCount` env-seam idiom; the `env`
 * parameter keeps it unit-testable.
 */
internal fun loadRedisUrl(env: (String) -> String? = System::getenv): String? = env("RATE_LIMIT_REDIS_URL")?.takeIf { it.isNotBlank() }

/**
 * The default factory resolved by `configureRateLimit`. If `RATE_LIMIT_REDIS_URL` is set it warns
 * that the distributed backend is not wired yet (it arrives in a follow-up) and still uses the
 * in-memory limiter — so setting the variable early is safe and visible in the logs.
 */
fun defaultRateLimiterFactory(env: (String) -> String? = System::getenv): RateLimiterFactory {
    if (loadRedisUrl(env) != null) {
        logger.warn(
            "RATE_LIMIT_REDIS_URL is set but the distributed Redis backend is not wired yet; " +
                "using the in-memory token-bucket limiter.",
        )
    }
    return RateLimiterFactory()
}
