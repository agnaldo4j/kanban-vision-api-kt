package com.kanbanvision.persistence

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

object IntegrationTestSetup {
    private var initialized = false

    fun ensureInitialized() {
        if (!initialized) {
            val pg = EmbeddedPostgres.start()
            DatabaseFactory.init(
                DatabaseConfig(
                    url = pg.getJdbcUrl("postgres", "postgres"),
                    driver = "org.postgresql.Driver",
                    user = "postgres",
                    password = "",
                ),
            )
            initialized = true
        }
    }

    fun cleanTables() {
        DatabaseFactory.dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("DELETE FROM cards")
                stmt.executeUpdate("DELETE FROM columns")
                stmt.executeUpdate("DELETE FROM boards")
            }
            conn.commit()
        }
    }
}
