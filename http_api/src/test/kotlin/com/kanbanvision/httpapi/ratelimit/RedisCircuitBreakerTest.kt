package com.kanbanvision.httpapi.ratelimit

import io.lettuce.core.RedisException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedisCircuitBreakerTest {
    @BeforeTest
    @AfterTest
    fun resetBreaker() = RedisCircuitBreaker.reset()

    @Test
    fun `given a forced-open breaker when queried then isOpen is true until reset`() {
        assertFalse(RedisCircuitBreaker.isOpen())
        RedisCircuitBreaker.forceOpen()
        assertTrue(RedisCircuitBreaker.isOpen())
        RedisCircuitBreaker.reset()
        assertFalse(RedisCircuitBreaker.isOpen())
    }

    @Test
    fun `given a closed breaker when executing then it returns the block result`() =
        runTest {
            assertEquals(42, RedisCircuitBreaker.executeSuspend { 42 })
        }

    @Test
    fun `given a failing block when executing then the redis exception propagates`() =
        runTest {
            assertFailsWith<RedisException> {
                RedisCircuitBreaker.executeSuspend { throw RedisException("boom") }
            }
        }

    @Test
    fun `given a meter registry when binding metrics then it does not throw`() {
        RedisCircuitBreaker.bindMetrics(SimpleMeterRegistry())
    }
}
