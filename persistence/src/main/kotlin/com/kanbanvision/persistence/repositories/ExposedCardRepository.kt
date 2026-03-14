package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.domain.port.CardRepository
import com.kanbanvision.persistence.tables.Cards
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

class ExposedCardRepository : CardRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    override suspend fun save(card: Card): Card = dbQuery {
        Cards.upsert {
            it[id] = card.id.value
            it[columnId] = card.columnId.value
            it[title] = card.title
            it[description] = card.description
            it[position] = card.position
            it[createdAt] = card.createdAt.toKotlinInstant()
        }
        card
    }

    override suspend fun findById(id: CardId): Card? = dbQuery {
        Cards.selectAll()
            .where { Cards.id eq id.value }
            .singleOrNull()
            ?.toCard()
    }

    override suspend fun findByColumnId(columnId: ColumnId): List<Card> = dbQuery {
        Cards.selectAll()
            .where { Cards.columnId eq columnId.value }
            .orderBy(Cards.position)
            .map { it.toCard() }
    }

    override suspend fun delete(id: CardId): Boolean = dbQuery {
        Cards.deleteWhere { Cards.id eq id.value } > 0
    }

    private fun ResultRow.toCard(): Card = Card(
        id = CardId(this[Cards.id]),
        columnId = ColumnId(this[Cards.columnId]),
        title = this[Cards.title],
        description = this[Cards.description],
        position = this[Cards.position],
        createdAt = Instant.ofEpochSecond(this[Cards.createdAt].epochSeconds),
    )
}
