package com.kanbanvision.persistence

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.AfterEach
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DbCircuitBreakerTest {
    @AfterEach
    fun tearDown() {
        // Singleton compartilhado entre classes de teste — sempre devolver ao estado CLOSED.
        DbCircuitBreaker.reset()
    }

    @Test
    fun `given adr-0020 configuration when breaker is built then thresholds match the decision`() {
        val config = DbCircuitBreaker.circuitBreaker.circuitBreakerConfig

        assertEquals(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED, config.slidingWindowType)
        assertEquals(10, config.slidingWindowSize)
        assertEquals(10, config.minimumNumberOfCalls)
        assertEquals(50f, config.failureRateThreshold)
        assertEquals(80f, config.slowCallRateThreshold)
        assertEquals(Duration.ofSeconds(5), config.slowCallDurationThreshold)
        assertEquals(3, config.permittedNumberOfCallsInHalfOpenState)
        assertTrue(config.isAutomaticTransitionFromOpenToHalfOpenEnabled)
        assertEquals("database", DbCircuitBreaker.circuitBreaker.name)
    }

    @Test
    fun `given breaker state transitions when checking isOpen then open and forced open are detected`() {
        assertFalse(DbCircuitBreaker.isOpen())

        DbCircuitBreaker.circuitBreaker.transitionToOpenState()
        assertTrue(DbCircuitBreaker.isOpen())
        assertEquals(CircuitBreaker.State.OPEN, DbCircuitBreaker.circuitBreaker.state)

        DbCircuitBreaker.reset()
        assertFalse(DbCircuitBreaker.isOpen())

        DbCircuitBreaker.forceOpen()
        assertTrue(DbCircuitBreaker.isOpen())
    }

    @Test
    fun `given meter registry when binding metrics then circuit breaker state gauge is registered`() {
        val meterRegistry = SimpleMeterRegistry()

        DbCircuitBreaker.bindMetrics(meterRegistry)

        assertTrue(
            meterRegistry.meters.any { it.id.name == "resilience4j.circuitbreaker.state" },
            "expected resilience4j.circuitbreaker.state gauge to be bound",
        )
    }
}
