package com.kanbanvision.persistence.serializers

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimulationStateSerializerTest {
    @Test
    fun `surrogate defaults are available for backward-compatible decoding`() {
        val worker = WorkerSurrogate(id = "w1", name = "Worker")
        val squad = SquadSurrogate(id = "s1", name = "Squad")
        val tribe = TribeSurrogate(id = "t1", name = "Tribe")
        val context = SimulationContextSurrogate(organizationId = "org-1", boardId = "board-1")
        val state = SimulationStateSurrogate(currentDay = 1, wipLimit = 1)

        assertTrue(worker.abilities.isEmpty())
        assertTrue(squad.workers.isEmpty())
        assertTrue(tribe.squads.isEmpty())
        assertTrue(context.steps.isEmpty())
        assertTrue(context.tribes.isEmpty())
        assertTrue(context.workerAssignments.isEmpty())
        assertTrue(state.cards.isEmpty())
        assertTrue(state.items.isEmpty())
        assertEquals(1, state.currentDay)
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
}
