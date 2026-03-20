package com.kanbanvision.persistence.serializers

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimulationStateSerializerDecodeTest {
    @Test
    fun `decode applies defaults for optional card and context fields`() {
        val state = SimulationStateSerializer.decode(rawWithMinimalCardAndContext())
        val card = state.cards.single()
        val context = assertNotNull(state.context)

        assertEquals("", card.stepId)
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
        assertEquals("", state.cards.first().stepId)
        assertEquals(0, state.cards.first().remainingDeployEffort)
    }

    @Test
    fun `decode supports legacy columnId field`() {
        val raw =
            """
            {
              "currentDay": 2,
              "wipLimit": 3,
              "cards": [
                {
                  "id": "legacy-card",
                  "columnId": "legacy-step-1",
                  "title": "Legacy",
                  "serviceClass": "STANDARD",
                  "state": "TODO",
                  "agingDays": 0
                }
              ]
            }
            """.trimIndent()

        val state = SimulationStateSerializer.decode(raw)
        val card = state.cards.single()

        assertEquals("legacy-card", card.id)
        assertEquals("legacy-step-1", card.stepId)
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
}
