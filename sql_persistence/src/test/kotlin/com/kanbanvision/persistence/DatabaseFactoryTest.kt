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
