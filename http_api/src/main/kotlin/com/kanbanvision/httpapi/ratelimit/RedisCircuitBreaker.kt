package com.kanbanvision.httpapi.ratelimit

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics
import io.lettuce.core.RedisException
import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration

/**
 * Circuit breaker around the Redis rate-limit backend — same idiom as `DbCircuitBreaker`
 * (`sql_persistence`): a top-level `object` wrapping resilience4j, so it needs no Koin wiring.
 *
 * Unlike `DbCircuitBreaker`, this one is **not** wired into readiness: a Redis outage must NOT remove
 * the pod from rotation — the rate limiter simply degrades to a seeded in-memory bucket
 * ([RedisRateLimiter]). It is surfaced only via `/metrics`.
 */
object RedisCircuitBreaker {
    private const val CIRCUIT_NAME = "redis"
    private const val SLIDING_WINDOW_SIZE = 10
    private const val MINIMUM_NUMBER_OF_CALLS = 10
    private const val FAILURE_RATE_THRESHOLD_PCT = 50f
    private const val SLOW_CALL_RATE_THRESHOLD_PCT = 80f
    private const val SLOW_CALL_DURATION_SECS = 1L
    private const val WAIT_IN_OPEN_STATE_SECS = 10L
    private const val PERMITTED_CALLS_IN_HALF_OPEN = 3

    val registry: CircuitBreakerRegistry = CircuitBreakerRegistry.of(buildConfig())
    val circuitBreaker: CircuitBreaker = registry.circuitBreaker(CIRCUIT_NAME)

    fun isOpen(): Boolean =
        circuitBreaker.state == CircuitBreaker.State.OPEN ||
            circuitBreaker.state == CircuitBreaker.State.FORCED_OPEN

    fun reset() = circuitBreaker.reset()

    fun forceOpen() {
        circuitBreaker.transitionToForcedOpenState()
    }

    fun bindMetrics(meterRegistry: MeterRegistry) {
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry)
    }

    suspend fun <T> executeSuspend(block: suspend () -> T): T = circuitBreaker.executeSuspendFunction(block)

    private fun buildConfig(): CircuitBreakerConfig =
        CircuitBreakerConfig
            .custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(SLIDING_WINDOW_SIZE)
            .minimumNumberOfCalls(MINIMUM_NUMBER_OF_CALLS)
            .failureRateThreshold(FAILURE_RATE_THRESHOLD_PCT)
            .slowCallRateThreshold(SLOW_CALL_RATE_THRESHOLD_PCT)
            .slowCallDurationThreshold(Duration.ofSeconds(SLOW_CALL_DURATION_SECS))
            .waitDurationInOpenState(Duration.ofSeconds(WAIT_IN_OPEN_STATE_SECS))
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .permittedNumberOfCallsInHalfOpenState(PERMITTED_CALLS_IN_HALF_OPEN)
            .recordExceptions(RedisException::class.java)
            .ignoreExceptions(CallNotPermittedException::class.java)
            .build()
}
