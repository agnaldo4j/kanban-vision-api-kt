package com.kanbanvision.persistence.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Boards : Table("boards") {
    val id = varchar("id", 36)
    val name = varchar("name", 255)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
