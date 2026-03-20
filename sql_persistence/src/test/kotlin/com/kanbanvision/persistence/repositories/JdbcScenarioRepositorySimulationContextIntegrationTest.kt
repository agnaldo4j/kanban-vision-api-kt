package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.model.Ability
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioConfig
import com.kanbanvision.domain.model.Seniority
import com.kanbanvision.domain.model.SimulationState
import com.kanbanvision.domain.model.Squad
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.Tribe
import com.kanbanvision.domain.model.Worker
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.persistence.IntegrationTestSetup
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcScenarioRepositorySimulationContextIntegrationTest {
    private val repository = JdbcScenarioRepository()

    private val organizationId = UUID.randomUUID().toString()
    private val config = ScenarioConfig(wipLimit = 3, teamSize = 2, seedValue = 99L)

    @BeforeAll
    fun initDatabase() {
        IntegrationTestSetup.ensureInitialized()
    }

    @BeforeEach
    fun cleanDatabase() {
        IntegrationTestSetup.cleanTables()
        insertOrganization(organizationId)
    }

    @Test
    fun `saveState and findState preserves simulation context organization board and team`() =
        runBlocking {
            val scenario = newScenario()
            repository.save(scenario)
            val (state, worker, step) = stateWithContext(scenario)

            repository.saveState(scenario.id, state)
            val result = repository.findState(scenario.id)

            assertTrue(result.isRight())
            assertContext(result.getOrNull()!!, scenario, worker, step)
        }

    private fun insertOrganization(id: String) {
        DatabaseFactory.dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO organizations (id, name) VALUES (?, ?)").use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, "Test Org")
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    private fun newScenario() = Scenario(id = UUID.randomUUID().toString(), organizationId = organizationId, config = config)

    private fun stateWithContext(scenario: Scenario): Triple<SimulationState, Worker, Step> {
        val worker =
            Worker(
                name = "Worker 1",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )
        val tribe =
            Tribe(
                name = "Tribe 1",
                squads = listOf(Squad(name = "Squad 1", workers = listOf(worker))),
            )
        val step =
            Step(
                boardId = scenario.boardId,
                name = "Development",
                requiredAbility = AbilityName.DEVELOPER,
            )
        val baseState = SimulationState.initial(scenario = scenario, config = config, tribes = listOf(tribe))
        val state =
            baseState.copy(
                context = baseState.context?.copy(steps = listOf(step), workerAssignments = mapOf(worker.id to step.id)),
            )
        return Triple(state, worker, step)
    }

    private fun assertContext(
        found: SimulationState,
        scenario: Scenario,
        worker: Worker,
        step: Step,
    ) {
        val context = found.context
        assertTrue(context != null)
        assertEquals(scenario.organizationId, context.organizationId)
        assertEquals(scenario.boardId, context.boardId)
        assertEquals(1, context.tribes.size)
        assertEquals(1, context.workers.size)
        assertEquals(worker.name, context.workers.first().name)
        assertEquals(1, context.steps.size)
        assertEquals(step.name, context.steps.first().name)
        assertEquals(step.id, context.workerAssignments[worker.id])
    }
}
