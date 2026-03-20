package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Step
import com.kanbanvision.persistence.IntegrationTestSetup
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcStepRepositoryIntegrationTest {
    private val boardRepository = JdbcBoardRepository()
    private val repository = JdbcStepRepository()

    private var existingBoardId: String? = null

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
            existingBoardId = board.id
        }

    private fun newStep(
        name: String = "Test Step",
        position: Int = 0,
        requiredAbility: AbilityName = AbilityName.DEVELOPER,
    ) = Step(
        id = UUID.randomUUID().toString(),
        boardId = existingBoardId!!,
        name = name,
        position = position,
        requiredAbility = requiredAbility,
    )

    @Test
    fun `save persists step and findById returns it`() =
        runBlocking {
            val step = newStep()

            repository.save(step)

            val result = repository.findById(step.id)
            assertTrue(result.isRight())
            val found = result.getOrNull()
            assertNotNull(found)
            assertEquals(step.id, found.id)
            assertEquals(step.boardId, found.boardId)
            assertEquals(step.name, found.name)
            assertEquals(step.position, found.position)
            assertEquals(step.requiredAbility, found.requiredAbility)
        }

    @Test
    fun `save persists deployer required ability`() =
        runBlocking {
            val step = newStep(name = "Deploy", requiredAbility = AbilityName.DEPLOYER)

            repository.save(step)

            val result = repository.findById(step.id)
            assertTrue(result.isRight())
            assertEquals(AbilityName.DEPLOYER, result.getOrNull()?.requiredAbility)
        }

    @Test
    fun `findById returns StepNotFound when step does not exist`() =
        runBlocking<Unit> {
            val result = repository.findById(UUID.randomUUID().toString())

            assertTrue(result.isLeft())
            assertIs<DomainError.StepNotFound>(result.leftOrNull())
        }

    @Test
    fun `save updates existing step on conflict`() =
        runBlocking {
            val step = newStep("Original", position = 0)
            repository.save(step)

            repository.save(step.copy(name = "Updated", position = 5))

            val result = repository.findById(step.id)
            assertEquals("Updated", result.getOrNull()?.name)
            assertEquals(5, result.getOrNull()?.position)
        }

    @Test
    fun `findByBoardId returns steps ordered by position`() =
        runBlocking {
            val step0 = newStep("Backlog", position = 0)
            val step1 = newStep("In Progress", position = 1)
            val step2 = newStep("Done", position = 2)
            repository.save(step2)
            repository.save(step0)
            repository.save(step1)

            val result = repository.findByBoardId(existingBoardId!!)

            assertTrue(result.isRight())
            val found = result.getOrNull()!!
            assertEquals(3, found.size)
            assertEquals(step0.id, found[0].id)
            assertEquals(step1.id, found[1].id)
            assertEquals(step2.id, found[2].id)
        }

    @Test
    fun `findByBoardId returns empty list when board has no steps`() =
        runBlocking {
            val result = repository.findByBoardId(existingBoardId!!)

            assertTrue(result.isRight())
            assertTrue(result.getOrNull()!!.isEmpty())
        }

    @Test
    fun `save with non-existent boardId returns PersistenceError and no step is persisted`() =
        runBlocking<Unit> {
            val orphanStep =
                Step(
                    id = UUID.randomUUID().toString(),
                    boardId = UUID.randomUUID().toString(),
                    name = "Orphan",
                    position = 0,
                    requiredAbility = AbilityName.DEVELOPER,
                )

            val result = repository.save(orphanStep)

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
            val found = repository.findById(orphanStep.id)
            assertTrue(found.isLeft())
            assertIs<DomainError.StepNotFound>(found.leftOrNull())
        }

    @Test
    fun `save with name containing special characters persists correctly`() =
        runBlocking {
            val step = newStep(name = "In Progress / Revisão & \"Done\"")

            repository.save(step)

            val result = repository.findById(step.id)
            assertTrue(result.isRight())
            assertEquals("In Progress / Revisão & \"Done\"", result.getOrNull()?.name)
        }

    @Test
    fun `findById with non-UUID string returns StepNotFound`() =
        runBlocking<Unit> {
            val result = repository.findById("not-a-valid-uuid")

            assertTrue(result.isLeft())
            assertIs<DomainError.StepNotFound>(result.leftOrNull())
        }

    @Test
    fun `save with duplicate name on same board returns PersistenceError`() =
        runBlocking<Unit> {
            repository.save(newStep("To Do"))

            val result = repository.save(newStep("To Do"))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
