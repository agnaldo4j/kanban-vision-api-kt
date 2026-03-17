package com.kanbanvision.httpapi

import com.kanbanvision.httpapi.plugins.withSpan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SpanHelperTest {
    @Test
    fun `withSpan returns block result on success`() =
        runBlocking {
            val result = withSpan("test.span") { 42 }
            assertEquals(42, result)
        }

    @Test
    fun `withSpan propagates exception from block`() {
        assertThrows<IllegalStateException> {
            runBlocking {
                withSpan("test.span.error") {
                    throw IllegalStateException("simulated error")
                }
            }
        }
    }

    @Test
    fun `withSpan propagates CancellationException without recording as error`() {
        assertThrows<CancellationException> {
            runBlocking {
                withSpan("test.span.cancelled") {
                    throw CancellationException("cancelled")
                }
            }
        }
    }
}
