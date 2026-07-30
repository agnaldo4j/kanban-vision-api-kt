package com.kanbanvision.httpapi.ratelimit

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.RateLimiter
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.kanbanvision.httpapi.ratelimit.RateLimiterFactory")

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

internal fun loadRedisUrl(env: (String) -> String? = System::getenv): String? = env("RATE_LIMIT_REDIS_URL")?.takeIf { it.isNotBlank() }

fun defaultRateLimiterFactory(
    redisFactory: (String) -> RateLimiterFactory,
    env: (String) -> String? = System::getenv,
): RateLimiterFactory {
    val url = loadRedisUrl(env) ?: return RateLimiterFactory()
    return runCatching { redisFactory(url) }.getOrElse { e ->
        logger.warn("Failed to initialise the Redis rate-limit backend; using the in-memory limiter", e)
        RateLimiterFactory()
    }
}
