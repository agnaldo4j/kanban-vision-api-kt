package com.kanbanvision.persistence.repositories

import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.StepRef
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlin.test.Test
import kotlin.test.assertFailsWith

class JdbcCardRepositoryInternalUpdateTest {
    @Test
    fun `given update statement that affects zero rows when applying persistence transform then repository throws illegal state`() {
        val repository = JdbcCardRepository()
        val connection = mockk<java.sql.Connection>()
        val statement = mockk<java.sql.PreparedStatement>()
        val card = Card(id = "09000000-0000-0000-0000-000000000001", step = StepRef("s1"), title = "card", position = 0)

        every { connection.prepareStatement(any<String>()) } returns statement
        every { statement.setString(any(), any()) } just runs
        every { statement.setInt(any(), any()) } just runs
        every { statement.executeUpdate() } returns 0
        every { statement.close() } just runs

        val method =
            JdbcCardRepository::class
                .java
                .getDeclaredMethod(
                    "applyAndPersist",
                    java.sql.Connection::class.java,
                    Card::class.java,
                    kotlin.jvm.functions.Function1::class.java,
                ).apply { isAccessible = true }

        val error =
            assertFailsWith<java.lang.reflect.InvocationTargetException> {
                method.invoke(repository, connection, card, { current: Card -> current })
            }
        assertFailsWith<IllegalStateException> { throw error.targetException }
    }
}
