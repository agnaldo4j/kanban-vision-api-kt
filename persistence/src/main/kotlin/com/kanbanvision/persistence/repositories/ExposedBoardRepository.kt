package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.port.BoardRepository
import com.kanbanvision.persistence.tables.Boards
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

class ExposedBoardRepository : BoardRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    override suspend fun save(board: Board): Board = dbQuery {
        Boards.upsert {
            it[id] = board.id.value
            it[name] = board.name
            it[createdAt] = board.createdAt.toKotlinInstant()
        }
        board
    }

    override suspend fun findById(id: BoardId): Board? = dbQuery {
        Boards.selectAll()
            .where { Boards.id eq id.value }
            .singleOrNull()
            ?.toBoard()
    }

    override suspend fun findAll(): List<Board> = dbQuery {
        Boards.selectAll().map { it.toBoard() }
    }

    override suspend fun delete(id: BoardId): Boolean = dbQuery {
        Boards.deleteWhere { Boards.id eq id.value } > 0
    }

    private fun ResultRow.toBoard(): Board = Board(
        id = BoardId(this[Boards.id]),
        name = this[Boards.name],
        createdAt = Instant.ofEpochSecond(this[Boards.createdAt].epochSeconds),
    )
}
