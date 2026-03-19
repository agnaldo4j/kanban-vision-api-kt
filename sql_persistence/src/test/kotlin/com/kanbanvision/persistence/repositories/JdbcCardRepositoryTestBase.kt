package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.team.AbilityName
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
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
    protected val columnRepository = JdbcColumnRepository()
    protected val repository = JdbcCardRepository()

    protected var existingColumnId: ColumnId? = null

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
                    id = BoardId(UUID.randomUUID().toString()),
                    name = "Test Board",
                    createdAt = Instant.ofEpochMilli(System.currentTimeMillis()),
                )
            boardRepository.save(board)
            val column =
                Column(
                    id = ColumnId(UUID.randomUUID().toString()),
                    boardId = board.id,
                    name = "Test Column",
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
        id = CardId(UUID.randomUUID().toString()),
        columnId = existingColumnId!!,
        title = title,
        description = "Description",
        position = position,
        createdAt = Instant.ofEpochMilli(System.currentTimeMillis()),
    )
}
