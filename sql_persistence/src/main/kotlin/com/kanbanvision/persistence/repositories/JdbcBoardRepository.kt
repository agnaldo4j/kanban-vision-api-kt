package com.kanbanvision.persistence.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.Board
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.usecases.repositories.BoardRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Instant

class JdbcBoardRepository : BoardRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        const val COL_ID = 1
        const val COL_NAME = 2
        const val COL_CREATED_AT = 3
    }

    private fun toPersistenceError(e: Throwable): DomainError {
        if (e is CancellationException) throw e
        log.error("Persistence error", e)
        return DomainError.PersistenceError(e.message ?: "Database error")
    }

    private suspend fun <T> query(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    override suspend fun save(board: Board): Either<DomainError, Board> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn
                            .prepareStatement(
                                """
                                INSERT INTO boards (id, name, created_at)
                                VALUES (?, ?, ?)
                                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name
                                """.trimIndent(),
                            ).use { stmt ->
                                stmt.setString(COL_ID, board.id)
                                stmt.setString(COL_NAME, board.name)
                                stmt.setLong(COL_CREATED_AT, board.audit.createdAt.toEpochMilli())
                                stmt.executeUpdate()
                            }
                        conn.commit()
                    }
                    board
                }.mapLeft(::toPersistenceError)
        }

    override suspend fun findById(id: String): Either<DomainError, Board> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn.prepareStatement("SELECT id, name, created_at FROM boards WHERE id = ?").use { stmt ->
                            stmt.setString(COL_ID, id)
                            stmt.executeQuery().use { rs ->
                                if (rs.next()) rs.toBoard() else null
                            }
                        }
                    }
                }.fold(
                    ifLeft = { toPersistenceError(it).left() },
                    ifRight = { board -> board?.right() ?: DomainError.BoardNotFound(id).left() },
                )
        }

    private fun java.sql.ResultSet.toBoard() =
        Board(
            id = getString("id"),
            name = getString("name"),
            audit = Audit(createdAt = Instant.ofEpochMilli(getLong("created_at"))),
        )
}
