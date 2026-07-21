package com.kanbanvision.httpapi.ratelimit.redis

import com.kanbanvision.httpapi.ratelimit.RateLimiterFactory
import com.kanbanvision.httpapi.ratelimit.loadRedisUrl
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.TimeoutOptions
import org.slf4j.LoggerFactory
import java.time.Duration

private const val COMMAND_TIMEOUT_MILLIS = 250L
private const val LUA_RESOURCE = "/redis/token_bucket.lua"
private val logger = LoggerFactory.getLogger("com.kanbanvision.httpapi.ratelimit.redis.RedisRateLimiter")

/**
 * The default factory resolved by `configureRateLimit`. If `RATE_LIMIT_REDIS_URL` is set it builds the
 * Redis backend; if that fails (e.g. Redis down at boot) it logs and degrades to the in-memory limiter
 * so the app never fails to start on a Redis outage (ADR-0041). The [redisFactory] seam keeps the
 * branch unit-testable without a live Redis. Lives here (not in `ratelimit`) so the core package never
 * depends on this Lettuce sub-package (no package cycle).
 */
fun defaultRateLimiterFactory(
    env: (String) -> String? = System::getenv,
    redisFactory: (String) -> RateLimiterFactory = ::redisBackedFactory,
): RateLimiterFactory {
    val url = loadRedisUrl(env) ?: return RateLimiterFactory()
    return runCatching { redisFactory(url) }.getOrElse { e ->
        logger.warn("Failed to initialise the Redis rate-limit backend; using the in-memory limiter", e)
        RateLimiterFactory()
    }
}

/**
 * Builds a Redis-backed [RateLimiterFactory]: creates the Lettuce client (short command timeout so a
 * slow Redis fails fast into the breaker/fallback), loads the Lua script, and wires the gateway. May
 * throw if Redis is unreachable at boot — the caller (`defaultRateLimiterFactory`) treats that as a
 * degrade-to-in-memory, so the app never fails to start on a Redis outage (ADR-0041).
 *
 * JaCoCo-excluded (`ratelimit.redis`): touches Lettuce, validated by the CI native smoke.
 */
fun redisBackedFactory(url: String): RateLimiterFactory {
    val script = loadLuaScript()
    val client = RedisClient.create(url)
    client.options =
        ClientOptions
            .builder()
            .autoReconnect(true)
            .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(COMMAND_TIMEOUT_MILLIS)))
            .build()
    val connection = client.connect()
    val sha = connection.sync().scriptLoad(script)
    val gateway = LettuceRedisGateway(connection.async(), script, sha)
    val closeable =
        AutoCloseable {
            connection.close()
            client.shutdown()
        }
    return RateLimiterFactory(gateway = gateway, closeable = closeable)
}

private fun loadLuaScript(): String =
    checkNotNull(RedisRateLimiterBootstrap::class.java.getResourceAsStream(LUA_RESOURCE)) {
        "Missing Lua script resource: $LUA_RESOURCE"
    }.bufferedReader().use { it.readText() }

private object RedisRateLimiterBootstrap
