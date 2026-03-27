package com.kanbanvision.persistence.repositories

import arrow.core.Either
import arrow.core.left
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Organization
import com.kanbanvision.persistence.dbQuery
import com.kanbanvision.persistence.tables.OrganizationsTable
import com.kanbanvision.usecases.repositories.OrganizationRepository
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.slf4j.LoggerFactory

class JdbcOrganizationRepository : OrganizationRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun findById(id: String): Either<DomainError, Organization> =
        dbQuery(log) {
            OrganizationsTable
                .selectAll()
                .where { OrganizationsTable.id eq id }
                .singleOrNull()
                ?.let { row ->
                    Organization(
                        id = row[OrganizationsTable.id],
                        name = row[OrganizationsTable.name],
                    )
                }
        }.fold(
            ifLeft = { it.left() },
            ifRight = { org -> org?.let { Either.Right(it) } ?: DomainError.OrganizationNotFound(id).left() },
        )
}
