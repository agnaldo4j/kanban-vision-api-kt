package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.Card
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JdbcCardRepositoryIntegrationTest : JdbcCardRepositoryTestBase() {
    @Test
    fun `save persists card and findById returns it`() =
        runBlocking {
            val card = newCard()

            repository.save(card)

            val result = repository.findById(card.id)
            assertTrue(result.isRight())
            val found = result.getOrNull()
            assertNotNull(found)
            assertEquals(card.id, found.id)
            assertEquals(card.stepId, found.stepId)
            assertEquals(card.title, found.title)
            assertEquals(card.description, found.description)
            assertEquals(card.position, found.position)
            assertEquals(card.createdAt, found.createdAt)
        }

    @Test
    fun `findById returns CardNotFound when card does not exist`() =
        runBlocking<Unit> {
            val result = repository.findById(UUID.randomUUID().toString())

            assertTrue(result.isLeft())
            assertIs<DomainError.CardNotFound>(result.leftOrNull())
        }

    @Test
    fun `save updates existing card on conflict`() =
        runBlocking {
            val card = newCard("Original Title")
            repository.save(card)

            repository.save(card.copy(title = "Updated Title", position = 99))

            val result = repository.findById(card.id)
            assertEquals("Updated Title", result.getOrNull()?.title)
            assertEquals(99, result.getOrNull()?.position)
        }

    @Test
    fun `findByStepId returns cards ordered by position`() =
        runBlocking {
            val card0 = newCard("First", position = 0)
            val card1 = newCard("Second", position = 1)
            val card2 = newCard("Third", position = 2)
            repository.save(card2)
            repository.save(card0)
            repository.save(card1)

            val result = repository.findByStepId(existingStepId!!)

            assertTrue(result.isRight())
            val found = result.getOrNull()!!
            assertEquals(3, found.size)
            assertEquals(card0.id, found[0].id)
            assertEquals(card1.id, found[1].id)
            assertEquals(card2.id, found[2].id)
        }

    @Test
    fun `findByStepId returns empty list when step has no cards`() =
        runBlocking {
            val result = repository.findByStepId(existingStepId!!)

            assertTrue(result.isRight())
            assertTrue(result.getOrNull()!!.isEmpty())
        }

    @Test
    fun `save with non-existent stepId returns PersistenceError and no card is persisted`() =
        runBlocking<Unit> {
            val orphanCard =
                Card(
                    id = UUID.randomUUID().toString(),
                    stepId = UUID.randomUUID().toString(),
                    title = "Orphan",
                    position = 0,
                    audit = Audit(createdAt = Instant.ofEpochMilli(System.currentTimeMillis())),
                )

            val result = repository.save(orphanCard)

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
            val found = repository.findById(orphanCard.id)
            assertTrue(found.isLeft())
            assertIs<DomainError.CardNotFound>(found.leftOrNull())
        }

    @Test
    fun `updateCard applies transform and persists result atomically`() =
        runBlocking {
            val card = newCard("Original", position = 0)
            repository.save(card)

            val result = repository.updateCard(card.id) { it.moveTo(existingStepId!!, newPosition = 5) }

            assertTrue(result.isRight())
            val updated = result.getOrNull()
            assertNotNull(updated)
            assertEquals(card.id, updated.id)
            assertEquals(existingStepId, updated.stepId)
            assertEquals(5, updated.position)
            val found = repository.findById(card.id)
            assertEquals(5, found.getOrNull()?.position)
        }

    @Test
    fun `updateCard returns CardNotFound when card does not exist`() =
        runBlocking<Unit> {
            val result = repository.updateCard(UUID.randomUUID().toString()) { it }

            assertTrue(result.isLeft())
            assertIs<DomainError.CardNotFound>(result.leftOrNull())
        }

    @Test
    fun `updateCard returns PersistenceError and does not modify card when update violates constraint`() =
        runBlocking<Unit> {
            val card = newCard("Atomic Test", position = 0)
            repository.save(card)
            val nonExistentStepId = UUID.randomUUID().toString()

            val result = repository.updateCard(card.id) { it.copy(stepId = nonExistentStepId) }

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
            val found = repository.findById(card.id)
            assertTrue(found.isRight())
            assertEquals(existingStepId, found.getOrNull()?.stepId)
        }
}
