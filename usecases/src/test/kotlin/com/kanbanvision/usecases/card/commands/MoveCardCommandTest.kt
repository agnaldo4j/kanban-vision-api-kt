package com.kanbanvision.usecases.card.commands

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MoveCardCommandTest {
    @Test
    fun `validate passes with valid data`() {
        MoveCardCommand(cardId = "card-1", targetColumnId = "col-1", newPosition = 0).validate()
    }

    @Test
    fun `validate throws with blank card id`() {
        assertFailsWith<IllegalArgumentException> {
            MoveCardCommand(cardId = "", targetColumnId = "col-1", newPosition = 0).validate()
        }
    }

    @Test
    fun `validate throws with blank target column id`() {
        assertFailsWith<IllegalArgumentException> {
            MoveCardCommand(cardId = "card-1", targetColumnId = "", newPosition = 0).validate()
        }
    }

    @Test
    fun `validate throws with negative position`() {
        assertFailsWith<IllegalArgumentException> {
            MoveCardCommand(cardId = "card-1", targetColumnId = "col-1", newPosition = -1).validate()
        }
    }
}
