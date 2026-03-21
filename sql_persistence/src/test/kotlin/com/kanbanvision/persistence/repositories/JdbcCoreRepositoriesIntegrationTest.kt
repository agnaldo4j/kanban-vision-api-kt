package com.kanbanvision.persistence.repositories

import arrow.core.getOrElse
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.BoardRef
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.StepRef
import com.kanbanvision.persistence.support.EmbeddedPostgresSupport
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcCoreRepositoriesIntegrationTest {
    private val boardRepository = JdbcBoardRepository()
    private val stepRepository = JdbcStepRepository()
    private val cardRepository = JdbcCardRepository()

    @BeforeAll
    fun setupDatabase() {
        EmbeddedPostgresSupport.ensureStarted()
    }

    @BeforeEach
    fun cleanDatabase() {
        EmbeddedPostgresSupport.refreshDataSource()
        EmbeddedPostgresSupport.resetDatabase()
    }

    @Test
    fun `given board entity when saving and finding by id then same persisted values are returned`() =
        runBlocking {
            val board =
                Board(
                    id = "01000000-0000-0000-0000-000000000001",
                    name = "Main Board",
                    audit = Audit(createdAt = Instant.ofEpochMilli(1234)),
                )

            val saved = boardRepository.save(board).getOrElse { error("save failed: $it") }
            val loaded = boardRepository.findById(board.id).getOrElse { error("find failed: $it") }

            assertEquals(saved.id, loaded.id)
            assertEquals(saved.name, loaded.name)
            assertEquals(saved.audit.createdAt.toEpochMilli(), loaded.audit.createdAt.toEpochMilli())
        }

    @Test
    fun `given existing board id when saving again then repository applies upsert and persists latest board name`() =
        runBlocking {
            val boardId = "01000000-0000-0000-0000-000000000010"
            boardRepository.save(Board(id = boardId, name = "Board V1")).getOrElse { error("first save failed: $it") }
            boardRepository.save(Board(id = boardId, name = "Board V2")).getOrElse { error("second save failed: $it") }

            val loaded = boardRepository.findById(boardId).getOrElse { error("find failed: $it") }
            assertEquals("Board V2", loaded.name)
        }

    @Test
    fun `given missing board id when finding by id then repository returns board not found domain error`() =
        runBlocking {
            val result = boardRepository.findById("01000000-0000-0000-0000-000000009999")
            assertIs<DomainError.BoardNotFound>(result.leftOrNull())
        }

    @Test
    fun `given step entities when saving and querying then repository returns single and ordered board lists`() =
        runBlocking {
            val boardId = "02000000-0000-0000-0000-000000000001"
            EmbeddedPostgresSupport.insertBoard(boardId, "Board A")
            val stepA =
                Step(
                    id = "02000000-0000-0000-0000-000000000101",
                    board = BoardRef(boardId),
                    name = "Analysis",
                    position = 0,
                    requiredAbility = AbilityName.PRODUCT_MANAGER,
                )
            val stepB =
                Step(
                    id = "02000000-0000-0000-0000-000000000102",
                    board = BoardRef(boardId),
                    name = "Development",
                    position = 1,
                    requiredAbility = AbilityName.DEVELOPER,
                )

            stepRepository.save(stepA).getOrElse { error("save stepA failed: $it") }
            stepRepository.save(stepB).getOrElse { error("save stepB failed: $it") }

            val byId = stepRepository.findById(stepA.id).getOrElse { error("find by id failed: $it") }
            val byBoard = stepRepository.findByBoardId(boardId).getOrElse { error("find by board failed: $it") }

            assertEquals(stepA.id, byId.id)
            assertEquals(listOf(stepA.id, stepB.id), byBoard.map { it.id })
        }

    @Test
    fun `given existing step id when saving again then repository updates mutable step fields via upsert`() =
        runBlocking {
            val boardId = "02000000-0000-0000-0000-000000000010"
            EmbeddedPostgresSupport.insertBoard(boardId, "Board B")
            val original =
                Step(
                    id = "02000000-0000-0000-0000-000000000201",
                    board = BoardRef(boardId),
                    name = "Build",
                    position = 0,
                    requiredAbility = AbilityName.DEVELOPER,
                )
            val updated = original.copy(name = "Build Updated", position = 2, requiredAbility = AbilityName.TESTER)

            stepRepository.save(original).getOrElse { error("first save failed: $it") }
            stepRepository.save(updated).getOrElse { error("second save failed: $it") }

            val loaded = stepRepository.findById(original.id).getOrElse { error("find failed: $it") }
            assertEquals("Build Updated", loaded.name)
            assertEquals(2, loaded.position)
            assertEquals(AbilityName.TESTER, loaded.requiredAbility)
        }

    @Test
    fun `given missing step id when finding by id then repository returns step not found domain error`() =
        runBlocking {
            val result = stepRepository.findById("02000000-0000-0000-0000-000000009999")
            assertIs<DomainError.StepNotFound>(result.leftOrNull())
        }

    @Test
    fun `given board without steps when listing by board id then repository returns empty step list`() =
        runBlocking {
            val boardId = "02000000-0000-0000-0000-000000009998"
            EmbeddedPostgresSupport.insertBoard(boardId, "No Steps")

            val result = stepRepository.findByBoardId(boardId).getOrElse { error("find by board failed: $it") }
            assertEquals(emptyList(), result)
        }

    @Test
    fun `given card entities when saving and updating then repository persists transformed card`() =
        runBlocking {
            val card = seedCardForPersistence()

            cardRepository.save(card).getOrElse { error("save failed: $it") }
            val updated =
                cardRepository
                    .updateCard(card.id) { it.copy(title = "Card B", description = "updated", position = 3) }
                    .getOrElse { error("update failed: $it") }
            val loaded = cardRepository.findById(card.id).getOrElse { error("find failed: $it") }
            val byStep = cardRepository.findByStepId(card.step.id).getOrElse { error("findByStep failed: $it") }

            assertEquals("Card B", updated.title)
            assertEquals("updated", loaded.description)
            assertEquals(3, loaded.position)
            assertEquals(1, byStep.size)
        }

    @Test
    fun `given existing card id when saving again then repository upserts card fields`() =
        runBlocking {
            val card = seedCardForPersistence()
            val changed = card.copy(title = "Card Changed", description = "new", position = 7)

            cardRepository.save(card).getOrElse { error("first save failed: $it") }
            cardRepository.save(changed).getOrElse { error("second save failed: $it") }

            val loaded = cardRepository.findById(card.id).getOrElse { error("find failed: $it") }
            assertEquals("Card Changed", loaded.title)
            assertEquals("new", loaded.description)
            assertEquals(7, loaded.position)
        }

    @Test
    fun `given missing card id when finding and updating then repository returns card not found domain error`() =
        runBlocking {
            val byId = cardRepository.findById("03000000-0000-0000-0000-000000009999")
            val updated = cardRepository.updateCard("03000000-0000-0000-0000-000000009999") { it.copy(title = "noop") }

            assertIs<DomainError.CardNotFound>(byId.leftOrNull())
            assertIs<DomainError.CardNotFound>(updated.leftOrNull())
        }

    @Test
    fun `given step without cards when finding by step id then repository returns empty card list`() =
        runBlocking {
            val boardId = "03000000-0000-0000-0000-000000009998"
            val stepId = "03000000-0000-0000-0000-000000009999"
            EmbeddedPostgresSupport.insertBoard(boardId)
            EmbeddedPostgresSupport.insertStep(
                EmbeddedPostgresSupport.StepSeed(
                    id = stepId,
                    boardId = boardId,
                    requiredAbility = AbilityName.DEVELOPER.name,
                ),
            )

            val result = cardRepository.findByStepId(stepId).getOrElse { error("find by step failed: $it") }
            assertEquals(emptyList(), result)
        }

    private fun seedCardForPersistence(): Card {
        val boardId = "03000000-0000-0000-0000-000000000001"
        val stepId = "03000000-0000-0000-0000-000000000011"
        EmbeddedPostgresSupport.insertBoard(boardId)
        EmbeddedPostgresSupport.insertStep(
            EmbeddedPostgresSupport.StepSeed(
                id = stepId,
                boardId = boardId,
                requiredAbility = AbilityName.DEVELOPER.name,
            ),
        )
        return Card(
            id = "03000000-0000-0000-0000-000000000101",
            step = StepRef(stepId),
            title = "Card A",
            description = "desc",
            position = 0,
            audit = Audit(createdAt = Instant.ofEpochMilli(3456)),
        )
    }
}
