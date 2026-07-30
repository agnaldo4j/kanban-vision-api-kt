package com.kanbanvision.httpapi.ratelimit

import io.ktor.server.plugins.ratelimit.RateLimiter
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds

class LocalTokenBucketRateLimiter(
    private val limit: Int,
    private val refillPeriodMillis: Long,
    initialTokens: Double = limit.toDouble(),
    private val clock: () -> Long = System::currentTimeMillis,
) : RateLimiter {
    private data class Bucket(
        val tokens: Double,
        val timestampMillis: Long,
    )

    init {
        require(limit > 0) { "limit must be positive, was: $limit" }
        require(refillPeriodMillis > 0) { "refillPeriodMillis must be positive, was: $refillPeriodMillis" }
    }

    private val state = AtomicReference(Bucket(initialTokens.coerceIn(0.0, limit.toDouble()), clock()))

    override suspend fun tryConsume(tokens: Int): RateLimiter.State {
        while (true) {
            val now = clock()
            val current = state.get()
            val available = refill(current, now)

            if (available < tokens) {
                val waitMillis = ceil((tokens - available) * refillPeriodMillis / limit).toLong()
                return RateLimiter.State.Exhausted(waitMillis.milliseconds)
            }

            val remaining = available - tokens
            if (state.compareAndSet(current, Bucket(remaining, now))) {
                val millisToFull = ceil((limit - remaining) * refillPeriodMillis / limit).toLong()
                return RateLimiter.State.Available(
                    remainingTokens = floor(remaining).toInt(),
                    limit = limit,
                    refillAtTimeMillis = now + millisToFull,
                )
            }
        }
    }

    private fun refill(
        current: Bucket,
        now: Long,
    ): Double {
        val elapsed = (now - current.timestampMillis).coerceAtLeast(0L)
        return minOf(limit.toDouble(), current.tokens + elapsed.toDouble() * limit / refillPeriodMillis)
    }
}
