package com.kanbanvision.httpapi.ratelimit

import io.ktor.server.plugins.ratelimit.RateLimiter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalTokenBucketRateLimiterTest {
    private var nowMillis = 0L
    private val clock = { nowMillis }

    private fun limiter(
        limit: Int = 3,
        periodMillis: Long = 3_000,
        initialTokens: Double = limit.toDouble(),
    ) = LocalTokenBucketRateLimiter(limit, periodMillis, initialTokens, clock)

    @Test
    fun `given a full bucket when consuming then it allows exactly the limit then exhausts`() =
        runTest {
            val rateLimiter = limiter(limit = 3, periodMillis = 3_000)

            assertEquals(2, (rateLimiter.tryConsume(1) as RateLimiter.State.Available).remainingTokens)
            assertEquals(1, (rateLimiter.tryConsume(1) as RateLimiter.State.Available).remainingTokens)
            assertEquals(0, (rateLimiter.tryConsume(1) as RateLimiter.State.Available).remainingTokens)
            assertIs<RateLimiter.State.Exhausted>(rateLimiter.tryConsume(1))
        }

    @Test
    fun `given an exhausted bucket when time passes then tokens refill continuously not by fixed window`() =
        runTest {
            val rateLimiter = limiter(limit = 3, periodMillis = 3_000) // 1 token per 1_000 ms
            repeat(3) { rateLimiter.tryConsume(1) }
            assertIs<RateLimiter.State.Exhausted>(rateLimiter.tryConsume(1))

            // A fixed-window limiter would still deny here (same window until 3_000 ms);
            // a true token bucket has refilled exactly one token by 1_000 ms.
            nowMillis = 1_000
            assertIs<RateLimiter.State.Available>(rateLimiter.tryConsume(1))
            assertIs<RateLimiter.State.Exhausted>(rateLimiter.tryConsume(1))
        }

    @Test
    fun `given a partially seeded bucket when consuming beyond the seed then it exhausts immediately`() =
        runTest {
            // Seeded empty: models the degraded fallback — must NOT re-grant a full window.
            val rateLimiter = limiter(limit = 3, periodMillis = 3_000, initialTokens = 0.0)

            assertIs<RateLimiter.State.Exhausted>(rateLimiter.tryConsume(1))
        }

    @Test
    fun `given a consume when available then refillAtTimeMillis is an absolute epoch timestamp`() =
        runTest {
            nowMillis = 5_000
            val rateLimiter = limiter(limit = 2, periodMillis = 2_000)

            val state = rateLimiter.tryConsume(1) as RateLimiter.State.Available
            // remaining 1 of 2 ⇒ 1_000 ms to refill the missing token ⇒ absolute 5_000 + 1_000.
            assertEquals(6_000, state.refillAtTimeMillis)
        }

    @Test
    fun `given initial tokens out of range when constructing then they are clamped to the capacity`() =
        runTest {
            val over = limiter(limit = 2, periodMillis = 2_000, initialTokens = 100.0)
            assertEquals(1, (over.tryConsume(1) as RateLimiter.State.Available).remainingTokens)
            assertEquals(0, (over.tryConsume(1) as RateLimiter.State.Available).remainingTokens)
            assertIs<RateLimiter.State.Exhausted>(over.tryConsume(1))

            val under = limiter(limit = 2, periodMillis = 2_000, initialTokens = -5.0)
            assertIs<RateLimiter.State.Exhausted>(under.tryConsume(1))
        }

    @Test
    fun `given the default system clock when consuming then it works without an injected clock`() =
        runTest {
            val rateLimiter = LocalTokenBucketRateLimiter(limit = 1, refillPeriodMillis = 60_000)

            val state = rateLimiter.tryConsume(1)
            assertIs<RateLimiter.State.Available>(state)
            assertTrue(state.refillAtTimeMillis > 0)
        }
}
