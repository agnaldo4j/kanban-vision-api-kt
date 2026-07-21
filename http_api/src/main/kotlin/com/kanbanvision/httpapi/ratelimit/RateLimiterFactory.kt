package com.kanbanvision.httpapi.ratelimit

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.RateLimiter

/**
 * Builds the provider lambda plugged into Ktor's RateLimit plugin via the custom-provider overload.
 *
 * With no [gateway] it yields the in-memory [LocalTokenBucketRateLimiter]; with one it yields the
 * Redis-backed [RedisRateLimiter] (distinct key namespace per limiter). [close] releases the Redis
 * client (via [closeable]) on shutdown.
 *
 * Ktor partitions its limiter registry by `RateLimitName` (global vs `auth`), and the Redis key adds
 * the `namespace`, so the two limiters never share a bucket even for the same client key.
 */
class RateLimiterFactory(
    private val gateway: RedisTokenBucketGateway? = null,
    private val closeable: AutoCloseable? = null,
) : AutoCloseable {
    fun provider(
        namespace: String,
        limit: Int,
        windowMillis: Long,
    ): (ApplicationCall, Any) -> RateLimiter {
        val backend = gateway
        return { _, key ->
            if (backend == null) {
                LocalTokenBucketRateLimiter(limit, windowMillis)
            } else {
                RedisRateLimiter("ratelimit:$namespace:$key", limit, windowMillis, backend)
            }
        }
    }

    override fun close() {
        closeable?.close()
    }
}

/**
 * Reads the optional Redis URL for the distributed limiter (`RATE_LIMIT_REDIS_URL`). Absent or blank
 * ⇒ `null` ⇒ in-memory fallback. Mirrors the `loadTrustedProxyCount` env-seam idiom; the `env`
 * parameter keeps it unit-testable.
 */
internal fun loadRedisUrl(env: (String) -> String? = System::getenv): String? = env("RATE_LIMIT_REDIS_URL")?.takeIf { it.isNotBlank() }
