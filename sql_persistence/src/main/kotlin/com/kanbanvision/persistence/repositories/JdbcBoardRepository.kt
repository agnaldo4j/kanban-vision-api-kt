package com.kanbanvision.persistence.repositories

import arrow.core.Either
import arrow.core.left
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.Board
import com.kanbanvision.persistence.dbQuery
import com.kanbanvision.persistence.tables.BoardsTable
import com.kanbanvision.usecases.repositories.BoardRepository
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.time.Instant

class JdbcBoardRepository : BoardRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun save(board: Board): Either<DomainError, Board> =
        dbQuery(log) {
            BoardsTable.upsert {
                it[id] = board.id
                it[name] = board.name
                it[createdAt] = board.audit.createdAt.toEpochMilli()
            }
            board
        }

    override suspend fun findById(id: String): Either<DomainError, Board> =
        dbQuery(log) {
            BoardsTable
                .selectAll()
                .where { BoardsTable.id eq id }
                .singleOrNull()
                ?.let { row ->
                    Board(
                        id = row[BoardsTable.id],
                        name = row[BoardsTable.name],
                        audit = Audit(createdAt = Instant.ofEpochMilli(row[BoardsTable.createdAt])),
                    )
                }
        }.fold(
            ifLeft = { it.left() },
            ifRight = { board -> board?.let { Either.Right(it) } ?: DomainError.BoardNotFound(id).left() },
        )
}
