package com.kanbanvision.httpapi.ratelimit

import io.ktor.server.plugins.ratelimit.RateLimiter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class TokenBucketResultTest {
    @Test
    fun `given an allowed result when mapping then it is Available with the mapped fields`() {
        val result = TokenBucketResult(allowed = true, remaining = 3, limit = 10, refillAtEpochMillis = 999, waitMillis = 0)

        assertTrue(result.allowed)
        assertEquals(3, result.remaining)
        assertEquals(10, result.limit)
        assertEquals(999, result.refillAtEpochMillis)
        assertEquals(0, result.waitMillis)

        val available = assertIs<RateLimiter.State.Available>(result.toState())
        assertEquals(3, available.remainingTokens)
        assertEquals(10, available.limit)
        assertEquals(999, available.refillAtTimeMillis)
    }

    @Test
    fun `given a denied result when mapping then it is Exhausted with the wait duration`() {
        val result = TokenBucketResult(allowed = false, remaining = 0, limit = 10, refillAtEpochMillis = 0, waitMillis = 4_200)

        assertFalse(result.allowed)
        assertEquals(4_200, result.waitMillis)

        val exhausted = assertIs<RateLimiter.State.Exhausted>(result.toState())
        assertEquals(4_200.milliseconds, exhausted.toWait)
    }
}
