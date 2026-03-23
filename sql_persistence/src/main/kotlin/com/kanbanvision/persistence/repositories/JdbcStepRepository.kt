package com.kanbanvision.persistence.repositories

import arrow.core.Either
import arrow.core.left
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.BoardRef
import com.kanbanvision.domain.model.Step
import com.kanbanvision.persistence.dbQuery
import com.kanbanvision.persistence.tables.StepsTable
import com.kanbanvision.usecases.repositories.StepRepository
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory

class JdbcStepRepository : StepRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun save(step: Step): Either<DomainError, Step> =
        dbQuery(log) {
            StepsTable.upsert {
                it[id] = step.id
                it[boardId] = step.board.id
                it[name] = step.name
                it[position] = step.position
                it[requiredAbility] = step.requiredAbility.name
            }
            step
        }

    override suspend fun findById(id: String): Either<DomainError, Step> =
        dbQuery(log) {
            StepsTable
                .selectAll()
                .where(StepsTable.id eq id)
                .singleOrNull()
                ?.let { row ->
                    Step(
                        id = row[StepsTable.id],
                        board = BoardRef(row[StepsTable.boardId]),
                        name = row[StepsTable.name],
                        position = row[StepsTable.position],
                        requiredAbility = AbilityName.valueOf(row[StepsTable.requiredAbility]),
                    )
                }
        }.fold(
            ifLeft = { it.left() },
            ifRight = { step -> step?.let { Either.Right(it) } ?: DomainError.StepNotFound(id).left() },
        )

    override suspend fun findByBoardId(boardId: String): Either<DomainError, List<Step>> =
        dbQuery(log) {
            StepsTable
                .selectAll()
                .where(StepsTable.boardId eq boardId)
                .orderBy(StepsTable.position)
                .map { row ->
                    Step(
                        id = row[StepsTable.id],
                        board = BoardRef(row[StepsTable.boardId]),
                        name = row[StepsTable.name],
                        position = row[StepsTable.position],
                        requiredAbility = AbilityName.valueOf(row[StepsTable.requiredAbility]),
                    )
                }
        }
}
