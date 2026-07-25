package com.kanbanvision.domain.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NonBlankStringTest {
    @Test
    fun `NonBlankTitle accepts a non-blank value and exposes it`() {
        assertEquals("Build API", NonBlankTitle("Build API").value)
    }

    @Test
    fun `NonBlankTitle rejects blank and empty values`() {
        assertFailsWith<IllegalArgumentException> { NonBlankTitle("") }
        assertFailsWith<IllegalArgumentException> { NonBlankTitle("   ") }
    }

    @Test
    fun `NonBlankName accepts a non-blank value and exposes it`() {
        assertEquals("Acme", NonBlankName("Acme").value)
    }

    @Test
    fun `NonBlankName rejects blank and empty values`() {
        assertFailsWith<IllegalArgumentException> { NonBlankName("") }
        assertFailsWith<IllegalArgumentException> { NonBlankName("   ") }
    }
}
