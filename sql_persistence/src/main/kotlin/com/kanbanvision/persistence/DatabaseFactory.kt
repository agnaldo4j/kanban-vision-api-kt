package com.kanbanvision.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

data class DatabaseConfig(
    val url: String,
    val driver: String,
    val user: String,
    val password: String,
    val poolSize: Int = 10,
)

object DatabaseFactory {
    lateinit var dataSource: HikariDataSource
        private set

    fun init(config: DatabaseConfig) {
        val hikariConfig =
            HikariConfig().apply {
                jdbcUrl = config.url
                driverClassName = config.driver
                username = config.user
                password = config.password
                maximumPoolSize = config.poolSize
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            }
        dataSource = HikariDataSource(hikariConfig)
        createSchema()
    }

    private fun createSchema() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                createBoardsTable(stmt)
                createColumnsTable(stmt)
                createCardsTable(stmt)
            }
            conn.commit()
        }
    }

    private fun createBoardsTable(stmt: java.sql.Statement) {
        stmt.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS boards (
                id         VARCHAR(36)  PRIMARY KEY,
                name       VARCHAR(255) NOT NULL,
                created_at BIGINT       NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createColumnsTable(stmt: java.sql.Statement) {
        stmt.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS columns (
                id         VARCHAR(36)  PRIMARY KEY,
                board_id   VARCHAR(36)  NOT NULL REFERENCES boards(id),
                name       VARCHAR(255) NOT NULL,
                position   INT          NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createCardsTable(stmt: java.sql.Statement) {
        stmt.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS cards (
                id          VARCHAR(36)  PRIMARY KEY,
                column_id   VARCHAR(36)  NOT NULL REFERENCES columns(id),
                title       VARCHAR(255) NOT NULL,
                description TEXT         NOT NULL DEFAULT '',
                position    INT          NOT NULL,
                created_at  BIGINT       NOT NULL
            )
            """.trimIndent(),
        )
    }
}
