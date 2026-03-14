package com.kanbanvision.usecases.card.queries

import kotlin.test.Test
import kotlin.test.assertFailsWith

class GetCardQueryTest {
    @Test
    fun `validate passes with valid id`() {
        GetCardQuery(id = "some-id").validate()
    }

    @Test
    fun `validate throws with blank id`() {
        assertFailsWith<IllegalArgumentException> { GetCardQuery(id = "").validate() }
    }

    @Test
    fun `validate throws with whitespace-only id`() {
        assertFailsWith<IllegalArgumentException> { GetCardQuery(id = "   ").validate() }
    }
}
