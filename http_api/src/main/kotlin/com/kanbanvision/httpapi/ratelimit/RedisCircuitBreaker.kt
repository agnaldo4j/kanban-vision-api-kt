package com.kanbanvision.httpapi.ratelimit

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics
import io.lettuce.core.RedisException
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import java.time.Duration

private val logger = LoggerFactory.getLogger(RedisCircuitBreaker::class.java)

object RedisCircuitBreaker {
    private const val CIRCUIT_NAME = "redis"
    private const val SLIDING_WINDOW_SIZE = 10
    private const val MINIMUM_NUMBER_OF_CALLS = 10
    private const val FAILURE_RATE_THRESHOLD_PCT = 50f
    private const val SLOW_CALL_RATE_THRESHOLD_PCT = 80f

    internal const val SLOW_CALL_DURATION_MILLIS = 200L
    private const val WAIT_IN_OPEN_STATE_SECS = 10L
    private const val PERMITTED_CALLS_IN_HALF_OPEN = 3

    val registry: CircuitBreakerRegistry = CircuitBreakerRegistry.of(buildConfig())
    val circuitBreaker: CircuitBreaker = registry.circuitBreaker(CIRCUIT_NAME)

    init {
        circuitBreaker.eventPublisher.onStateTransition { event ->
            when (event.stateTransition.toState) {
                CircuitBreaker.State.OPEN, CircuitBreaker.State.FORCED_OPEN ->
                    logger.warn(
                        "Redis rate-limit backend unavailable — the limiter degraded to a per-pod " +
                            "in-memory bucket; the shared ceiling is diluted under the HPA until Redis recovers.",
                    )
                CircuitBreaker.State.CLOSED ->
                    logger.info("Redis rate-limit backend recovered — the limiter is shared again.")
                else -> Unit
            }
        }
    }

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
            .slowCallDurationThreshold(Duration.ofMillis(SLOW_CALL_DURATION_MILLIS))
            .waitDurationInOpenState(Duration.ofSeconds(WAIT_IN_OPEN_STATE_SECS))
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .permittedNumberOfCallsInHalfOpenState(PERMITTED_CALLS_IN_HALF_OPEN)
            .recordExceptions(RedisException::class.java)
            .ignoreExceptions(CallNotPermittedException::class.java)
            .build()
}
