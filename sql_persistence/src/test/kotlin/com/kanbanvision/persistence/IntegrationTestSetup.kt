package com.kanbanvision.persistence

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

object IntegrationTestSetup {
    private var initialized = false
    private var pg: EmbeddedPostgres? = null

    fun ensureInitialized() {
        if (!initialized) {
            val embeddedPg = EmbeddedPostgres.start()
            pg = embeddedPg
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    DatabaseFactory.dataSource.close()
                    embeddedPg.close()
                },
            )
            DatabaseFactory.init(
                DatabaseConfig(
                    url = embeddedPg.getJdbcUrl("postgres", "postgres"),
                    driver = "org.postgresql.Driver",
                    user = "postgres",
                    password = "",
                ),
            )
            initialized = true
        }
    }

    fun closeDataSource() {
        DatabaseFactory.dataSource.close()
    }

    fun reinitDataSource() {
        pg?.let { embeddedPg ->
            DatabaseFactory.init(
                DatabaseConfig(
                    url = embeddedPg.getJdbcUrl("postgres", "postgres"),
                    driver = "org.postgresql.Driver",
                    user = "postgres",
                    password = "",
                ),
            )
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
