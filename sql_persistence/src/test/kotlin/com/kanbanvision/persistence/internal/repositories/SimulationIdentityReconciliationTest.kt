package com.kanbanvision.persistence.internal.repositories

import arrow.core.getOrElse
import com.kanbanvision.persistence.internal.tables.SimulationStatesTable
import com.kanbanvision.persistence.internal.tables.SimulationsTable
import com.kanbanvision.persistence.support.EmbeddedPostgresSupport
import com.kanbanvision.persistence.support.PersistenceFixtures
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O decode tolerante do GAP-DV degrada um id em branco para um sentinel, o que mantém o agregado
 * carregável. Mas a identidade de topo é diferente dos demais campos: `save` faz upsert **por**
 * `simulation.id`, então um sentinel gravaria uma linha NOVA e deixaria a original órfã — corrupção
 * silenciosa, pior que o 500 que a tolerância evita.
 *
 * A linha relacional é a fonte autoritativa, e `rowToSimulation` reconcilia o id do blob com o dela.
 * (review #383 P1)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimulationIdentityReconciliationTest {
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

    @Test
    fun `a blob with a blank identifier keeps the relational identity instead of forking a new row`() =
        runBlocking {
            val simulation = PersistenceFixtures.simulation()
            EmbeddedPostgresSupport.insertOrganization(simulation.organization.id, simulation.organization.name.value)
            simulationRepository.save(simulation).getOrElse { error("save failed: $it") }
            blankOutTopLevelIdInBlob(simulation.id.value)

            val loaded = simulationRepository.findById(simulation.id).getOrElse { error("find failed: $it") }
            simulationRepository.save(loaded).getOrElse { error("re-save failed: $it") }

            assertEquals(simulation.id, loaded.id, "the relational row, not the blob, owns the identity")
            assertEquals(1, countSimulationRows(), "re-saving must not fork a second row under the sentinel id")
        }

    /** Simula o blob legado: zera só o `id` de topo, deixando o resto do agregado intacto. */
    private fun blankOutTopLevelIdInBlob(simulationId: String) {
        transaction {
            val blob =
                SimulationStatesTable
                    .selectAll()
                    .where { SimulationStatesTable.simulationId eq simulationId }
                    .single()[SimulationStatesTable.stateJson]
            // A coluna é JSONB: o Postgres devolve o JSON NORMALIZADO (espaço após `:`, ordem de chaves
            // própria), então casar o literal `"id":"…"` do encode falha. O regex tolera o espaçamento.
            val blanked = blob.replaceFirst(Regex(""""id"\s*:\s*"${Regex.escape(simulationId)}""""), """"id": """"")
            check(blanked != blob) { "the blob must actually change, otherwise the test proves nothing" }
            SimulationStatesTable.update({ SimulationStatesTable.simulationId eq simulationId }) {
                it[stateJson] = blanked
            }
        }
    }

    private fun countSimulationRows(): Long = transaction { SimulationsTable.selectAll().count() }
}
