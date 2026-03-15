package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.usecases.repositories.ColumnRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JdbcColumnRepository : ColumnRepository {
    private companion object {
        const val COL_ID = 1
        const val COL_BOARD_ID = 2
        const val COL_NAME = 3
        const val COL_POSITION = 4
    }

    private suspend fun <T> query(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    override suspend fun save(column: Column): Column =
        query {
            DatabaseFactory.dataSource.connection.use { conn ->
                conn
                    .prepareStatement(
                        """
                        INSERT INTO columns (id, board_id, name, position)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET
                            name     = EXCLUDED.name,
                            position = EXCLUDED.position
                        """.trimIndent(),
                    ).use { stmt ->
                        stmt.setString(COL_ID, column.id.value)
                        stmt.setString(COL_BOARD_ID, column.boardId.value)
                        stmt.setString(COL_NAME, column.name)
                        stmt.setInt(COL_POSITION, column.position)
                        stmt.executeUpdate()
                    }
                conn.commit()
            }
            column
        }

    override suspend fun findById(id: ColumnId): Column? =
        query {
            DatabaseFactory.dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT id, board_id, name, position FROM columns WHERE id = ?").use { stmt ->
                    stmt.setString(COL_ID, id.value)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.toColumn() else null
                    }
                }
            }
        }

    override suspend fun findByBoardId(boardId: BoardId): List<Column> =
        query {
            DatabaseFactory.dataSource.connection.use { conn ->
                conn
                    .prepareStatement(
                        "SELECT id, board_id, name, position FROM columns WHERE board_id = ? ORDER BY position",
                    ).use { stmt ->
                        stmt.setString(COL_ID, boardId.value)
                        stmt.executeQuery().use { rs ->
                            buildList { while (rs.next()) add(rs.toColumn()) }
                        }
                    }
            }
        }

    override suspend fun delete(id: ColumnId): Boolean =
        query {
            DatabaseFactory.dataSource.connection.use { conn ->
                val deleted =
                    conn.prepareStatement("DELETE FROM columns WHERE id = ?").use { stmt ->
                        stmt.setString(COL_ID, id.value)
                        stmt.executeUpdate()
                    }
                conn.commit()
                deleted > 0
            }
        }

    private fun java.sql.ResultSet.toColumn() =
        Column(
            id = ColumnId(getString("id")),
            boardId = BoardId(getString("board_id")),
            name = getString("name"),
            position = getInt("position"),
        )
}
