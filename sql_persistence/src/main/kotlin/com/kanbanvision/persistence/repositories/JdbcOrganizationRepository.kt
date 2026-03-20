package com.kanbanvision.persistence.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Organization
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.usecases.repositories.OrganizationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class JdbcOrganizationRepository : OrganizationRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun toPersistenceError(e: Throwable): DomainError {
        if (e is CancellationException) throw e
        log.error("Persistence error", e)
        return DomainError.PersistenceError(e.message ?: "Database error")
    }

    private suspend fun <T> query(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    override suspend fun findById(id: String): Either<DomainError, Organization> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn.prepareStatement("SELECT id, name FROM organizations WHERE id = ?").use { stmt ->
                            stmt.setString(1, id)
                            stmt.executeQuery().use { rs ->
                                if (rs.next()) rs.toOrganization() else null
                            }
                        }
                    }
                }.fold(
                    ifLeft = { toPersistenceError(it).left() },
                    ifRight = { organization -> organization?.right() ?: DomainError.OrganizationNotFound(id).left() },
                )
        }

    private fun java.sql.ResultSet.toOrganization() =
        Organization(
            id = getString("id"),
            name = getString("name"),
        )
}
