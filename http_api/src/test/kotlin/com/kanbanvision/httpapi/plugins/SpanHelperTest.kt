package com.kanbanvision.httpapi.plugins

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpanHelperTest {
    @Test
    fun `given successful block when wrapping in span then helper returns the block result`() =
        runTest {
            val result = withSpan("test.success") { 42 }

            assertEquals(42, result)
        }

    @Test
    fun `given failing block when wrapping in span then helper rethrows the exception`() =
        runTest {
            assertFailsWith<IllegalStateException> {
                withSpan("test.error") {
                    throw IllegalStateException("boom")
                }
            }
        }

    @Test
    fun `given cancellation exception when wrapping in span then helper rethrows cancellation`() =
        runTest {
            assertFailsWith<CancellationException> {
                withSpan("test.cancellation") {
                    throw CancellationException("cancel")
                }
            }
        }
}
