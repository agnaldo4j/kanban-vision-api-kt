package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardEffortLifecycleTest {
    private val createdAt = Instant.parse("2026-03-19T12:00:00Z")
    private val updatedAt = Instant.parse("2026-03-19T12:10:00Z")

    @Test
    fun `remainingEffortFor returns values for all abilities including tester`() {
        val card =
            Card(
                title = "Feature Y",
                analysisEffort = 2,
                developmentEffort = 3,
                testEffort = 4,
                deployEffort = 5,
            )

        assertEquals(2, card.remainingEffortFor(AbilityName.PRODUCT_MANAGER))
        assertEquals(3, card.remainingEffortFor(AbilityName.DEVELOPER))
        assertEquals(4, card.remainingEffortFor(AbilityName.TESTER))
        assertEquals(5, card.remainingEffortFor(AbilityName.DEPLOYER))
    }

    @Test
    fun `card validates id title and lifecycle dates`() {
        assertThrows<IllegalArgumentException> {
            Card(id = " ", title = "Card", analysisEffort = 0, developmentEffort = 0, testEffort = 0, deployEffort = 0)
        }
        assertThrows<IllegalArgumentException> {
            Card(title = " ", analysisEffort = 0, developmentEffort = 0, testEffort = 0, deployEffort = 0)
        }
    }

    @Test
    fun `card validates updatedDate not before createdDate`() {
        assertThrows<IllegalArgumentException> {
            Card(
                title = "Card",
                analysisEffort = 0,
                developmentEffort = 0,
                testEffort = 0,
                deployEffort = 0,
                audit = Audit(createdAt = createdAt, updatedAt = createdAt.minusSeconds(1)),
            )
        }
    }

    @Test
    fun `card validates deletedDate not before createdDate`() {
        assertThrows<IllegalArgumentException> {
            Card(
                title = "Card",
                analysisEffort = 0,
                developmentEffort = 0,
                testEffort = 0,
                deployEffort = 0,
                audit = Audit(createdAt = createdAt, updatedAt = updatedAt, deletedAt = createdAt.minusSeconds(1)),
            )
        }
    }

    @Test
    fun `card validates effort values and remaining bounds`() {
        assertThrows<IllegalArgumentException> {
            Card(title = "Card", analysisEffort = -1, developmentEffort = 0, testEffort = 0, deployEffort = 0)
        }
        assertThrows<IllegalArgumentException> {
            Card(title = "Card", analysisEffort = 0, developmentEffort = -1, testEffort = 0, deployEffort = 0)
        }
        assertThrows<IllegalArgumentException> {
            Card(title = "Card", analysisEffort = 0, developmentEffort = 0, testEffort = -1, deployEffort = 0)
        }
        assertThrows<IllegalArgumentException> {
            Card(title = "Card", analysisEffort = 0, developmentEffort = 0, testEffort = 0, deployEffort = -1)
        }
    }

    @Test
    fun `card validates remaining analysis and development bounds`() {
        assertThrows<IllegalArgumentException> {
            Card(
                title = "Card",
                analysisEffort = 1,
                developmentEffort = 1,
                testEffort = 1,
                deployEffort = 1,
                remainingAnalysisEffort = 2,
            )
        }
        assertThrows<IllegalArgumentException> {
            Card(
                title = "Card",
                analysisEffort = 1,
                developmentEffort = 1,
                testEffort = 1,
                deployEffort = 1,
                remainingDevelopmentEffort = 2,
            )
        }
    }

    @Test
    fun `card validates remaining test and deploy bounds`() {
        assertThrows<IllegalArgumentException> {
            Card(
                title = "Card",
                analysisEffort = 1,
                developmentEffort = 1,
                testEffort = 1,
                deployEffort = 1,
                remainingTestEffort = 2,
            )
        }
        assertThrows<IllegalArgumentException> {
            Card(
                title = "Card",
                analysisEffort = 1,
                developmentEffort = 1,
                testEffort = 1,
                deployEffort = 1,
                remainingDeployEffort = 2,
            )
        }
    }

    @Test
    fun `consumeEffort updates timestamp and never goes below zero`() {
        val card =
            Card(
                title = "Feature Z",
                analysisEffort = 1,
                developmentEffort = 1,
                testEffort = 1,
                deployEffort = 1,
            )
        val now = card.createdDate.plusSeconds(1)

        val updated = card.consumeEffort(AbilityName.TESTER, points = 5, now = now)

        assertEquals(0, updated.remainingTestEffort)
        assertEquals(now, updated.updatedDate)
        assertTrue(updated.updatedDate != card.updatedDate)
    }
}
