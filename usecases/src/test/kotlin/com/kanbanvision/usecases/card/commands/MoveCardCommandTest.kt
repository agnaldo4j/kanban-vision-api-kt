package com.kanbanvision.usecases.card.commands

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MoveCardCommandTest {
    @Test
    fun `validate passes with valid data`() {
        assertTrue(MoveCardCommand(cardId = "card-1", targetColumnId = "col-1", newPosition = 0).validate().isRight())
    }

    @Test
    fun `validate returns error with blank card id`() {
        val result = MoveCardCommand(cardId = "", targetColumnId = "col-1", newPosition = 0).validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate returns error with blank target column id`() {
        val result = MoveCardCommand(cardId = "card-1", targetColumnId = "", newPosition = 0).validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate returns error with negative position`() {
        val result = MoveCardCommand(cardId = "card-1", targetColumnId = "col-1", newPosition = -1).validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate accumulates all errors when all fields are invalid`() {
        val result = MoveCardCommand(cardId = "", targetColumnId = "", newPosition = -1).validate()
        assertTrue(result.isLeft())
        val error = result.leftOrNull()
        assertIs<DomainError.ValidationError>(error)
        assertTrue(error.message.contains("Card id must not be blank"))
        assertTrue(error.message.contains("Target column id must not be blank"))
        assertTrue(error.message.contains("Position must be non-negative"))
    }
}
