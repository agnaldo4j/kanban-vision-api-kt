package com.kanbanvision.httpapi.ratelimit

import io.ktor.server.plugins.ratelimit.RateLimiter
import io.lettuce.core.RedisException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RedisRateLimiterTest {
    @BeforeTest
    @AfterTest
    fun resetBreaker() = RedisCircuitBreaker.reset()

    private class FakeGateway(
        var result: TokenBucketResult? = null,
        var error: Exception? = null,
    ) : RedisTokenBucketGateway {
        var calls = 0

        override suspend fun consume(
            key: String,
            limit: Int,
            refillPeriodMillis: Long,
            tokens: Int,
        ): TokenBucketResult {
            calls++
            error?.let { throw it }
            return result ?: error("no result configured")
        }
    }

    @Test
    fun `given a successful call when consuming then it maps the Redis result`() =
        runTest {
            val gateway =
                FakeGateway(TokenBucketResult(allowed = true, remaining = 7, limit = 10, refillAtEpochMillis = 123, waitMillis = 0))

            val state = RedisRateLimiter("ratelimit:global:ip", 10, 60_000, gateway).tryConsume(1)

            val available = assertIs<RateLimiter.State.Available>(state)
            assertEquals(7, available.remainingTokens)
        }

    @Test
    fun `given an exhausted bucket when consuming then it maps to Exhausted`() =
        runTest {
            val gateway =
                FakeGateway(TokenBucketResult(allowed = false, remaining = 0, limit = 5, refillAtEpochMillis = 0, waitMillis = 5_000))

            assertIs<RateLimiter.State.Exhausted>(RedisRateLimiter("ratelimit:auth:ip", 5, 60_000, gateway).tryConsume(1))
        }

    @Test
    fun `given a Redis failure after draining when consuming then the fallback is seeded and does not re-grant a full window`() =
        runTest {
            val gateway = FakeGateway()
            val limiter = RedisRateLimiter("ratelimit:global:ip", 3, 3_000, gateway)

            // First call observes the shared bucket already drained (remaining = 0).
            gateway.result = TokenBucketResult(allowed = true, remaining = 0, limit = 3, refillAtEpochMillis = 100, waitMillis = 0)
            limiter.tryConsume(1)

            // Redis now fails ⇒ fallback seeded from 0 ⇒ immediately exhausted (no fresh full window).
            gateway.result = null
            gateway.error = RedisException("connection reset")
            assertIs<RateLimiter.State.Exhausted>(limiter.tryConsume(1))
        }

    @Test
    fun `given an open breaker when consuming then it falls back without touching the gateway`() =
        runTest {
            val gateway = FakeGateway(TokenBucketResult(allowed = true, remaining = 5, limit = 5, refillAtEpochMillis = 1, waitMillis = 0))
            RedisCircuitBreaker.forceOpen()

            val state = RedisRateLimiter("ratelimit:global:ip", 5, 60_000, gateway).tryConsume(1)

            assertIs<RateLimiter.State.Available>(state) // local fallback seeded full (no observation yet)
            assertEquals(0, gateway.calls)
        }

    @Test
    fun `given a cancellation when consuming then it propagates instead of degrading`() =
        runTest {
            val gateway = FakeGateway(error = CancellationException("cancelled"))

            assertFailsWith<CancellationException> {
                RedisRateLimiter("ratelimit:global:ip", 5, 60_000, gateway).tryConsume(1)
            }
        }
}
