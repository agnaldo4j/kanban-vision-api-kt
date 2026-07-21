package com.kanbanvision.httpapi.ratelimit

import com.kanbanvision.httpapi.ratelimit.redis.defaultRateLimiterFactory
import io.ktor.server.application.ApplicationCall
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RateLimiterFactoryTest {
    private val anyCall = mockk<ApplicationCall>()

    private class StubGateway : RedisTokenBucketGateway {
        override suspend fun consume(
            key: String,
            limit: Int,
            refillPeriodMillis: Long,
            tokens: Int,
        ) = TokenBucketResult(allowed = true, remaining = 1, limit = limit, refillAtEpochMillis = 0, waitMillis = 0)
    }

    @Test
    fun `given no gateway when building a provider then it yields an in-memory limiter`() {
        val provider = RateLimiterFactory().provider("global", limit = 5, windowMillis = 60_000)

        assertIs<LocalTokenBucketRateLimiter>(provider(anyCall, "1.2.3.4"))
    }

    @Test
    fun `given a gateway when building a provider then it yields a Redis limiter`() {
        val provider = RateLimiterFactory(gateway = StubGateway()).provider("auth", limit = 5, windowMillis = 60_000)

        assertIs<RedisRateLimiter>(provider(anyCall, "1.2.3.4"))
    }

    @Test
    fun `given two client keys when building limiters then each key gets its own instance`() {
        val provider = RateLimiterFactory().provider("global", limit = 5, windowMillis = 60_000)

        val first = provider(anyCall, "1.2.3.4")
        val second = provider(anyCall, "5.6.7.8")

        assertTrue(first !== second)
    }

    @Test
    fun `given a closeable when closing the factory then it is released`() {
        var closed = false
        RateLimiterFactory(closeable = AutoCloseable { closed = true }).close()
        assertTrue(closed)
    }

    @Test
    fun `given no closeable when closing the factory then it is a no-op`() {
        RateLimiterFactory().close()
    }

    @Test
    fun `loadRedisUrl returns null when absent or blank and trims a real value`() {
        assertNull(loadRedisUrl { null })
        assertNull(loadRedisUrl { "" })
        assertNull(loadRedisUrl { "   " })
        assertEquals("redis://cache:6379", loadRedisUrl { "redis://cache:6379" })
        // No-arg path (default System::getenv) — mirror the real env so it never flakes.
        assertEquals(System.getenv("RATE_LIMIT_REDIS_URL")?.takeIf { it.isNotBlank() }, loadRedisUrl())
    }

    @Test
    fun `defaultRateLimiterFactory uses in-memory when no redis url is set`() {
        val factory = defaultRateLimiterFactory(env = { null })
        assertIs<LocalTokenBucketRateLimiter>(factory.provider("global", 5, 60_000)(anyCall, "ip"))
    }

    @Test
    fun `defaultRateLimiterFactory builds the redis factory when the url is set`() {
        val redisFactory = RateLimiterFactory(gateway = StubGateway())
        val result = defaultRateLimiterFactory(env = { "redis://cache:6379" }, redisFactory = { redisFactory })
        assertSame(redisFactory, result)
    }

    @Test
    fun `defaultRateLimiterFactory degrades to in-memory when the redis backend fails to init`() {
        val factory =
            defaultRateLimiterFactory(env = { "redis://cache:6379" }, redisFactory = { error("Redis down at boot") })
        assertIs<LocalTokenBucketRateLimiter>(factory.provider("global", 5, 60_000)(anyCall, "ip"))
    }

    @Test
    fun `defaultRateLimiterFactory no-arg resolves from the process environment`() {
        defaultRateLimiterFactory().close()
    }
}
