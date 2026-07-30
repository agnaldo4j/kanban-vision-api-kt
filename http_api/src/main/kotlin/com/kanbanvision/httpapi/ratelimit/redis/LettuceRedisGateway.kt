package com.kanbanvision.httpapi.ratelimit.redis

import com.kanbanvision.httpapi.ratelimit.RedisTokenBucketGateway
import com.kanbanvision.httpapi.ratelimit.TokenBucketResult
import io.lettuce.core.RedisFuture
import io.lettuce.core.RedisNoScriptException
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await

internal class LettuceRedisGateway(
    private val commands: RedisAsyncCommands<String, String>,
    private val script: String,
    private val scriptSha: String,
) : RedisTokenBucketGateway {
    override suspend fun consume(
        key: String,
        limit: Int,
        refillPeriodMillis: Long,
        tokens: Int,
        resetClock: Boolean,
    ): TokenBucketResult {
        val args =
            arrayOf(
                limit.toString(),
                refillPeriodMillis.toString(),
                tokens.toString(),
                if (resetClock) "1" else "0",
            )

        // NOSCRIPT is a control-flow signal, not an error to log: Redis dropped the cached script
        // (flush/restart), so reload it and retry once.
        @Suppress("SwallowedException")
        val raw: List<Long> =
            try {
                evalSha(key, args)
            } catch (e: RedisNoScriptException) {
                commands.scriptLoad(script).await()
                evalSha(key, args)
            }
        return TokenBucketResult(
            allowed = raw[0] == 1L,
            remaining = raw[1].toInt(),
            limit = raw[2].toInt(),
            refillAtEpochMillis = raw[3],
            waitMillis = raw[4],
        )
    }

    private suspend fun evalSha(
        key: String,
        args: Array<String>,
    ): List<Long> {
        val future: RedisFuture<List<Long>> = commands.evalsha(scriptSha, ScriptOutputType.MULTI, arrayOf(key), *args)
        return future.await()
    }
}
