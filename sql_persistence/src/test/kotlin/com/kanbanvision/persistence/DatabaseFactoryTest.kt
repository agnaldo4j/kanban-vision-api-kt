package com.kanbanvision.persistence

import com.kanbanvision.persistence.support.EmbeddedPostgresSupport
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseFactoryTest {
    @Test
    fun `given initialized embedded database when checking readiness then result is true`() {
        EmbeddedPostgresSupport.ensureStarted()
        EmbeddedPostgresSupport.resetDatabase()

        assertTrue(DatabaseFactory.isReady())
    }

    @Test
    fun `given initialized datasource when refreshing datasource then readiness remains true`() {
        EmbeddedPostgresSupport.ensureStarted()
        EmbeddedPostgresSupport.refreshDataSource()
        EmbeddedPostgresSupport.resetDatabase()

        assertTrue(DatabaseFactory.isReady())
    }

    @Test
    fun `given closed datasource when checking readiness then result is false`() {
        EmbeddedPostgresSupport.ensureStarted()
        EmbeddedPostgresSupport.refreshDataSource()
        DatabaseFactory.dataSource.close()

        assertFalse(DatabaseFactory.isReady())
    }

    @Test
    fun `given interrupted current thread when checking readiness then timeout branch returns false`() {
        EmbeddedPostgresSupport.ensureStarted()
        EmbeddedPostgresSupport.refreshDataSource()
        Thread.currentThread().interrupt()
        try {
            assertFalse(DatabaseFactory.isReady())
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `given initialized datasource when close is called then datasource is shut down and isReady returns false`() {
        EmbeddedPostgresSupport.ensureStarted()
        EmbeddedPostgresSupport.refreshDataSource()

        DatabaseFactory.close()

        assertFalse(DatabaseFactory.isReady())

        EmbeddedPostgresSupport.refreshDataSource() // restore for subsequent tests
    }

    @Test
    fun `given database config when initializing with migrations disabled then connection is established without running migrations`() {
        EmbeddedPostgresSupport.ensureStarted()
        val url = DatabaseFactory.dataSource.jdbcUrl

        DatabaseFactory.init(
            DatabaseConfig(url = url, driver = "org.postgresql.Driver", user = "postgres", password = "postgres", poolSize = 1),
            migrationsEnabled = false,
        )

        assertTrue(DatabaseFactory.isReady())

        EmbeddedPostgresSupport.refreshDataSource() // restore full pool config
    }

    @Test
    fun `given database config created with default constructor values then pool size defaults to ten`() {
        val config =
            DatabaseConfig(
                url = "jdbc:postgresql://localhost:5432/test",
                driver = "org.postgresql.Driver",
                user = "postgres",
                password = "postgres",
            )

        assertTrue(config.poolSize == 10)
        assertTrue(config.migrationsLocation == "classpath:db/migration")
    }

    @Test
    fun `given filesystem migrations location when initializing a fresh database then flyway migrates from the custom path`() {
        EmbeddedPostgresSupport.ensureStarted()
        val freshDbUrl = createFreshDatabase("flyway_fs_location_test")

        try {
            DatabaseFactory.init(
                DatabaseConfig(
                    url = freshDbUrl,
                    driver = "org.postgresql.Driver",
                    user = "postgres",
                    password = "postgres",
                    poolSize = 2,
                    // Contrato do Native Image (ADR-0032): a imagem seta FLYWAY_LOCATIONS
                    // com filesystem: porque o ClassPathScanner não lê resources do binário.
                    migrationsLocation = "filesystem:src/main/resources/db/migration",
                ),
            )

            // Banco criado vazio nesta execução: as linhas do histórico SÓ podem ter vindo
            // da location filesystem customizada.
            val applied = countSuccessfulMigrations()
            assertTrue(applied >= 2, "expected V1+V2 applied from filesystem location, got $applied")
        } finally {
            EmbeddedPostgresSupport.refreshDataSource()
        }
    }

    private fun createFreshDatabase(name: String): String {
        val baseUrl = DatabaseFactory.dataSource.jdbcUrl
        DatabaseFactory.dataSource.connection.use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { st ->
                st.execute("DROP DATABASE IF EXISTS $name")
                st.execute("CREATE DATABASE $name")
            }
        }
        return baseUrl.replace(Regex("/postgres(\\?|$)"), "/$name$1")
    }

    private fun countSuccessfulMigrations(): Int =
        DatabaseFactory.dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }
}
