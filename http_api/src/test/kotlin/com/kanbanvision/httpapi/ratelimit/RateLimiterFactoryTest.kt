package com.kanbanvision.httpapi.ratelimit

import io.ktor.server.application.ApplicationCall
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RateLimiterFactoryTest {
    private val anyCall = mockk<ApplicationCall>()

    @Test
    fun `given the provider when invoked then it yields an in-memory token bucket limiter`() {
        val provider = RateLimiterFactory().provider(limit = 5, windowMillis = 60_000)

        assertIs<LocalTokenBucketRateLimiter>(provider(anyCall, "1.2.3.4"))
    }

    @Test
    fun `given two client keys when building limiters then each key gets its own instance`() {
        val provider = RateLimiterFactory().provider(limit = 5, windowMillis = 60_000)

        val first = provider(anyCall, "1.2.3.4")
        val second = provider(anyCall, "5.6.7.8")

        // Independent instances ⇒ independent quotas (Ktor also partitions global vs auth by name).
        assertIs<LocalTokenBucketRateLimiter>(first)
        assertIs<LocalTokenBucketRateLimiter>(second)
        assert(first !== second)
    }

    @Test
    fun `given close when there is no distributed backend then it is a no-op`() {
        RateLimiterFactory().close()
    }

    @Test
    fun `loadRedisUrl returns null when absent or blank and trims a real value`() {
        assertNull(loadRedisUrl { null })
        assertNull(loadRedisUrl { "" })
        assertNull(loadRedisUrl { "   " })
        assertEquals("redis://cache:6379", loadRedisUrl { "redis://cache:6379" })
        // No-arg path (default System::getenv); RATE_LIMIT_REDIS_URL is unset in CI/dev.
        assertNull(loadRedisUrl())
    }

    @Test
    fun `defaultRateLimiterFactory falls back to in-memory whether or not the redis url is set`() {
        assertIs<RateLimiterFactory>(defaultRateLimiterFactory { null })
        // The set-but-unwired branch logs a warning and still returns the in-memory factory.
        assertIs<RateLimiterFactory>(defaultRateLimiterFactory { "redis://cache:6379" })
        // No-arg path (default System::getenv).
        defaultRateLimiterFactory().close()
    }
}
