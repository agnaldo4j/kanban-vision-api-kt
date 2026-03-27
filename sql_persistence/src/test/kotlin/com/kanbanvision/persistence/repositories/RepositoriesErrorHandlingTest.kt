package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.BoardRef
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.StepRef
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.persistence.support.EmbeddedPostgresSupport
import com.kanbanvision.persistence.support.PersistenceFixtures
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertIs

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepositoriesErrorHandlingTest {
    private val boardRepository = JdbcBoardRepository()
    private val stepRepository = JdbcStepRepository()
    private val cardRepository = JdbcCardRepository()
    private val organizationRepository = JdbcOrganizationRepository()
    private val simulationRepository = JdbcSimulationRepository()
    private val snapshotRepository = JdbcSnapshotRepository()

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
    fun `given closed datasource when board methods execute then persistence errors are returned`() =
        runBlocking {
            DatabaseFactory.dataSource.close()
            assertPersistenceError(boardRepository.save(Board(id = "06000000-0000-0000-0000-000000000001", name = "B")).leftOrNull())
            assertPersistenceError(boardRepository.findById("06000000-0000-0000-0000-000000000001").leftOrNull())
        }

    @Test
    fun `given closed datasource when step methods execute then persistence errors are returned`() =
        runBlocking {
            DatabaseFactory.dataSource.close()
            assertPersistenceError(stepSaveFailure().leftOrNull())
            assertPersistenceError(stepRepository.findById("06000000-0000-0000-0000-000000000002").leftOrNull())
            assertPersistenceError(stepRepository.findByBoardId("06000000-0000-0000-0000-000000000001").leftOrNull())
        }

    @Test
    fun `given closed datasource when card methods execute then persistence errors are returned`() =
        runBlocking {
            DatabaseFactory.dataSource.close()
            assertPersistenceError(cardSaveFailure().leftOrNull())
            assertPersistenceError(cardRepository.findById("06000000-0000-0000-0000-000000000003").leftOrNull())
            assertPersistenceError(
                cardRepository
                    .updateCard("06000000-0000-0000-0000-000000000003") { it.copy(title = "x") }
                    .leftOrNull(),
            )
            assertPersistenceError(cardRepository.findByStepId("06000000-0000-0000-0000-000000000002").leftOrNull())
        }

    @Test
    fun `given closed datasource when organization and simulation methods execute then persistence errors are returned`() =
        runBlocking {
            val simulation = PersistenceFixtures.simulation()
            DatabaseFactory.dataSource.close()
            assertPersistenceError(organizationRepository.findById("06000000-0000-0000-0000-000000000004").leftOrNull())
            assertPersistenceError(simulationRepository.save(simulation).leftOrNull())
            assertPersistenceError(simulationRepository.findById(simulation.id).leftOrNull())
        }

    @Test
    fun `given closed datasource when snapshot methods execute then persistence errors are returned`() =
        runBlocking {
            val simulation = PersistenceFixtures.simulation()
            val snapshot = PersistenceFixtures.snapshot(simulation.id)
            DatabaseFactory.dataSource.close()
            assertPersistenceError(snapshotRepository.save(snapshot).leftOrNull())
            assertPersistenceError(snapshotRepository.findByDay(simulation.id, SimulationDay(1)).leftOrNull())
            assertPersistenceError(snapshotRepository.findAllBySimulation(simulation.id).leftOrNull())
        }

    private suspend fun stepSaveFailure() =
        stepRepository.save(
            Step(
                id = "06000000-0000-0000-0000-000000000002",
                board = BoardRef("06000000-0000-0000-0000-000000000001"),
                name = "S",
                position = 0,
                requiredAbility = AbilityName.DEVELOPER,
            ),
        )

    private suspend fun cardSaveFailure() =
        cardRepository.save(
            Card(
                id = "06000000-0000-0000-0000-000000000003",
                step = StepRef("06000000-0000-0000-0000-000000000002"),
                title = "C",
                position = 0,
            ),
        )

    private fun assertPersistenceError(error: DomainError?) {
        assertIs<DomainError.PersistenceError>(error)
    }
}
