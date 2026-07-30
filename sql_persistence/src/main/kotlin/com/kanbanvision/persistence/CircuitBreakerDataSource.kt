package com.kanbanvision.persistence

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import java.sql.Connection
import javax.sql.DataSource

class CircuitBreakerDataSource(
    private val delegate: DataSource,
    private val circuitBreaker: CircuitBreaker,
) : DataSource by delegate {
    override fun getConnection(): Connection {
        rejectWhenOpen()
        return delegate.connection
    }

    override fun getConnection(
        username: String?,
        password: String?,
    ): Connection {
        rejectWhenOpen()
        return delegate.getConnection(username, password)
    }

    // Gate puro por estado: não adquire permit nem registra sucesso/falha de propósito. Em HALF_OPEN os
    // probes precisam alcançar o banco para validar a recuperação — disputar permits aqui os rejeitaria,
    // e registrar aqui contaria a mesma falha uma vez por camada (retries do Exposed × dbQuery).
    private fun rejectWhenOpen() {
        val state = circuitBreaker.state
        if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
            throw CallNotPermittedException.createCallNotPermittedException(circuitBreaker)
        }
    }
}
