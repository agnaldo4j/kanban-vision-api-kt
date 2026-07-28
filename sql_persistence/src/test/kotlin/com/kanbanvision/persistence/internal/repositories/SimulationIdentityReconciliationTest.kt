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

    @Test
    fun `a blob with a blank organization keeps the relational tenant instead of becoming unreadable`() =
        runBlocking {
            // review #384 P2: `organization.id` é a SEGUNDA identidade relacionalmente autoritativa —
            // é coluna com FK `REFERENCES organizations(id)` E a chave de tenancy dos 5 use cases.
            // Degradá-la a um sentinel viola a FK no save e faz o `ensure(org == caller)` devolver
            // Forbidden: o registro "tolerado" fica ilegível na prática, que é o oposto do objetivo.
            val simulation = PersistenceFixtures.simulation()
            EmbeddedPostgresSupport.insertOrganization(simulation.organization.id, simulation.organization.name.value)
            simulationRepository.save(simulation).getOrElse { error("save failed: $it") }
            blankOutOrganizationIdInBlob(simulation.id.value, simulation.organization.id)

            val loaded = simulationRepository.findById(simulation.id).getOrElse { error("find failed: $it") }
            // Sem a reconciliação isto falharia com violação de FK, não com asserção.
            simulationRepository.save(loaded).getOrElse { error("re-save failed: $it") }

            assertEquals(simulation.organization.id, loaded.organization.id, "the tenant comes from the relational row")
            assertEquals(1, countSimulationRows())
        }

    /** Simula o blob legado: zera só o `id` de topo, deixando o resto do agregado intacto. */
    private fun blankOutTopLevelIdInBlob(simulationId: String) =
        rewriteBlob(simulationId) { blob ->
            blob.replaceFirst(Regex(""""id"\s*:\s*"${Regex.escape(simulationId)}""""), """"id": """"")
        }

    /**
     * A coluna é JSONB: o Postgres guarda o valor PARSEADO e devolve JSON re-renderizado (espaço após
     * `:`, ordem de chaves própria), então casar o literal do `encode` falha em silêncio. Daí o regex
     * tolerante a espaçamento — e o `check` anti-vácuo, que foi o que revelou isso.
     */
    private fun rewriteBlob(
        simulationId: String,
        corrupt: (String) -> String,
    ) {
        transaction {
            val blob =
                SimulationStatesTable
                    .selectAll()
                    .where { SimulationStatesTable.simulationId eq simulationId }
                    .single()[SimulationStatesTable.stateJson]
            val corrupted = corrupt(blob)
            check(corrupted != blob) { "the blob must actually change, otherwise the test proves nothing" }
            SimulationStatesTable.update({ SimulationStatesTable.simulationId eq simulationId }) {
                it[stateJson] = corrupted
            }
        }
    }

    private fun blankOutOrganizationIdInBlob(
        simulationId: String,
        organizationId: String,
    ) = rewriteBlob(simulationId) { blob ->
        blob.replaceFirst(Regex(""""id"\s*:\s*"${Regex.escape(organizationId)}""""), """"id": """"")
    }

    private fun countSimulationRows(): Long = transaction { SimulationsTable.selectAll().count() }
}
