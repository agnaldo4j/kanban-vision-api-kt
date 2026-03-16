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
                "columns",
                "cards",
                "tenants",
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
    fun `flyway applies at least two versioned migrations`() {
        DatabaseFactory.dataSource.connection.use { conn ->
            val count =
                queryCount(
                    conn,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE type = 'SQL' AND success = true",
                )
            assertTrue(count >= 2, "Expected at least 2 successful migrations")
            conn.rollback()
        }
    }

    @Test
    fun `V2 migration creates FK index on cards column_id`() {
        DatabaseFactory.dataSource.connection.use { conn ->
            val count =
                queryCount(
                    conn,
                    """
                    SELECT COUNT(*) FROM pg_indexes
                    WHERE tablename = 'cards' AND indexname = 'idx_cards_column_id'
                    """.trimIndent(),
                )
            assertTrue(count == 1, "idx_cards_column_id should exist")
            conn.rollback()
        }
    }

    @Test
    fun `V2 migration creates CHECK constraints on scenarios`() {
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
}
