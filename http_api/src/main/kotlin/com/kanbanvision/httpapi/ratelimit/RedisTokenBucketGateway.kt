package com.kanbanvision.httpapi.ratelimit

import io.ktor.server.plugins.ratelimit.RateLimiter
import kotlin.time.Duration.Companion.milliseconds

/**
 * Seam over the atomic token-bucket operation in Redis. A single [consume] runs one atomic script
 * (`EVALSHA`) so N pods sharing a bucket never race. Kept as an interface so [RedisRateLimiter] is
 * unit-testable with a fake — the real Lettuce implementation lives in `ratelimit.redis` and is only
 * exercised by the CI native smoke (there is no embedded Redis in the repo).
 */
interface RedisTokenBucketGateway {
    /**
     * @param resetClock `true` only on the first call after a local-fallback period — tells Redis not
     *   to re-grant the outage window that local already served (ADR-0041 reconciliation).
     */
    suspend fun consume(
        key: String,
        limit: Int,
        refillPeriodMillis: Long,
        tokens: Int,
        resetClock: Boolean,
    ): TokenBucketResult
}

/**
 * Outcome of one token-bucket step, mirroring the fields Ktor's `RateLimiter.State` needs.
 * [refillAtEpochMillis] is an **absolute** epoch-ms timestamp (the plugin renders `X-RateLimit-Reset`
 * as `ceil(refillAtEpochMillis / 1000)`), matching [LocalTokenBucketRateLimiter].
 */
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
