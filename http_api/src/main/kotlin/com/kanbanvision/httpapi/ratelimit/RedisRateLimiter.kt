package com.kanbanvision.httpapi.ratelimit

import io.ktor.server.plugins.ratelimit.RateLimiter
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val logger = LoggerFactory.getLogger(RedisRateLimiter::class.java)

/**
 * Rate limiter backed by the shared Redis bucket, with a **seeded local fallback** for Redis outages.
 *
 * The authoritative count lives entirely in Redis (via [gateway]), so this instance is stateless with
 * respect to the quota and stays correct under Ktor's per-key instance eviction. It keeps only the last
 * `remaining` seen from Redis: when Redis fails (or [RedisCircuitBreaker] is open) it degrades to an
 * in-memory [LocalTokenBucketRateLimiter] **seeded from that observation** — so the remote and local
 * quotas are never summed (a client that just drained the shared bucket does NOT get a fresh full
 * window). With no observation yet, it seeds full = today's per-pod behaviour. It never opens to
 * unlimited and never surfaces a backend failure as a 5xx (ADR-0041, `security.md §6`).
 *
 * Ktor caches one instance per (limiter, client key) and evicts it at `refillAtTimeMillis` — exactly
 * when the bucket is full again — so the last-observed seed and the fallback bucket are bounded by
 * that lifecycle without a separate unbounded map.
 */
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

    // Set while this instance is serving from the local fallback. The first successful Redis call after
    // it flips true reconciles: it tells Redis to reset its refill clock so it does NOT re-grant the
    // outage window that local already served (ADR-0041 — avoids the double-count on recovery).
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
            fallbackBucket.set(null) // Redis healthy ⇒ drop any stale fallback so a later outage re-seeds fresh
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
