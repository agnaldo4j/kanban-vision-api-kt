package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.persistence.IntegrationTestSetup
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcTenantRepositoryIntegrationTest {
    private val repository = JdbcTenantRepository()

    @BeforeAll
    fun initDatabase() {
        IntegrationTestSetup.ensureInitialized()
    }

    @BeforeEach
    fun cleanDatabase() {
        IntegrationTestSetup.cleanTables()
    }

    private fun insertTenant(
        id: String,
        name: String,
    ) {
        DatabaseFactory.dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO tenants (id, name) VALUES (?, ?)").use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, name)
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    @Test
    fun `findById returns tenant when it exists`() =
        runBlocking {
            val id = UUID.randomUUID().toString()
            insertTenant(id, "Test Corp")

            val result = repository.findById(id)

            assertTrue(result.isRight())
            val tenant = result.getOrNull()!!
            assertEquals(id, tenant.id)
            assertEquals("Test Corp", tenant.name)
        }

    @Test
    fun `findById returns TenantNotFound when tenant does not exist`() =
        runBlocking<Unit> {
            val result = repository.findById(UUID.randomUUID().toString())

            assertTrue(result.isLeft())
            assertIs<DomainError.TenantNotFound>(result.leftOrNull())
        }

    @Test
    fun `findById with non-UUID string returns TenantNotFound`() =
        runBlocking<Unit> {
            val result = repository.findById("not-a-valid-uuid")

            assertTrue(result.isLeft())
            assertIs<DomainError.TenantNotFound>(result.leftOrNull())
        }

    @Test
    fun `findById returns tenant whose name contains special characters`() =
        runBlocking {
            val id = UUID.randomUUID().toString()
            insertTenant(id, "Empresa 'Ação' & Co.")

            val result = repository.findById(id)

            assertTrue(result.isRight())
            assertEquals("Empresa 'Ação' & Co.", result.getOrNull()?.name)
        }
}
