package com.kanbanvision.httpapi.ratelimit

import com.kanbanvision.httpapi.ratelimit.redis.COMMAND_TIMEOUT_MILLIS
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class RedisSlowCallBelowCommandTimeoutTest {
    @Test
    fun `given the breaker slow-call threshold when compared to the Lettuce command timeout then it is strictly below`() {
        assertTrue(
            RedisCircuitBreaker.SLOW_CALL_DURATION_MILLIS < COMMAND_TIMEOUT_MILLIS,
            "A slow-call threshold at or above the command timeout is dead config: a call that slow has " +
                "already timed out and counts as a failure, so the breaker would never trip on slowness. " +
                "Raising COMMAND_TIMEOUT_MILLIS is fine; raising SLOW_CALL_DURATION_MILLIS past it is not.",
        )
    }
}
