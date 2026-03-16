package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.scenario.Scenario
import com.kanbanvision.domain.model.scenario.ScenarioConfig
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.domain.model.valueobjects.TenantId
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.persistence.IntegrationTestSetup
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcScenarioRepositoryIntegrationTest {
    private val repository = JdbcScenarioRepository()

    private val tenantId = TenantId(UUID.randomUUID().toString())
    private val config = ScenarioConfig(wipLimit = 3, teamSize = 2, seedValue = 99L)

    @BeforeAll
    fun initDatabase() {
        IntegrationTestSetup.ensureInitialized()
    }

    @BeforeEach
    fun cleanDatabase() {
        IntegrationTestSetup.cleanTables()
        insertTenant(tenantId.value)
    }

    private fun insertTenant(id: String) {
        DatabaseFactory.dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO tenants (id, name) VALUES (?, ?)").use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, "Test Org")
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    private fun newScenario() = Scenario(id = ScenarioId(UUID.randomUUID().toString()), tenantId = tenantId, config = config)

    @Test
    fun `save and findById round-trip returns same scenario`() =
        runBlocking {
            val scenario = newScenario()

            repository.save(scenario)
            val result = repository.findById(scenario.id)

            assertTrue(result.isRight())
            val found = result.getOrNull()!!
            assertEquals(scenario.id, found.id)
            assertEquals(scenario.tenantId, found.tenantId)
            assertEquals(scenario.config.wipLimit, found.config.wipLimit)
            assertEquals(scenario.config.seedValue, found.config.seedValue)
        }

    @Test
    fun `findById returns ScenarioNotFound when not persisted`() =
        runBlocking<Unit> {
            val result = repository.findById(ScenarioId(UUID.randomUUID().toString()))

            assertTrue(result.isLeft())
            assertIs<DomainError.ScenarioNotFound>(result.leftOrNull())
        }

    @Test
    fun `saveState and findState round-trip preserves full state`() =
        runBlocking {
            val scenario = newScenario()
            repository.save(scenario)
            val state = SimulationState.initial(config)

            repository.saveState(scenario.id, state)
            val result = repository.findState(scenario.id)

            assertTrue(result.isRight())
            val found = result.getOrNull()!!
            assertEquals(state.currentDay, found.currentDay)
            assertEquals(state.policySet.wipLimit, found.policySet.wipLimit)
            assertEquals(state.items.size, found.items.size)
        }

    @Test
    fun `saveState overwrites previous state`() =
        runBlocking {
            val scenario = newScenario()
            repository.save(scenario)
            val initial = SimulationState.initial(config)
            repository.saveState(scenario.id, initial)
            val updated =
                initial.copy(
                    currentDay =
                        com.kanbanvision.domain.model.scenario
                            .SimulationDay(2),
                )

            repository.saveState(scenario.id, updated)
            val result = repository.findState(scenario.id)

            assertEquals(2, result.getOrNull()!!.currentDay.value)
        }

    @Test
    fun `findState returns ScenarioNotFound when no state persisted`() =
        runBlocking<Unit> {
            val scenario = newScenario()
            repository.save(scenario)

            val result = repository.findState(scenario.id)

            assertTrue(result.isLeft())
            assertIs<DomainError.ScenarioNotFound>(result.leftOrNull())
        }

    @Test
    fun `saveState and findState preserves work items`() =
        runBlocking {
            val scenario = newScenario()
            repository.save(scenario)
            val item =
                com.kanbanvision.domain.model.workitem.WorkItem
                    .create("Task A")
            val state = SimulationState.initial(config).copy(items = listOf(item))

            repository.saveState(scenario.id, state)
            val result = repository.findState(scenario.id)

            assertTrue(result.isRight())
            val found = result.getOrNull()!!
            assertEquals(1, found.items.size)
            assertEquals("Task A", found.items.first().title)
        }
}
