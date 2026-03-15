package com.kanbanvision.persistence.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
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

        const val PARAM_COLUMN_ID = 1
        const val PARAM_TITLE = 2
        const val PARAM_DESCRIPTION = 3
        const val PARAM_POSITION = 4
        const val PARAM_WHERE_ID = 5
    }

    private suspend fun <T> query(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    override suspend fun save(card: Card): Either<DomainError, Card> =
        query {
            Either
                .catch {
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
                }.mapLeft { e -> DomainError.PersistenceError(e.message ?: "Database error") }
        }

    override suspend fun findById(id: CardId): Either<DomainError, Card> =
        query {
            Either
                .catch {
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
                }.fold(
                    ifLeft = { e -> DomainError.PersistenceError(e.message ?: "Database error").left() },
                    ifRight = { card -> card?.right() ?: DomainError.CardNotFound(id.value).left() },
                )
        }

    override suspend fun updateCard(
        id: CardId,
        transform: (Card) -> Card,
    ): Either<DomainError, Card> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        val card = fetchForUpdate(conn, id) ?: return@use null
                        applyAndPersist(conn, card, transform)
                    }
                }.fold(
                    ifLeft = { e -> DomainError.PersistenceError(e.message ?: "Database error").left() },
                    ifRight = { updated -> updated?.right() ?: DomainError.CardNotFound(id.value).left() },
                )
        }

    private fun fetchForUpdate(
        conn: java.sql.Connection,
        id: CardId,
    ): Card? =
        conn
            .prepareStatement(
                "SELECT id, column_id, title, description, position, created_at" +
                    " FROM cards WHERE id = ? FOR UPDATE",
            ).use { stmt ->
                stmt.setString(COL_ID, id.value)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.toCard() else null }
            }

    private fun applyAndPersist(
        conn: java.sql.Connection,
        card: Card,
        transform: (Card) -> Card,
    ): Card {
        val updated = transform(card)
        val rowsUpdated =
            conn
                .prepareStatement(
                    """
                    UPDATE cards
                    SET column_id = ?, title = ?, description = ?, position = ?
                    WHERE id = ?
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(PARAM_COLUMN_ID, updated.columnId.value)
                    stmt.setString(PARAM_TITLE, updated.title)
                    stmt.setString(PARAM_DESCRIPTION, updated.description)
                    stmt.setInt(PARAM_POSITION, updated.position)
                    stmt.setString(PARAM_WHERE_ID, card.id.value)
                    stmt.executeUpdate()
                }
        check(rowsUpdated > 0) { "Card ${card.id.value} was not updated" }
        conn.commit()
        return updated
    }

    override suspend fun findByColumnId(columnId: ColumnId): Either<DomainError, List<Card>> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn
                            .prepareStatement(
                                "SELECT id, column_id, title, description, position, created_at" +
                                    " FROM cards WHERE column_id = ? ORDER BY position",
                            ).use { stmt ->
                                stmt.setString(COL_ID, columnId.value)
                                stmt.executeQuery().use { rs ->
                                    buildList { while (rs.next()) add(rs.toCard()) }
                                }
                            }
                    }
                }.mapLeft { e -> DomainError.PersistenceError(e.message ?: "Database error") }
        }

    override suspend fun delete(id: CardId): Either<DomainError, Boolean> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        val deleted =
                            conn.prepareStatement("DELETE FROM cards WHERE id = ?").use { stmt ->
                                stmt.setString(COL_ID, id.value)
                                stmt.executeUpdate()
                            }
                        conn.commit()
                        deleted > 0
                    }
                }.mapLeft { e -> DomainError.PersistenceError(e.message ?: "Database error") }
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
