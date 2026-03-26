package com.kanbanvision.persistence

import arrow.core.getOrElse
import com.kanbanvision.domain.errors.DomainError
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

            assertIs<DomainError.PersistenceError>(error)
            assertEquals("boom", error.message)
        }

    @Test
    fun `given exception with null message when dbQuery catches it then persistence error uses default message`() =
        runBlocking {
            EmbeddedPostgresSupport.ensureStarted()
            EmbeddedPostgresSupport.refreshDataSource()

            val error = dbQuery(log) { throw NullMessageException() }.leftOrNull()

            assertIs<DomainError.PersistenceError>(error)
            assertEquals("Database error", error.message)
        }

    @Test
    fun `given cancellation exception when dbQuery runs then it is rethrown not wrapped`() =
        runBlocking {
            EmbeddedPostgresSupport.ensureStarted()
            EmbeddedPostgresSupport.refreshDataSource()

            assertFailsWith<CancellationException> {
                dbQuery(log) { throw CancellationException("cancelled") }
            }
        }
}
