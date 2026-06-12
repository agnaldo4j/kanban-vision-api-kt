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
    }
}
