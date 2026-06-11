package com.kanbanvision.persistence.tables

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.postgresql.util.PGobject

// Custom Exposed column type for PostgreSQL JSONB columns.
// Uses PGobject to pass the value with the correct type hint so PostgreSQL
// accepts the insert without requiring an explicit ::jsonb cast in the query.
internal class JsonbColumnType : ColumnType<String>() {
    override fun sqlType(): String = "JSONB"

    override fun valueFromDB(value: Any): String =
        when (value) {
            is PGobject -> value.value ?: error("Unexpected NULL for JSONB column value")
            is String -> value
            else -> error("Unexpected JDBC type for JSONB column: ${value::class.qualifiedName}")
        }

    override fun notNullValueToDB(value: String): Any =
        PGobject().apply {
            type = "jsonb"
            this.value = value
        }
}

internal fun Table.jsonb(name: String): Column<String> = registerColumn(name, JsonbColumnType())
