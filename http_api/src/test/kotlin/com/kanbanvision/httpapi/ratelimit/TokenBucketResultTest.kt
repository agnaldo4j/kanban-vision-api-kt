package com.kanbanvision.httpapi.ratelimit

import io.ktor.server.plugins.ratelimit.RateLimiter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

class TokenBucketResultTest {
    @Test
    fun `given an allowed result when mapping then it is Available with the mapped fields`() {
        val state =
            TokenBucketResult(allowed = true, remaining = 3, limit = 10, refillAtEpochMillis = 999, waitMillis = 0)
                .toState()

        val available = assertIs<RateLimiter.State.Available>(state)
        assertEquals(3, available.remainingTokens)
        assertEquals(10, available.limit)
        assertEquals(999, available.refillAtTimeMillis)
    }

    @Test
    fun `given a denied result when mapping then it is Exhausted with the wait duration`() {
        val state =
            TokenBucketResult(allowed = false, remaining = 0, limit = 10, refillAtEpochMillis = 0, waitMillis = 4_200)
                .toState()

        val exhausted = assertIs<RateLimiter.State.Exhausted>(state)
        assertEquals(4_200.milliseconds, exhausted.toWait)
    }
}
