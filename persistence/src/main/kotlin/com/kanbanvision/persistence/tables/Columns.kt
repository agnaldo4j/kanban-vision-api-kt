package com.kanbanvision.persistence.tables

import org.jetbrains.exposed.sql.Table

object Columns : Table("columns") {
    val id = varchar("id", 36)
    val boardId = varchar("board_id", 36).references(Boards.id)
    val name = varchar("name", 255)
    val position = integer("position")
    override val primaryKey = PrimaryKey(id)
}
