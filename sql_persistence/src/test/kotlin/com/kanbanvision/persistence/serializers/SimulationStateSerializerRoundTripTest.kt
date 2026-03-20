package com.kanbanvision.persistence.serializers

import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.CardState
import com.kanbanvision.domain.model.PolicySet
import com.kanbanvision.domain.model.ServiceClass
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SimulationStateSerializerRoundTripTest {
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

    private fun richCard() =
        Card(
            id = "card-1",
            stepId = "step-1",
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
        assertEquals("step-1", decodedCard.stepId)
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
