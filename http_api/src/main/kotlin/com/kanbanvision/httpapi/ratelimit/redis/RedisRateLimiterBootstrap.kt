package com.kanbanvision.httpapi.ratelimit.redis

import com.kanbanvision.httpapi.ratelimit.RateLimiterFactory
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.TimeoutOptions
import java.time.Duration

internal const val COMMAND_TIMEOUT_MILLIS = 250L
private const val LUA_RESOURCE = "/redis/token_bucket.lua"

fun redisBackedFactory(url: String): RateLimiterFactory {
    val script = loadLuaScript()
    val client = RedisClient.create(url)
    client.options =
        ClientOptions
            .builder()
            .autoReconnect(true)
            .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(COMMAND_TIMEOUT_MILLIS)))
            .build()
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
