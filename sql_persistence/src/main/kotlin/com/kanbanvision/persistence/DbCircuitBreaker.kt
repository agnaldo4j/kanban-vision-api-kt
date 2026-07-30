package com.kanbanvision.persistence

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics
import io.micrometer.core.instrument.MeterRegistry
import java.sql.SQLException
import java.sql.SQLTimeoutException
import java.time.Duration

object DbCircuitBreaker {
    private const val CIRCUIT_NAME = "database"
    private const val SLIDING_WINDOW_SIZE = 10
    private const val MINIMUM_NUMBER_OF_CALLS = 10
    private const val FAILURE_RATE_THRESHOLD_PCT = 50f
    private const val SLOW_CALL_RATE_THRESHOLD_PCT = 80f
    private const val SLOW_CALL_DURATION_SECS = 5L
    private const val WAIT_IN_OPEN_STATE_SECS = 30L
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
            // Sem transição automática o circuito trava aberto: o pod sai do load balancer, nada atravessa
            // o breaker e OPEN nunca chega a HALF_OPEN, mesmo com o banco já recuperado.
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .permittedNumberOfCallsInHalfOpenState(PERMITTED_CALLS_IN_HALF_OPEN)
            .recordExceptions(SQLException::class.java, SQLTimeoutException::class.java)
            // A rejeição do próprio gate não é sucesso nem falha do banco — contá-la realimentaria o breaker.
            .ignoreExceptions(CallNotPermittedException::class.java)
            .build()
}
