package com.kanbanvision.httpapi.ratelimit.redis

import com.kanbanvision.httpapi.ratelimit.RateLimiterFactory
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.TimeoutOptions
import java.time.Duration

private const val COMMAND_TIMEOUT_MILLIS = 250L
private const val LUA_RESOURCE = "/redis/token_bucket.lua"

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
    // Close partially-initialised resources on ANY failure path (e.g. connect() succeeds but
    // SCRIPT LOAD is denied by a Redis ACL): otherwise the Netty event loop + TCP socket leak for the
    // process lifetime while the limiter silently runs local. runCatching avoids a generic catch.
    return runCatching {
        val connection = client.connect()
        val sha =
            runCatching { connection.sync().scriptLoad(script) }
                .getOrElse { e ->
                    connection.close()
                    throw e
                }
        RateLimiterFactory(
            gateway = LettuceRedisGateway(connection.async(), script, sha),
            closeable =
                AutoCloseable {
                    connection.close()
                    client.shutdown()
                },
        )
    }.getOrElse { e ->
        client.shutdown()
        throw e
    }
}

private fun loadLuaScript(): String =
    checkNotNull(RedisRateLimiterBootstrap::class.java.getResourceAsStream(LUA_RESOURCE)) {
        "Missing Lua script resource: $LUA_RESOURCE"
    }.bufferedReader().use { it.readText() }

private object RedisRateLimiterBootstrap
