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
 * O decorator é um gate puro — não registra sucesso/falha nem consome permits de HALF_OPEN.
 * Quem registra os resultados no breaker é a camada de `dbQuery` (`PersistenceSupport`), o
 * ponto único por onde passa todo acesso dos repositórios; registrar aqui também contaria a
 * mesma falha várias vezes (retries do Exposed × camadas) e abriria o circuito cedo demais.
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
        if (!circuitBreaker.tryAcquirePermission()) {
            throw CallNotPermittedException.createCallNotPermittedException(circuitBreaker)
        }
        circuitBreaker.releasePermission()
    }
}
