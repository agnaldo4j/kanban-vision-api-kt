package com.kanbanvision.usecases.simulation

import com.kanbanvision.usecases.simulation.commands.RunDayCommand
import com.kanbanvision.usecases.simulation.queries.GetSimulationCfdQuery
import com.kanbanvision.usecases.simulation.queries.GetSimulationDaysQuery
import com.kanbanvision.usecases.simulation.queries.GetSimulationQuery
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class SimulationQueryValidationPropertyTest {
    @Test
    fun `RunDayCommand rejects any blank simulationId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                RunDayCommand(simulationId = blank, decisions = emptyList()).validate().isLeft()
            }
        }
    }

    @Test
    fun `RunDayCommand accepts any non-blank simulationId`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { id ->
                RunDayCommand(simulationId = id, decisions = emptyList()).validate().isRight()
            }
        }
    }

    @Test
    fun `GetSimulationQuery rejects any blank simulationId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                GetSimulationQuery(simulationId = blank).validate().isLeft()
            }
        }
    }

    @Test
    fun `GetSimulationQuery accepts any non-blank simulationId`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { id ->
                GetSimulationQuery(simulationId = id).validate().isRight()
            }
        }
    }

    @Test
    fun `GetSimulationDaysQuery rejects any blank simulationId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                GetSimulationDaysQuery(simulationId = blank).validate().isLeft()
            }
        }
    }

    @Test
    fun `GetSimulationDaysQuery accepts any non-blank simulationId`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { id ->
                GetSimulationDaysQuery(simulationId = id).validate().isRight()
            }
        }
    }

    @Test
    fun `GetSimulationCfdQuery rejects any blank simulationId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                GetSimulationCfdQuery(simulationId = blank).validate().isLeft()
            }
        }
    }

    @Test
    fun `GetSimulationCfdQuery accepts any non-blank simulationId`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { id ->
                GetSimulationCfdQuery(simulationId = id).validate().isRight()
            }
        }
    }

    private companion object {
        const val ID_MAX_LENGTH = 50
        val ARB_BLANK: Arb<String> = Arb.of("", " ", "   ", "\t", "\n")
        val ARB_NON_BLANK: Arb<String> =
            Arb.string(minSize = 1, maxSize = ID_MAX_LENGTH).filter { it.isNotBlank() }
    }
}
