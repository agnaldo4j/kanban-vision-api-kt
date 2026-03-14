package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BoardTest {
    @Test
    fun `create board with valid name`() {
        val board = Board.create("My Board")
        assertEquals("My Board", board.name)
        assertTrue(board.id.value.isNotBlank())
        assertTrue(board.columns.isEmpty())
        assertNotNull(board.createdAt)
    }

    @Test
    fun `create board generates unique id on each call`() {
        val board1 = Board.create("Board 1")
        val board2 = Board.create("Board 2")
        assertNotEquals(board1.id, board2.id)
    }

    @Test
    fun `create board with blank name throws`() {
        assertThrows<IllegalArgumentException> { Board.create("") }
    }

    @Test
    fun `create board with whitespace-only name throws`() {
        assertThrows<IllegalArgumentException> { Board.create("   ") }
    }
}
