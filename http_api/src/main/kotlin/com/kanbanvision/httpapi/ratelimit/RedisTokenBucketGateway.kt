package com.kanbanvision.httpapi.ratelimit

import io.ktor.server.plugins.ratelimit.RateLimiter
import kotlin.time.Duration.Companion.milliseconds

interface RedisTokenBucketGateway {
    suspend fun consume(
        key: String,
        limit: Int,
        refillPeriodMillis: Long,
        tokens: Int,
        resetClock: Boolean,
    ): TokenBucketResult
}

class TokenBucketResult(
    val allowed: Boolean,
    val remaining: Int,
    val limit: Int,
    val refillAtEpochMillis: Long,
    val waitMillis: Long,
) {
    fun toState(): RateLimiter.State =
        if (allowed) {
            RateLimiter.State.Available(remaining, limit, refillAtEpochMillis)
        } else {
            RateLimiter.State.Exhausted(waitMillis.milliseconds)
        }
}
