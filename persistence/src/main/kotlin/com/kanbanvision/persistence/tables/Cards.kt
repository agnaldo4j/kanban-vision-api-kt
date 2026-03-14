package com.kanbanvision.persistence.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Cards : Table("cards") {
    val id = varchar("id", 36)
    val columnId = varchar("column_id", 36).references(Columns.id)
    val title = varchar("title", 255)
    val description = text("description").default("")
    val position = integer("position")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
