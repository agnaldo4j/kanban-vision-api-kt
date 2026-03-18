package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
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

    @Test
    fun `addColumn returns column with correct boardId and auto position`() {
        val board = Board.create("My Board")
        val column = board.addColumn("To Do")
        assertEquals(board.id, column.boardId)
        assertEquals("To Do", column.name)
        assertEquals(0, column.position)
    }

    @Test
    fun `addColumn position equals existing columns count`() {
        val boardId = BoardId.generate()
        val existing = Column.create(boardId = boardId, name = "To Do", position = 0)
        val board = Board(id = boardId, name = "My Board", columns = listOf(existing))
        val column = board.addColumn("In Progress")
        assertEquals(1, column.position)
    }

    @Test
    fun `addColumn with blank name throws`() {
        val board = Board.create("My Board")
        assertThrows<IllegalArgumentException> { board.addColumn("") }
    }

    @Test
    fun `addColumn with duplicate name throws`() {
        val boardId = BoardId.generate()
        val existing = Column.create(boardId = boardId, name = "To Do", position = 0)
        val board = Board(id = boardId, name = "My Board", columns = listOf(existing))
        assertThrows<IllegalArgumentException> { board.addColumn("To Do") }
    }

    @Test
    fun `addCard returns card with correct columnId and auto position`() {
        val boardId = BoardId.generate()
        val columnId = ColumnId.generate()
        val column = Column(id = columnId, boardId = boardId, name = "To Do", position = 0)
        val board = Board(id = boardId, name = "My Board", columns = listOf(column))
        val card = board.addCard(column, "Fix bug")
        assertEquals(columnId, card.columnId)
        assertEquals("Fix bug", card.title)
        assertEquals(0, card.position)
    }

    @Test
    fun `addCard position equals existing cards count`() {
        val boardId = BoardId.generate()
        val columnId = ColumnId.generate()
        val existingCard = Card.create(columnId = columnId, title = "Existing", position = 0)
        val column = Column(id = columnId, boardId = boardId, name = "To Do", position = 0, cards = listOf(existingCard))
        val board = Board(id = boardId, name = "My Board", columns = listOf(column))
        val card = board.addCard(column, "New Card")
        assertEquals(1, card.position)
    }

    @Test
    fun `addCard throws when column does not belong to board`() {
        val boardId = BoardId.generate()
        val otherBoardId = BoardId.generate()
        val column = Column(id = ColumnId.generate(), boardId = otherBoardId, name = "To Do", position = 0)
        val board = Board(id = boardId, name = "My Board")
        assertThrows<IllegalArgumentException> { board.addCard(column, "Task") }
    }
}
