package com.kanbanvision.persistence.repositories

import arrow.core.Either
import arrow.core.left
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.StepRef
import com.kanbanvision.persistence.dbQuery
import com.kanbanvision.persistence.tables.CardsTable
import com.kanbanvision.usecases.repositories.CardRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.time.Instant

class JdbcCardRepository : CardRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun save(card: Card): Either<DomainError, Card> =
        dbQuery(log) {
            CardsTable.upsert {
                it[id] = card.id
                it[stepId] = card.step.id
                it[title] = card.title
                it[description] = card.description
                it[position] = card.position
                it[createdAt] = card.audit.createdAt.toEpochMilli()
            }
            card
        }

    override suspend fun findById(id: String): Either<DomainError, Card> =
        dbQuery(log) {
            CardsTable
                .selectAll()
                .where { CardsTable.id eq id }
                .singleOrNull()
                ?.let { rowToCard(it) }
        }.fold(
            ifLeft = { it.left() },
            ifRight = { card -> card?.let { Either.Right(it) } ?: DomainError.CardNotFound(id).left() },
        )

    override suspend fun findByStepId(stepId: String): Either<DomainError, List<Card>> =
        dbQuery(log) {
            CardsTable
                .selectAll()
                .where { CardsTable.stepId eq stepId }
                .orderBy(CardsTable.position)
                .map { rowToCard(it) }
        }

    override suspend fun updateCard(
        id: String,
        transform: (Card) -> Card,
    ): Either<DomainError, Card> =
        dbQuery(log) {
            val existing =
                CardsTable
                    .selectAll()
                    .where { CardsTable.id eq id }
                    .singleOrNull()
                    ?.let { rowToCard(it) }
            if (existing == null) {
                null
            } else {
                val updated = transform(existing)
                CardsTable.update({ CardsTable.id eq id }) {
                    it[stepId] = updated.step.id
                    it[title] = updated.title
                    it[description] = updated.description
                    it[position] = updated.position
                }
                updated
            }
        }.fold(
            ifLeft = { it.left() },
            ifRight = { updated -> updated?.let { Either.Right(it) } ?: DomainError.CardNotFound(id).left() },
        )

    private fun rowToCard(row: ResultRow): Card =
        Card(
            id = row[CardsTable.id],
            step = StepRef(row[CardsTable.stepId]),
            title = row[CardsTable.title],
            description = row[CardsTable.description],
            position = row[CardsTable.position],
            audit = Audit(createdAt = Instant.ofEpochMilli(row[CardsTable.createdAt])),
        )
}
