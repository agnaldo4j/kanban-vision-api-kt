package com.kanbanvision.httpapi.ratelimit.redis

import com.kanbanvision.httpapi.ratelimit.RedisTokenBucketGateway
import com.kanbanvision.httpapi.ratelimit.TokenBucketResult
import io.lettuce.core.RedisFuture
import io.lettuce.core.RedisNoScriptException
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await

/**
 * Real Lettuce implementation of the token-bucket gateway: one atomic `EVALSHA` per request.
 *
 * JaCoCo-excluded (`ratelimit.redis`) — it can only run against a live Redis, so it is validated by
 * the CI native smoke, not JVM unit tests. If Redis was flushed/restarted and dropped the cached
 * script, `EVALSHA` raises `NOSCRIPT`; we reload once and retry so a restart does not wedge the breaker
 * open (the client-side SHA1 would need `MessageDigest`, which is a forbidden import — so the SHA comes
 * from `SCRIPT LOAD`).
 */
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
