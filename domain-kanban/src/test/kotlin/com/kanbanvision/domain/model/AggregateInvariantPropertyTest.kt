package com.kanbanvision.domain.model

import com.kanbanvision.domain.common.model.NonBlankName
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.organization.Squad
import com.kanbanvision.domain.model.organization.Tribe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class AggregateInvariantPropertyTest {
    @Test
    fun `Organization create rejects any blank name`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                runCatching { Organization.create(blank) }.isFailure
            }
        }
    }

    @Test
    fun `Organization create accepts any non-blank name`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { name ->
                runCatching { Organization.create(name) }.isSuccess
            }
        }
    }

    @Test
    fun `Tribe rejects any blank name`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                runCatching { Tribe(name = NonBlankName(blank)) }.isFailure
            }
        }
    }

    @Test
    fun `Tribe accepts any non-blank name`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { name ->
                runCatching { Tribe(name = NonBlankName(name)) }.isSuccess
            }
        }
    }

    @Test
    fun `Squad rejects any blank name`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                runCatching { Squad(name = NonBlankName(blank)) }.isFailure
            }
        }
    }

    @Test
    fun `Squad accepts any non-blank name`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { name ->
                runCatching { Squad(name = NonBlankName(name)) }.isSuccess
            }
        }
    }

    private companion object {
        const val NAME_MAX = 50
        val ARB_BLANK: Arb<String> = Arb.of("", " ", "   ", "\t", "\n")
        val ARB_NON_BLANK: Arb<String> =
            Arb.string(minSize = 1, maxSize = NAME_MAX).filter { it.isNotBlank() }
    }
}
