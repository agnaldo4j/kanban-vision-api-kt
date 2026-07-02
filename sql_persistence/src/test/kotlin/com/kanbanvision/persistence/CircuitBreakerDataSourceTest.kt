package com.kanbanvision.persistence

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CircuitBreakerDataSourceTest {
    private fun testCircuitBreaker(): CircuitBreaker =
        CircuitBreaker.of(
            "test-datasource",
            CircuitBreakerConfig
                .custom()
                .recordExceptions(SQLException::class.java)
                .build(),
        )

    @Test
    fun `given closed circuit when getting connection then connection is delegated`() {
        val connection = mockk<Connection>()
        val delegate = mockk<DataSource> { every { this@mockk.connection } returns connection }

        val dataSource = CircuitBreakerDataSource(delegate, testCircuitBreaker())

        assertSame(connection, dataSource.connection)
    }

    @Test
    fun `given closed circuit when getting connection with credentials then overload is delegated`() {
        val connection = mockk<Connection>()
        val delegate = mockk<DataSource> { every { getConnection("user", "pass") } returns connection }

        val dataSource = CircuitBreakerDataSource(delegate, testCircuitBreaker())

        assertSame(connection, dataSource.getConnection("user", "pass"))
    }

    @Test
    fun `given open circuit when getting connection then call fails fast without touching delegate`() {
        val delegate = mockk<DataSource>()
        val circuitBreaker = testCircuitBreaker()
        val dataSource = CircuitBreakerDataSource(delegate, circuitBreaker)
        circuitBreaker.transitionToOpenState()

        assertFailsWith<CallNotPermittedException> { dataSource.connection }
        assertFailsWith<CallNotPermittedException> { dataSource.getConnection("user", "pass") }
        verify(exactly = 0) { delegate.connection }
    }

    @Test
    fun `given failing delegate when getting connection then exception propagates without being recorded`() {
        val delegate = mockk<DataSource> { every { this@mockk.connection } throws SQLException("db down") }
        val circuitBreaker = testCircuitBreaker()
        val dataSource = CircuitBreakerDataSource(delegate, circuitBreaker)

        assertFailsWith<SQLException> { dataSource.connection }

        // Gate puro: o registro de falhas é responsabilidade exclusiva da camada dbQuery.
        assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }
}
