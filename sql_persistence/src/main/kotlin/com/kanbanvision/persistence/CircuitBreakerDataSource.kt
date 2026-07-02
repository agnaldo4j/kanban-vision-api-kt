package com.kanbanvision.persistence

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import java.sql.Connection
import javax.sql.DataSource

/**
 * Decora um [DataSource] rejeitando o checkout de conexões com o circuito aberto (ADR-0020):
 * `getConnection` falha imediatamente com `CallNotPermittedException` em vez de aguardar o
 * timeout do pool.
 *
 * O decorator é um gate puro por estado — não registra sucesso/falha nem toca na API de
 * permits. Em HALF_OPEN o checkout passa direto: os probes são limitados pela camada de
 * `dbQuery` (`PersistenceSupport`), que já adquiriu o permit, e precisam alcançar o banco
 * para validar a recuperação; disputar permits aqui rejeitaria probes legítimos. Registrar
 * aqui também contaria a mesma falha várias vezes (retries do Exposed × camadas).
 */
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

    private fun rejectWhenOpen() {
        val state = circuitBreaker.state
        if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
            throw CallNotPermittedException.createCallNotPermittedException(circuitBreaker)
        }
    }
}
