package com.kanbanvision.persistence

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.Connection
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlywayMigrationIntegrationTest {
    @BeforeAll
    fun initDatabase() {
        IntegrationTestSetup.ensureInitialized()
    }

    private fun queryScalar(
        conn: Connection,
        sql: String,
    ): String? =
        conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                rs.next()
                rs.getString(1)
            }
        }

    private fun queryCount(
        conn: Connection,
        sql: String,
    ): Int =
        conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

    @Test
    fun `flyway migrations create all seven domain tables`() {
        val expectedTables =
            listOf(
                "boards",
                "steps",
                "cards",
                "organizations",
                "scenarios",
                "scenario_states",
                "daily_snapshots",
            )
        DatabaseFactory.dataSource.connection.use { conn ->
            expectedTables.forEach { table ->
                assertNotNull(
                    queryScalar(conn, "SELECT to_regclass('public.$table')"),
                    "Table '$table' should exist",
                )
            }
            conn.rollback()
        }
    }

    @Test
    fun `flyway schema history table is created after migration`() {
        DatabaseFactory.dataSource.connection.use { conn ->
            assertNotNull(
                queryScalar(conn, "SELECT to_regclass('public.flyway_schema_history')"),
                "flyway_schema_history should exist",
            )
            conn.rollback()
        }
    }

    @Test
    fun `flyway applies at least one versioned migration`() {
        DatabaseFactory.dataSource.connection.use { conn ->
            val count =
                queryCount(
                    conn,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE type = 'SQL' AND success = true",
                )
            assertTrue(count >= 1, "Expected at least 1 successful migration")
            conn.rollback()
        }
    }

    @Test
    fun `V1 migration creates FK index on cards step_id`() {
        DatabaseFactory.dataSource.connection.use { conn ->
            val count =
                queryCount(
                    conn,
                    """
                    SELECT COUNT(*) FROM pg_indexes
                    WHERE tablename = 'cards' AND indexname = 'idx_cards_step_id'
                    """.trimIndent(),
                )
            assertTrue(count == 1, "idx_cards_step_id should exist")
            conn.rollback()
        }
    }

    @Test
    fun `V1 migration creates CHECK constraints on scenarios`() {
        DatabaseFactory.dataSource.connection.use { conn ->
            val count =
                queryCount(
                    conn,
                    """
                    SELECT COUNT(*) FROM information_schema.check_constraints
                    WHERE constraint_schema = 'public'
                      AND constraint_name IN ('check_wip_limit_positive', 'check_team_size_positive')
                    """.trimIndent(),
                )
            assertTrue(count == 2, "Expected 2 CHECK constraints on scenarios")
            conn.rollback()
        }
    }

    @Test
    fun `isReady returns true when database is available`() {
        assertTrue(DatabaseFactory.isReady())
    }

    @Test
    fun `isReady returns false when database is unavailable`() {
        IntegrationTestSetup.closeDataSource()
        try {
            assertTrue(!DatabaseFactory.isReady())
        } finally {
            IntegrationTestSetup.reinitDataSource()
        }
    }
}
