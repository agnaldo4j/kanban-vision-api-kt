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
}
