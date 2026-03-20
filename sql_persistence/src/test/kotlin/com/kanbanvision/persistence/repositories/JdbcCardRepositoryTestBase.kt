package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.Step
import com.kanbanvision.persistence.IntegrationTestSetup
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JdbcCardRepositoryTestBase {
    protected val boardRepository = JdbcBoardRepository()
    protected val columnRepository = JdbcStepRepository()
    protected val repository = JdbcCardRepository()

    protected var existingColumnId: String? = null

    @BeforeAll
    fun initDatabase() {
        IntegrationTestSetup.ensureInitialized()
    }

    @BeforeEach
    fun cleanDatabase() =
        runBlocking {
            IntegrationTestSetup.cleanTables()
            val board =
                Board(
                    id = UUID.randomUUID().toString(),
                    name = "Test Board",
                    audit = Audit(createdAt = Instant.ofEpochMilli(System.currentTimeMillis())),
                )
            boardRepository.save(board)
            val column =
                Step(
                    id = UUID.randomUUID().toString(),
                    boardId = board.id,
                    name = "Test Step",
                    position = 0,
                    requiredAbility = AbilityName.DEVELOPER,
                )
            columnRepository.save(column)
            existingColumnId = column.id
        }

    protected fun newCard(
        title: String = "Test Card",
        position: Int = 0,
    ) = Card(
        id = UUID.randomUUID().toString(),
        columnId = existingColumnId!!,
        title = title,
        description = "Description",
        position = position,
        audit = Audit(createdAt = Instant.ofEpochMilli(System.currentTimeMillis())),
    )
}
