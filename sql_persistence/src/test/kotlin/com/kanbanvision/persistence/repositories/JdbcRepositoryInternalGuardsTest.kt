package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class JdbcRepositoryInternalGuardsTest {
    @Test
    fun `given cancellation exception when mapping persistence error then repositories rethrow cancellation`() {
        assertFailsWith<CancellationException> { invokeToPersistenceError(JdbcBoardRepository(), CancellationException()) }
        assertFailsWith<CancellationException> { invokeToPersistenceError(JdbcStepRepository(), CancellationException()) }
        assertFailsWith<CancellationException> { invokeToPersistenceError(JdbcCardRepository(), CancellationException()) }
        assertFailsWith<CancellationException> { invokeToPersistenceError(JdbcOrganizationRepository(), CancellationException()) }
        assertFailsWith<CancellationException> { invokeToPersistenceError(JdbcSimulationRepository(), CancellationException()) }
        assertFailsWith<CancellationException> { invokeToPersistenceError(JdbcSnapshotRepository(), CancellationException()) }
    }

    @Test
    fun `given generic exception without message when mapping persistence error then repositories use default database error message`() {
        assertDefaultMessage(invokeToPersistenceError(JdbcBoardRepository(), RuntimeException()))
        assertDefaultMessage(invokeToPersistenceError(JdbcStepRepository(), RuntimeException()))
        assertDefaultMessage(invokeToPersistenceError(JdbcCardRepository(), RuntimeException()))
        assertDefaultMessage(invokeToPersistenceError(JdbcOrganizationRepository(), RuntimeException()))
        assertDefaultMessage(invokeToPersistenceError(JdbcSimulationRepository(), RuntimeException()))
        assertDefaultMessage(invokeToPersistenceError(JdbcSnapshotRepository(), RuntimeException()))
    }

    private fun assertDefaultMessage(error: DomainError) {
        val persistenceError = assertIs<DomainError.PersistenceError>(error)
        assertEquals("Database error", persistenceError.message)
    }

    private fun invokeToPersistenceError(
        target: Any,
        throwable: Throwable,
    ): DomainError {
        val method = target.javaClass.getDeclaredMethod("toPersistenceError", Throwable::class.java)
        method.isAccessible = true
        return try {
            method.invoke(target, throwable) as DomainError
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
