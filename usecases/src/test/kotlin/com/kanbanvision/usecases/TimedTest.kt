package com.kanbanvision.usecases

import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TimedTest {
    @Test
    fun `timed returns value and non-negative duration on success`() =
        runTest {
            val result =
                either<String, Int> {
                    val (value, duration) = timed { 42.right() }
                    assertEquals(42, value)
                    assertTrue(duration.inWholeNanoseconds >= 0)
                    value
                }
            assertTrue(result.isRight())
        }

    @Test
    fun `timed propagates error via Raise when block returns Left`() =
        runTest {
            val result =
                either<String, Int> {
                    timed { "not found".left() }
                    0
                }
            assertTrue(result.isLeft())
            assertIs<String>(result.leftOrNull())
            assertEquals("not found", result.leftOrNull())
        }
}
