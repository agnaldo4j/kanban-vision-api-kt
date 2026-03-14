package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.usecases.repositories.BoardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class JdbcBoardRepository : BoardRepository {
    private companion object {
        const val COL_ID = 1
        const val COL_NAME = 2
        const val COL_CREATED_AT = 3
    }

    private suspend fun <T> query(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    override suspend fun save(board: Board): Board =
        query {
            DatabaseFactory.dataSource.connection.use { conn ->
                conn
                    .prepareStatement(
                        """
                        INSERT INTO boards (id, name, created_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name
                        """.trimIndent(),
                    ).use { stmt ->
                        stmt.setString(COL_ID, board.id.value)
                        stmt.setString(COL_NAME, board.name)
                        stmt.setLong(COL_CREATED_AT, board.createdAt.toEpochMilli())
                        stmt.executeUpdate()
                    }
                conn.commit()
            }
            board
        }

    override suspend fun findById(id: BoardId): Board? =
        query {
            DatabaseFactory.dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT id, name, created_at FROM boards WHERE id = ?").use { stmt ->
                    stmt.setString(COL_ID, id.value)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.toBoard() else null
                    }
                }
            }
        }

    override suspend fun findAll(): List<Board> =
        query {
            DatabaseFactory.dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT id, name, created_at FROM boards").use { stmt ->
                    stmt.executeQuery().use { rs ->
                        buildList { while (rs.next()) add(rs.toBoard()) }
                    }
                }
            }
        }

    override suspend fun delete(id: BoardId): Boolean =
        query {
            DatabaseFactory.dataSource.connection.use { conn ->
                val deleted =
                    conn.prepareStatement("DELETE FROM boards WHERE id = ?").use { stmt ->
                        stmt.setString(COL_ID, id.value)
                        stmt.executeUpdate()
                    }
                conn.commit()
                deleted > 0
            }
        }

    private fun java.sql.ResultSet.toBoard() =
        Board(
            id = BoardId(getString("id")),
            name = getString("name"),
            createdAt = Instant.ofEpochMilli(getLong("created_at")),
        )
}
