package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class CardEffortTest {
    @Test
    fun `card stores effort required for each ability at creation`() {
        val card =
            Card(
                title = "Feature A",
                analysisEffort = 3,
                developmentEffort = 8,
                testEffort = 5,
                deployEffort = 2,
            )

        assertEquals(3, card.analysisEffort)
        assertEquals(8, card.developmentEffort)
        assertEquals(5, card.testEffort)
        assertEquals(2, card.deployEffort)
        assertEquals(3, card.remainingAnalysisEffort)
        assertEquals(8, card.remainingDevelopmentEffort)
        assertEquals(5, card.remainingTestEffort)
        assertEquals(2, card.remainingDeployEffort)
    }

    @Test
    fun `card effort can be partially consumed`() {
        val card =
            Card(
                title = "Feature B",
                analysisEffort = 4,
                developmentEffort = 6,
                testEffort = 2,
                deployEffort = 1,
            )

        val partiallyDone = card.consumeEffort(AbilityName.DEVELOPER, points = 3)

        assertEquals(3, partiallyDone.remainingDevelopmentEffort)
        assertEquals(4, partiallyDone.remainingAnalysisEffort)
        assertEquals(2, partiallyDone.remainingTestEffort)
        assertEquals(1, partiallyDone.remainingDeployEffort)
    }

    @Test
    fun `card consume maps to correct ability effort bucket`() {
        val card =
            Card(
                title = "Feature C",
                analysisEffort = 2,
                developmentEffort = 2,
                testEffort = 2,
                deployEffort = 2,
            )

        val afterAnalysis = card.consumeEffort(AbilityName.PRODUCT_MANAGER, points = 1)
        val afterDev = afterAnalysis.consumeEffort(AbilityName.DEVELOPER, points = 1)
        val afterTest = afterDev.consumeEffort(AbilityName.TESTER, points = 1)
        val afterDeploy = afterTest.consumeEffort(AbilityName.DEPLOYER, points = 1)

        assertEquals(1, afterDeploy.remainingAnalysisEffort)
        assertEquals(1, afterDeploy.remainingDevelopmentEffort)
        assertEquals(1, afterDeploy.remainingTestEffort)
        assertEquals(1, afterDeploy.remainingDeployEffort)
    }

    @Test
    fun `card consume with negative points throws`() {
        val card =
            Card(
                title = "Feature D",
                analysisEffort = 1,
                developmentEffort = 1,
                testEffort = 1,
                deployEffort = 1,
            )

        assertThrows<IllegalArgumentException> {
            card.consumeEffort(AbilityName.DEVELOPER, points = -1)
        }
    }
}
