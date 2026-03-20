package com.kanbanvision.persistence.serializers

import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.CardState
import com.kanbanvision.domain.model.PolicySet
import com.kanbanvision.domain.model.ServiceClass
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationState
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimulationStateSerializerTest {
    @Test
    fun `decode applies defaults for optional card and context fields`() {
        val state = SimulationStateSerializer.decode(rawWithMinimalCardAndContext())
        val card = state.cards.single()
        val context = assertNotNull(state.context)

        assertEquals("", card.columnId)
        assertEquals(0, card.position)
        assertEquals(0, card.analysisEffort)
        assertEquals(0, card.remainingDeployEffort)
        assertTrue(context.steps.isEmpty())
        assertTrue(context.tribes.isEmpty())
        assertTrue(context.workerAssignments.isEmpty())
    }

    @Test
    fun `decode applies defaults for optional context collections`() {
        val raw =
            """
            {
              "currentDay": 2,
              "wipLimit": 3,
              "cards": [],
              "context": {
                "organizationId": "org-1",
                "boardId": "board-1"
              }
            }
            """.trimIndent()

        val state = SimulationStateSerializer.decode(raw)
        val context = assertNotNull(state.context)

        assertEquals(2, state.currentDay.value)
        assertEquals(3, state.policySet.wipLimit)
        assertTrue(context.steps.isEmpty())
        assertTrue(context.tribes.isEmpty())
        assertTrue(context.workerAssignments.isEmpty())
    }

    @Test
    fun `decode supports legacy items field`() {
        val raw =
            """
            {
              "currentDay": 3,
              "wipLimit": 2,
              "items": [
                {
                  "id": "legacy-card",
                  "title": "Legacy",
                  "serviceClass": "STANDARD",
                  "state": "TODO",
                  "agingDays": 1
                }
              ]
            }
            """.trimIndent()

        val state = SimulationStateSerializer.decode(raw)

        assertEquals(1, state.cards.size)
        assertEquals("legacy-card", state.cards.first().id)
        assertEquals("Legacy", state.cards.first().title)
        assertEquals("", state.cards.first().columnId)
        assertEquals(0, state.cards.first().remainingDeployEffort)
    }

    @Test
    fun `encode and decode preserves card execution fields`() {
        val card = richCard()
        val state =
            SimulationState(
                currentDay = SimulationDay(3),
                policySet = PolicySet(wipLimit = 2),
                cards = listOf(card),
            )

        val raw = SimulationStateSerializer.encode(state)
        val decoded = SimulationStateSerializer.decode(raw)

        assertCardExecutionFields(decoded.cards.single())
    }

    @Test
    fun `surrogate data classes expose stable generated methods`() {
        val ability = AbilitySurrogate(id = "a1", name = "DEVELOPER", seniority = "PL")
        val worker = WorkerSurrogate(id = "w1", name = "Worker", abilities = listOf(ability))
        val squad = SquadSurrogate(id = "s1", name = "Squad", workers = listOf(worker))
        val tribe = TribeSurrogate(id = "t1", name = "Tribe", squads = listOf(squad))
        val step = StepSurrogate(id = "st1", boardId = "b1", name = "Dev", position = 1, requiredAbility = "DEVELOPER")
        val item = WorkItemSurrogate(id = "c1", title = "Card", serviceClass = "STANDARD", state = "TODO", agingDays = 0)
        val context = SimulationContextSurrogate(organizationId = "org-1", boardId = "b1", steps = listOf(step), tribes = listOf(tribe))
        val state = SimulationStateSurrogate(currentDay = 1, wipLimit = 2, cards = listOf(item), context = context)

        assertEquals("a1", ability.component1())
        assertEquals("w1", worker.component1())
        assertEquals("s1", squad.component1())
        assertEquals("t1", tribe.component1())
        assertEquals("st1", step.component1())
        assertEquals("c1", item.component1())
        assertEquals("org-1", context.component1())
        assertEquals(1, state.component1())
    }

    @Test
    fun `surrogate copy hash and string are consistent`() {
        val base = WorkItemSurrogate(id = "c1", title = "Card", serviceClass = "STANDARD", state = "TODO", agingDays = 1)
        val copied = base.copy(title = "Card 2", position = 3)

        assertNotEquals(base, copied)
        assertNotEquals(base.hashCode(), copied.hashCode())
        assertTrue(base.toString().contains("Card"))
        assertTrue(copied.toString().contains("Card 2"))
        assertEquals(3, copied.position)
        assertEquals("c1", copied.id)
    }

    @Test
    fun `decode with worker without abilities fails validation`() {
        assertThrows<IllegalArgumentException> {
            SimulationStateSerializer.decode(rawWithWorkerWithoutAbilities())
        }
    }

    private fun rawWithWorkerWithoutAbilities(): String =
        """
        {
          "currentDay": 1,
          "wipLimit": 1,
          "context": {
            "organizationId": "org-1",
            "boardId": "board-1",
            "tribes": [
              {
                "id": "tribe-1",
                "name": "Tribe",
                "squads": [
                  {
                    "id": "squad-1",
                    "name": "Squad",
                    "workers": [
                      {
                        "id": "worker-1",
                        "name": "Worker"
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()

    private fun rawWithMinimalCardAndContext(): String =
        """
        {
          "currentDay": 1,
          "wipLimit": 1,
          "cards": [
            {
              "id": "card-1",
              "title": "Card",
              "serviceClass": "STANDARD",
              "state": "TODO",
              "agingDays": 0
            }
          ],
          "context": {
            "organizationId": "org-1",
            "boardId": "board-1"
          }
        }
        """.trimIndent()

    private fun richCard() =
        Card(
            id = "card-1",
            columnId = "step-1",
            title = "Execution card",
            description = "desc",
            position = 2,
            serviceClass = ServiceClass.FIXED_DATE,
            state = CardState.IN_PROGRESS,
            agingDays = 4,
            analysisEffort = 2,
            developmentEffort = 3,
            testEffort = 5,
            deployEffort = 1,
            remainingAnalysisEffort = 1,
            remainingDevelopmentEffort = 2,
            remainingTestEffort = 4,
            remainingDeployEffort = 0,
        )

    private fun assertCardExecutionFields(decodedCard: Card) {
        assertEquals("step-1", decodedCard.columnId)
        assertEquals("desc", decodedCard.description)
        assertEquals(2, decodedCard.position)
        assertEquals(ServiceClass.FIXED_DATE, decodedCard.serviceClass)
        assertEquals(CardState.IN_PROGRESS, decodedCard.state)
        assertEquals(4, decodedCard.agingDays)
        assertEquals(2, decodedCard.analysisEffort)
        assertEquals(3, decodedCard.developmentEffort)
        assertEquals(5, decodedCard.testEffort)
        assertEquals(1, decodedCard.deployEffort)
        assertEquals(1, decodedCard.remainingAnalysisEffort)
        assertEquals(2, decodedCard.remainingDevelopmentEffort)
        assertEquals(4, decodedCard.remainingTestEffort)
        assertEquals(0, decodedCard.remainingDeployEffort)
    }
}
