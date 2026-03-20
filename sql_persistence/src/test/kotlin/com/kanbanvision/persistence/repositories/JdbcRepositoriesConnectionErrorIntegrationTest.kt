package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.Step
import com.kanbanvision.persistence.IntegrationTestSetup
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.UUID
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcRepositoriesConnectionErrorIntegrationTest {
    private val boardRepository = JdbcBoardRepository()
    private val columnRepository = JdbcStepRepository()
    private val cardRepository = JdbcCardRepository()

    @BeforeAll
    fun setup() {
        IntegrationTestSetup.ensureInitialized()
        IntegrationTestSetup.closeDataSource()
    }

    @AfterAll
    fun teardown() {
        IntegrationTestSetup.reinitDataSource()
    }

    @Test
    fun `board save returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val board =
                Board(
                    id = UUID.randomUUID().toString(),
                    name = "Board",
                    audit = Audit(createdAt = Instant.now()),
                )
            val result = boardRepository.save(board)
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `board findById returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val result = boardRepository.findById(UUID.randomUUID().toString())
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `step save returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val step =
                Step(
                    id = UUID.randomUUID().toString(),
                    boardId = UUID.randomUUID().toString(),
                    name = "Step",
                    position = 0,
                    requiredAbility = AbilityName.DEVELOPER,
                )
            val result = columnRepository.save(step)
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `step findById returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val result = columnRepository.findById(UUID.randomUUID().toString())
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `step findByBoardId returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val result = columnRepository.findByBoardId(UUID.randomUUID().toString())
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `card save returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val card =
                Card(
                    id = UUID.randomUUID().toString(),
                    stepId = UUID.randomUUID().toString(),
                    title = "Card",
                    description = "",
                    position = 0,
                    audit = Audit(createdAt = Instant.now()),
                )
            val result = cardRepository.save(card)
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `card findById returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val result = cardRepository.findById(UUID.randomUUID().toString())
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `card findByStepId returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val result = cardRepository.findByStepId(UUID.randomUUID().toString())
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `card updateCard returns PersistenceError when datasource is closed`() =
        runBlocking<Unit> {
            val result = cardRepository.updateCard(UUID.randomUUID().toString()) { it }
            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
