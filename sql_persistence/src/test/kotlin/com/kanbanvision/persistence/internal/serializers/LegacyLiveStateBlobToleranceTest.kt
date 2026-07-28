package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.simulation.SimulationStatus
import com.kanbanvision.persistence.support.PersistenceFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GAP-DV: the sibling of [LegacyBlobToleranceTest], on the **live state** axis.
 *
 * `LegacyBlobToleranceTest` pins tolerance for decisions and movements (GAP-DH/GAP-DS). The board
 * itself — Scenario, Board, Step, Card, plus the Organization branch — was still decoded with raw
 * constructors (`NonBlankName`, `Enum.valueOf`), so one legacy value threw and took the whole
 * aggregate down (`PersistenceError` → 500 on `findById` **and** on an entire `findAll` page, which
 * maps its rows inside the same block).
 *
 * Each test corrupts the encoded fixture and asserts the corruption actually landed (anti-vacuum),
 * so a `replace` that silently misses cannot make the test pass on both sides of the fix.
 */
class LegacyLiveStateBlobToleranceTest {
    @Test
    fun `a card with an unreadable state decodes to the quarantine sentinel instead of crashing the load`() {
        val encoded = PersistenceFixtures.simulation().encoded().replace(""""state":"TODO"""", """"state":"ARCHIVED"""")
        assertTrue(encoded.contains("ARCHIVED"), "the corrupted blob must actually differ")

        val decoded = SimulationSerializer.decode(encoded)

        assertEquals(CardState.BLOCKED, decoded.firstCard().state)
        assertTrue(
            decoded.scenario.board.steps
                .isNotEmpty(),
            "the rest of the aggregate must survive",
        )
    }

    @Test
    fun `a step whose required ability was written by a newer release still loads with its workers intact`() {
        // Cenário real: um release novo cria a ability, o rollback deixa pods antigos lendo o que
        // eles mesmos gravaram. O valor ilegível aparece nos DOIS lados — no requiredAbility do step
        // e nas abilities dos workers —, e o fallback consistente preserva
        // `Step.init`'s workers.all { hasAbility(requiredAbility) } sem descartar ninguém.
        val encoded = PersistenceFixtures.simulation().encoded().replace("DEVELOPER", "ARCHITECT")
        assertTrue(encoded.contains("ARCHITECT"), "the corrupted blob must actually differ")

        val decoded = SimulationSerializer.decode(encoded)

        val step =
            decoded.scenario.board.steps
                .first()
        assertEquals(AbilityName.DEVELOPER, step.requiredAbility)
        assertEquals(1, step.workers.size, "no worker may be dropped — discarding is permanent on the next save")
        assertTrue(step.workers.all { it.hasAbility(step.requiredAbility) })
    }

    @Test
    fun `blank names and titles across the live state decode to sentinels instead of crashing the load`() {
        val encoded =
            PersistenceFixtures
                .simulation()
                .encoded()
                .replace(""""name":"Main Board"""", """"name":""""")
                .replace(""""title":"Card 1"""", """"title":""""")
        assertTrue(encoded.contains(""""name":""""), "the corrupted blob must actually differ")

        val decoded = SimulationSerializer.decode(encoded)

        assertEquals("(unnamed)", decoded.scenario.board.name.value)
        assertEquals("(untitled)", decoded.firstCard().title.value)
    }

    @Test
    fun `a card with efforts out of range is clamped instead of crashing the load`() {
        // Card.init exige 0 <= remaining <= effort; um blob com remaining > effort lançaria.
        val encoded =
            PersistenceFixtures
                .simulation()
                .encoded()
                .replace(""""remainingDevelopmentEffort":2""", """"remainingDevelopmentEffort":99""")
                .replace(""""agingDays":0""", """"agingDays":-5""")
        assertTrue(encoded.contains("99"), "the corrupted blob must actually differ")

        val card = SimulationSerializer.decode(encoded).firstCard()

        assertEquals(card.developmentEffort, card.remainingDevelopmentEffort)
        assertEquals(0, card.agingDays)
    }

    @Test
    fun `an unreadable simulation status and seniority decode to fallbacks instead of crashing the load`() {
        val encoded =
            PersistenceFixtures
                .simulation()
                .encoded()
                .replace(""""status":"RUNNING"""", """"status":"ARCHIVED"""")
                .replace(""""seniority":"PL"""", """"seniority":"STAFF"""")
        assertTrue(encoded.contains("STAFF"), "the corrupted blob must actually differ")

        val decoded = SimulationSerializer.decode(encoded)

        assertEquals(SimulationStatus.DRAFT, decoded.status)
        assertTrue(decoded.organization.tribes.isNotEmpty(), "the organization branch must survive")
    }

    @Test
    fun `a policy set with a non positive wip limit is coerced instead of crashing the load`() {
        val encoded = PersistenceFixtures.simulation().encoded().replace(""""wipLimit":2""", """"wipLimit":0""")
        assertTrue(encoded.contains(""""wipLimit":0"""), "the corrupted blob must actually differ")

        val decoded = SimulationSerializer.decode(encoded)

        assertEquals(1, decoded.scenario.rules.wipLimit)
    }

    @Test
    fun `a worker with no abilities is completed with a sentinel instead of crashing the load`() {
        // Worker.init exige abilities.isNotEmpty(). Surrogate direto (mesmo idioma do
        // LegacyBlobToleranceTest) porque um array vazio não sai de um `replace` legível.
        val decoded = WorkerSurrogate(id = "w-1", name = "worker", abilities = emptyList()).toDomain()

        assertEquals(setOf(AbilityName.DEVELOPER), decoded.abilities.map { it.name }.toSet())
    }

    @Test
    fun `a legacy tester worker without the deployer ability is completed instead of crashing the load`() {
        // Worker.init exige `!hasTester || hasDeployer` — um blob gravado antes desse guard lançaria.
        val decoded =
            WorkerSurrogate(
                id = "w-1",
                name = "worker",
                abilities = listOf(AbilitySurrogate(id = "a-1", name = "TESTER", seniority = "SR")),
            ).toDomain()

        assertTrue(decoded.hasAbility(AbilityName.TESTER), "the original ability must be preserved, not replaced")
        assertTrue(decoded.hasAbility(AbilityName.DEPLOYER), "the missing companion ability must be added")
    }

    @Test
    fun `a worker keeping both tester and deployer is left untouched`() {
        val abilities =
            listOf(
                AbilitySurrogate(id = "a-1", name = "TESTER", seniority = "SR"),
                AbilitySurrogate(id = "a-2", name = "DEPLOYER", seniority = "SR"),
            )

        val decoded = WorkerSurrogate(id = "w-1", name = "worker", abilities = abilities).toDomain()

        assertEquals(2, decoded.abilities.size, "no ability may be added when the invariant already holds")
    }

    @Test
    fun `a worker with no abilities is repaired with its own step ability instead of crashing the load`() {
        // review #383 P1: completar o worker vazio com o fallback GLOBAL deixaria `Step.init` lançar
        // quando o step exige outra ability — o agregado seguiria não-carregável. A reparação usa a
        // ability DO STEP que contém o worker. Aqui o step passa a exigir TESTER, o que encadeia a
        // regra do DEPLOYER (`Worker.init`: !hasTester || hasDeployer).
        val encoded =
            PersistenceFixtures
                .simulation()
                .encoded()
                .replace(""""requiredAbility":"DEVELOPER"""", """"requiredAbility":"TESTER"""")
                .replace(Regex(""""abilities":\[[^]]*]"""), """"abilities":[]""")
        assertTrue(encoded.contains(""""abilities":[]"""), "the corrupted blob must actually differ")

        val step =
            SimulationSerializer
                .decode(encoded)
                .scenario.board.steps
                .first()

        assertEquals(AbilityName.TESTER, step.requiredAbility)
        assertTrue(step.workers.all { it.hasAbility(AbilityName.TESTER) }, "Step.init requires every worker to have it")
        assertTrue(step.workers.all { it.hasAbility(AbilityName.DEPLOYER) }, "a TESTER worker must also be a DEPLOYER")
    }

    @Test
    fun `blank identifiers decode to a sentinel instead of crashing the load`() {
        val encoded = PersistenceFixtures.simulation().encoded().replace(""""id":"60000000-0000-0000-0000-000000000001"""", """"id":""""")
        assertTrue(encoded.contains(""""id":""""), "the corrupted blob must actually differ")

        val decoded = SimulationSerializer.decode(encoded)

        assertEquals("(unknown)", decoded.scenario.board.id.value)
    }

    private fun com.kanbanvision.domain.model.simulation.Simulation.encoded() = SimulationSerializer.encode(this)

    private fun com.kanbanvision.domain.model.simulation.Simulation.firstCard() =
        scenario.board.steps
            .flatMap { it.cards }
            .first()
}
