package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.valueobjects.BoardId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColumnTest {
    private val boardId = BoardId.generate()

    @Test
    fun `create column with valid data`() {
        val column = Column.create(boardId = boardId, name = "Todo", position = 0)
        assertEquals("Todo", column.name)
        assertEquals(boardId, column.boardId)
        assertEquals(0, column.position)
        assertTrue(column.id.value.isNotBlank())
        assertTrue(column.cards.isEmpty())
    }

    @Test
    fun `create column at non-zero position`() {
        val column = Column.create(boardId = boardId, name = "Done", position = 2)
        assertEquals(2, column.position)
    }

    @Test
    fun `create column with blank name throws`() {
        assertThrows<IllegalArgumentException> { Column.create(boardId = boardId, name = "", position = 0) }
    }

    @Test
    fun `create column with whitespace-only name throws`() {
        assertThrows<IllegalArgumentException> { Column.create(boardId = boardId, name = "  ", position = 0) }
    }

    @Test
    fun `create column with negative position throws`() {
        assertThrows<IllegalArgumentException> { Column.create(boardId = boardId, name = "Todo", position = -1) }
    }
}
