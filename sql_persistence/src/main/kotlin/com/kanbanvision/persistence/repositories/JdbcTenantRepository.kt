package com.kanbanvision.persistence.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.tenant.Tenant
import com.kanbanvision.domain.model.valueobjects.TenantId
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.usecases.repositories.TenantRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class JdbcTenantRepository : TenantRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun toPersistenceError(e: Throwable): DomainError {
        if (e is CancellationException) throw e
        log.error("Persistence error", e)
        return DomainError.PersistenceError(e.message ?: "Database error")
    }

    private suspend fun <T> query(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    override suspend fun findById(id: TenantId): Either<DomainError, Tenant> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn.prepareStatement("SELECT id, name FROM tenants WHERE id = ?").use { stmt ->
                            stmt.setString(1, id.value)
                            stmt.executeQuery().use { rs ->
                                if (rs.next()) rs.toTenant() else null
                            }
                        }
                    }
                }.fold(
                    ifLeft = { toPersistenceError(it).left() },
                    ifRight = { tenant -> tenant?.right() ?: DomainError.TenantNotFound(id.value).left() },
                )
        }

    private fun java.sql.ResultSet.toTenant() =
        Tenant(
            id = TenantId(getString("id")),
            name = getString("name"),
        )
}
