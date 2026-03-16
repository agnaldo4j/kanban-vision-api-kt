package com.kanbanvision.persistence

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlywayMigrationIntegrationTest {
    @BeforeAll
    fun initDatabase() {
        IntegrationTestSetup.ensureInitialized()
    }

    @Test
    fun `flyway migrations create all seven domain tables`() {
        val expectedTables =
            listOf(
                "boards",
                "columns",
                "cards",
                "tenants",
                "scenarios",
                "scenario_states",
                "daily_snapshots",
            )
        DatabaseFactory.dataSource.connection.use { conn ->
            expectedTables.forEach { table ->
                val result =
                    conn
                        .createStatement()
                        .executeQuery("SELECT to_regclass('public.$table')")
                result.next()
                assertNotNull(result.getString(1), "Table '$table' should exist")
                result.close()
            }
            conn.rollback()
        }
    }

    @Test
    fun `flyway schema history table is created after migration`() {
        DatabaseFactory.dataSource.connection.use { conn ->
            val result =
                conn
                    .createStatement()
                    .executeQuery("SELECT to_regclass('public.flyway_schema_history')")
            result.next()
            assertNotNull(result.getString(1), "flyway_schema_history should exist")
            result.close()
            conn.rollback()
        }
    }

    @Test
    fun `flyway applies at least two versioned migrations`() {
        DatabaseFactory.dataSource.connection.use { conn ->
            val result =
                conn
                    .createStatement()
                    .executeQuery(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE type = 'SQL' AND success = true",
                    )
            result.next()
            assertTrue(result.getInt(1) >= 2, "Expected at least 2 successful migrations")
            result.close()
            conn.rollback()
        }
    }

    @Test
    fun `V2 migration creates FK index on cards column_id`() {
        DatabaseFactory.dataSource.connection.use { conn ->
            val result =
                conn
                    .createStatement()
                    .executeQuery(
                        """
                        SELECT COUNT(*) FROM pg_indexes
                        WHERE tablename = 'cards' AND indexname = 'idx_cards_column_id'
                        """.trimIndent(),
                    )
            result.next()
            assertTrue(result.getInt(1) == 1, "idx_cards_column_id should exist")
            result.close()
            conn.rollback()
        }
    }

    @Test
    fun `V2 migration creates CHECK constraints on scenarios`() {
        DatabaseFactory.dataSource.connection.use { conn ->
            val result =
                conn
                    .createStatement()
                    .executeQuery(
                        """
                        SELECT COUNT(*) FROM information_schema.check_constraints
                        WHERE constraint_schema = 'public'
                          AND constraint_name IN ('check_wip_limit_positive', 'check_team_size_positive')
                        """.trimIndent(),
                    )
            result.next()
            assertTrue(result.getInt(1) == 2, "Expected 2 CHECK constraints on scenarios")
            result.close()
            conn.rollback()
        }
    }
}
