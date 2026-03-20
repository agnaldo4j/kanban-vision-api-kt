package com.kanbanvision.persistence.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Step
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.usecases.repositories.StepRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class JdbcStepRepository : StepRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        const val COL_ID = 1
        const val COL_BOARD_ID = 2
        const val COL_NAME = 3
        const val COL_POSITION = 4
        const val COL_REQUIRED_ABILITY = 5
    }

    private fun toPersistenceError(e: Throwable): DomainError {
        if (e is CancellationException) throw e
        log.error("Persistence error", e)
        return DomainError.PersistenceError(e.message ?: "Database error")
    }

    private suspend fun <T> query(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    override suspend fun save(step: Step): Either<DomainError, Step> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        conn
                            .prepareStatement(
                                """
                                INSERT INTO steps (id, board_id, name, position, required_ability)
                                VALUES (?, ?, ?, ?, ?)
                                ON CONFLICT (id) DO UPDATE SET
                                    name     = EXCLUDED.name,
                                    position = EXCLUDED.position,
                                    required_ability = EXCLUDED.required_ability
                                """.trimIndent(),
                            ).use { stmt ->
                                stmt.setString(COL_ID, step.id)
                                stmt.setString(COL_BOARD_ID, step.boardId)
                                stmt.setString(COL_NAME, step.name)
                                stmt.setInt(COL_POSITION, step.position)
                                stmt.setString(COL_REQUIRED_ABILITY, step.requiredAbility.name)
                                stmt.executeUpdate()
                            }
                        conn.commit()
                    }
                    step
                }.mapLeft(::toPersistenceError)
        }

    override suspend fun findById(id: String): Either<DomainError, Step> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        val sql = "SELECT id, board_id, name, position, required_ability FROM steps WHERE id = ?"
                        conn.prepareStatement(sql).use { stmt ->
                            stmt.setString(COL_ID, id)
                            stmt.executeQuery().use { rs ->
                                if (rs.next()) rs.toStep() else null
                            }
                        }
                    }
                }.fold(
                    ifLeft = { toPersistenceError(it).left() },
                    ifRight = { step -> step?.right() ?: DomainError.StepNotFound(id).left() },
                )
        }

    override suspend fun findByBoardId(boardId: String): Either<DomainError, List<Step>> =
        query {
            Either
                .catch {
                    DatabaseFactory.dataSource.connection.use { conn ->
                        val sql =
                            "SELECT id, board_id, name, position, required_ability FROM steps WHERE board_id = ? ORDER BY position"
                        conn.prepareStatement(sql).use { stmt ->
                            stmt.setString(1, boardId)
                            stmt.executeQuery().use { rs ->
                                buildList { while (rs.next()) add(rs.toStep()) }
                            }
                        }
                    }
                }.mapLeft(::toPersistenceError)
        }

    private fun java.sql.ResultSet.toStep() =
        Step(
            id = getString("id"),
            boardId = getString("board_id"),
            name = getString("name"),
            position = getInt("position"),
            requiredAbility = AbilityName.valueOf(getString("required_ability")),
        )
}
