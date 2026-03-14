package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.usecases.repositories.CardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class JdbcCardRepository : CardRepository {
    private companion object {
        const val COL_ID = 1
        const val COL_COLUMN_ID = 2
        const val COL_TITLE = 3
        const val COL_DESCRIPTION = 4
        const val COL_POSITION = 5
        const val COL_CREATED_AT = 6
    }

    private suspend fun <T> query(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    override suspend fun save(card: Card): Card =
        query {
            DatabaseFactory.dataSource.connection.use { conn ->
                conn
                    .prepareStatement(
                        """
                        INSERT INTO cards (id, column_id, title, description, position, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET
                            column_id   = EXCLUDED.column_id,
                            title       = EXCLUDED.title,
                            description = EXCLUDED.description,
                            position    = EXCLUDED.position
                        """.trimIndent(),
                    ).use { stmt ->
                        stmt.setString(COL_ID, card.id.value)
                        stmt.setString(COL_COLUMN_ID, card.columnId.value)
                        stmt.setString(COL_TITLE, card.title)
                        stmt.setString(COL_DESCRIPTION, card.description)
                        stmt.setInt(COL_POSITION, card.position)
                        stmt.setLong(COL_CREATED_AT, card.createdAt.toEpochMilli())
                        stmt.executeUpdate()
                    }
                conn.commit()
            }
            card
        }

    override suspend fun findById(id: CardId): Card? =
        query {
            DatabaseFactory.dataSource.connection.use { conn ->
                conn
                    .prepareStatement(
                        "SELECT id, column_id, title, description, position, created_at FROM cards WHERE id = ?",
                    ).use { stmt ->
                        stmt.setString(COL_ID, id.value)
                        stmt.executeQuery().use { rs ->
                            if (rs.next()) rs.toCard() else null
                        }
                    }
            }
        }

    override suspend fun findByColumnId(columnId: ColumnId): List<Card> =
        query {
            DatabaseFactory.dataSource.connection.use { conn ->
                conn
                    .prepareStatement(
                        "SELECT id, column_id, title, description, position, created_at FROM cards WHERE column_id = ? ORDER BY position",
                    ).use { stmt ->
                        stmt.setString(COL_ID, columnId.value)
                        stmt.executeQuery().use { rs ->
                            buildList { while (rs.next()) add(rs.toCard()) }
                        }
                    }
            }
        }

    override suspend fun delete(id: CardId): Boolean =
        query {
            DatabaseFactory.dataSource.connection.use { conn ->
                val deleted =
                    conn.prepareStatement("DELETE FROM cards WHERE id = ?").use { stmt ->
                        stmt.setString(COL_ID, id.value)
                        stmt.executeUpdate()
                    }
                conn.commit()
                deleted > 0
            }
        }

    private fun java.sql.ResultSet.toCard() =
        Card(
            id = CardId(getString("id")),
            columnId = ColumnId(getString("column_id")),
            title = getString("title"),
            description = getString("description"),
            position = getInt("position"),
            createdAt = Instant.ofEpochMilli(getLong("created_at")),
        )
}
