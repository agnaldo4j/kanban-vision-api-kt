package com.kanbanvision.httpapi.ratelimit

import io.ktor.server.plugins.ratelimit.RateLimiter
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val logger = LoggerFactory.getLogger(RedisRateLimiter::class.java)

class RedisRateLimiter(
    private val redisKey: String,
    private val limit: Int,
    private val refillPeriodMillis: Long,
    private val gateway: RedisTokenBucketGateway,
    private val breaker: RedisCircuitBreaker = RedisCircuitBreaker,
) : RateLimiter {
    @Volatile
    private var lastRemaining: Int? = null
    private val fallbackBucket = AtomicReference<LocalTokenBucketRateLimiter?>(null)

    private val degraded = AtomicBoolean(false)

    @Suppress("TooGenericExceptionCaught") // any backend failure must degrade, never 5xx (ADR-0041)
    override suspend fun tryConsume(tokens: Int): RateLimiter.State {
        if (breaker.isOpen()) return degradeTo(tokens)
        return try {
            val resetClock = degraded.get()
            val result =
                breaker.executeSuspend { gateway.consume(redisKey, limit, refillPeriodMillis, tokens, resetClock) }
            lastRemaining = result.remaining
            degraded.set(false)
            fallbackBucket.set(null)
            result.toState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.trace("Redis rate-limit call failed for {}; degrading to local bucket", redisKey, e)
            degradeTo(tokens)
        }
    }

    private suspend fun degradeTo(tokens: Int): RateLimiter.State {
        degraded.set(true)
        return fallback().tryConsume(tokens)
    }

    private fun fallback(): RateLimiter =
        fallbackBucket.updateAndGet { existing ->
            existing ?: LocalTokenBucketRateLimiter(
                limit = limit,
                refillPeriodMillis = refillPeriodMillis,
                initialTokens = (lastRemaining ?: limit).toDouble(),
            )
        }!!
}
