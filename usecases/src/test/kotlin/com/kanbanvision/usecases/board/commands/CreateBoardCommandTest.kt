package com.kanbanvision.usecases.board.commands

import kotlin.test.Test
import kotlin.test.assertFailsWith

class CreateBoardCommandTest {
    @Test
    fun `validate passes with valid name`() {
        CreateBoardCommand(name = "My Board").validate()
    }

    @Test
    fun `validate throws with blank name`() {
        assertFailsWith<IllegalArgumentException> { CreateBoardCommand(name = "").validate() }
    }

    @Test
    fun `validate throws with whitespace-only name`() {
        assertFailsWith<IllegalArgumentException> { CreateBoardCommand(name = "   ").validate() }
    }
}
