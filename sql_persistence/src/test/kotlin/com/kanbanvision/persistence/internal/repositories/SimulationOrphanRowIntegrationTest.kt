package com.kanbanvision.persistence.internal.repositories

import arrow.core.getOrElse
import com.kanbanvision.domain.model.simulation.SimulationError
import com.kanbanvision.domain.model.simulation.SimulationId
import com.kanbanvision.persistence.support.EmbeddedPostgresSupport
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * GAP-DT: uma linha em `simulations` sem par em `simulation_states`.
 *
 * O schema permite (a FK aponta de `simulation_states` para `simulations`, não o inverso), mas
 * nenhuma escrita do sistema produz: `save` é o único escritor e grava as duas tabelas na mesma
 * transação. Só chega aqui data-fix manual ou legado — antes o repositório FABRICAVA uma Simulation
 * com defaults de domínio hardcodados; agora ele só hidrata, e a linha órfã é reportada como
 * inexistente.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimulationOrphanRowIntegrationTest {
    private val simulationRepository = JdbcSimulationRepository()

    @BeforeAll
    fun setupDatabase() {
        EmbeddedPostgresSupport.ensureStarted()
    }

    @BeforeEach
    fun cleanDatabase() {
        EmbeddedPostgresSupport.refreshDataSource()
        EmbeddedPostgresSupport.resetDatabase()
    }

    private fun seedOrphanRow(
        simulationId: String,
        organizationId: String,
    ) {
        EmbeddedPostgresSupport.insertOrganization(organizationId, "Orphan Org")
        EmbeddedPostgresSupport.insertSimulationRow(
            EmbeddedPostgresSupport.SimulationSeed(
                id = simulationId,
                organizationId = organizationId,
                wipLimit = 3,
                teamSize = 4,
                seedValue = 12L,
            ),
        )
    }

    @Test
    fun `given a simulation row without serialized state when finding by id then it is reported as not found`() =
        runBlocking {
            val simulationId = "04000000-0000-0000-0000-000000000001"
            seedOrphanRow(simulationId, organizationId = "04000000-0000-0000-0000-000000000002")

            val result = simulationRepository.findById(SimulationId(simulationId))

            assertIs<SimulationError.SimulationNotFound>(result.leftOrNull())
        }

    @Test
    fun `given a simulation row without serialized state when listing then it is skipped and the page does not fail`() =
        runBlocking {
            val organizationId = "04000000-0000-0000-0000-000000000012"
            seedOrphanRow("04000000-0000-0000-0000-000000000011", organizationId)

            val page = simulationRepository.findAll(organizationId, page = 1, size = 10).getOrElse { error("findAll failed: $it") }
            val total = simulationRepository.countByOrganization(organizationId).getOrElse { error("count failed: $it") }

            assertEquals(emptyList(), page.map { it.id.value })
            assertEquals(0L, total, "a contagem tem de concordar com a página, senão a paginação mente")
        }
}
