package com.kanbanvision.persistence

import arrow.core.getOrElse
import com.kanbanvision.domain.errors.CommonError
import com.kanbanvision.persistence.support.EmbeddedPostgresSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private class NullMessageException : IllegalStateException(null as String?)

class PersistenceSupportTest {
    private val log = LoggerFactory.getLogger(javaClass)

    @Test
    fun `given successful block when dbQuery executes then right value is returned`() =
        runBlocking {
            EmbeddedPostgresSupport.ensureStarted()
            EmbeddedPostgresSupport.refreshDataSource()

            val result = dbQuery(log) { 42 }.getOrElse { error("unexpected left: $it") }

            assertEquals(42, result)
        }

    @Test
    fun `given exception with message when dbQuery catches it then persistence error preserves message`() =
        runBlocking {
            EmbeddedPostgresSupport.ensureStarted()
            EmbeddedPostgresSupport.refreshDataSource()

            val error = dbQuery(log) { error("boom") }.leftOrNull()

            assertIs<CommonError.PersistenceError>(error)
            assertEquals("boom", error.message)
        }

    @Test
    fun `given exception with null message when dbQuery catches it then persistence error uses default message`() =
        runBlocking {
            EmbeddedPostgresSupport.ensureStarted()
            EmbeddedPostgresSupport.refreshDataSource()

            val error = dbQuery(log) { throw NullMessageException() }.leftOrNull()

            assertIs<CommonError.PersistenceError>(error)
            assertEquals("Database error", error.message)
        }

    @Test
    fun `given cancellation exception when dbQuery runs then it is rethrown not wrapped`() =
        runBlocking<Unit> {
            EmbeddedPostgresSupport.ensureStarted()
            EmbeddedPostgresSupport.refreshDataSource()

            assertFailsWith<CancellationException> {
                dbQuery(log) { throw CancellationException("cancelled") }
            }
        }

    @Test
    fun `given open circuit when dbQuery runs then service unavailable is returned without executing block`() =
        runBlocking {
            EmbeddedPostgresSupport.ensureStarted()
            EmbeddedPostgresSupport.refreshDataSource()
            DbCircuitBreaker.circuitBreaker.transitionToOpenState()
            try {
                var executed = false

                val error = dbQuery(log) { executed = true }.leftOrNull()

                assertIs<CommonError.ServiceUnavailable>(error)
                assertEquals("database", error.service)
                assertEquals("circuit breaker open", error.reason)
                assertEquals(false, executed)
            } finally {
                DbCircuitBreaker.reset()
            }
        }

    @Test
    fun `given circuit closed again after reset when dbQuery runs then queries succeed`() =
        runBlocking {
            EmbeddedPostgresSupport.ensureStarted()
            EmbeddedPostgresSupport.refreshDataSource()
            DbCircuitBreaker.circuitBreaker.transitionToOpenState()
            DbCircuitBreaker.reset()

            val result = dbQuery(log) { 7 }.getOrElse { error("unexpected left: $it") }

            assertEquals(7, result)
        }
}
