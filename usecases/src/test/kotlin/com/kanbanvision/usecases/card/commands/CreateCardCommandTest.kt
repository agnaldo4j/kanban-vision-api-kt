package com.kanbanvision.usecases.card.commands

import kotlin.test.Test
import kotlin.test.assertFailsWith

class CreateCardCommandTest {
    @Test
    fun `validate passes with valid data`() {
        CreateCardCommand(columnId = "col-1", title = "Task").validate()
    }

    @Test
    fun `validate throws with blank column id`() {
        assertFailsWith<IllegalArgumentException> { CreateCardCommand(columnId = "", title = "Task").validate() }
    }

    @Test
    fun `validate throws with blank title`() {
        assertFailsWith<IllegalArgumentException> { CreateCardCommand(columnId = "col-1", title = "").validate() }
    }

    @Test
    fun `validate throws with whitespace-only title`() {
        assertFailsWith<IllegalArgumentException> { CreateCardCommand(columnId = "col-1", title = "  ").validate() }
    }
}
