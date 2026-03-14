package com.kanbanvision.usecases.board.queries

import kotlin.test.Test
import kotlin.test.assertFailsWith

class GetBoardQueryTest {
    @Test
    fun `validate passes with valid id`() {
        GetBoardQuery(id = "some-id").validate()
    }

    @Test
    fun `validate throws with blank id`() {
        assertFailsWith<IllegalArgumentException> { GetBoardQuery(id = "").validate() }
    }

    @Test
    fun `validate throws with whitespace-only id`() {
        assertFailsWith<IllegalArgumentException> { GetBoardQuery(id = "   ").validate() }
    }
}
