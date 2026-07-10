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
                RunDayCommand(simulationId = blank, decisions = emptyList(), callerOrganizationId = FIXTURE_ORGANIZATION_ID)
                    .validate()
                    .isLeft()
            }
        }
    }

    @Test
    fun `RunDayCommand rejects any blank callerOrganizationId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                RunDayCommand(simulationId = "sim-1", decisions = emptyList(), callerOrganizationId = blank)
                    .validate()
                    .isLeft()
            }
        }
    }

    @Test
    fun `RunDayCommand accepts any non-blank simulationId`() {
        runBlocking {
            forAll(ARB_NON_BLANK, ARB_NON_BLANK) { id, callerOrgId ->
                RunDayCommand(simulationId = id, decisions = emptyList(), callerOrganizationId = callerOrgId)
                    .validate()
                    .isRight()
            }
        }
    }

    @Test
    fun `GetSimulationQuery rejects any blank simulationId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                GetSimulationQuery(simulationId = blank, callerOrganizationId = FIXTURE_ORGANIZATION_ID)
                    .validate()
                    .isLeft()
            }
        }
    }

    @Test
    fun `GetSimulationQuery rejects any blank callerOrganizationId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                GetSimulationQuery(simulationId = "sim-1", callerOrganizationId = blank).validate().isLeft()
            }
        }
    }

    @Test
    fun `GetSimulationQuery accepts any non-blank simulationId`() {
        runBlocking {
            forAll(ARB_NON_BLANK, ARB_NON_BLANK) { id, callerOrgId ->
                GetSimulationQuery(simulationId = id, callerOrganizationId = callerOrgId).validate().isRight()
            }
        }
    }

    @Test
    fun `GetSimulationDaysQuery rejects any blank simulationId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                GetSimulationDaysQuery(simulationId = blank, callerOrganizationId = FIXTURE_ORGANIZATION_ID)
                    .validate()
                    .isLeft()
            }
        }
    }

    @Test
    fun `GetSimulationDaysQuery rejects any blank callerOrganizationId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                GetSimulationDaysQuery(simulationId = "sim-1", callerOrganizationId = blank).validate().isLeft()
            }
        }
    }

    @Test
    fun `GetSimulationDaysQuery accepts any non-blank simulationId`() {
        runBlocking {
            forAll(ARB_NON_BLANK, ARB_NON_BLANK) { id, callerOrgId ->
                GetSimulationDaysQuery(simulationId = id, callerOrganizationId = callerOrgId).validate().isRight()
            }
        }
    }

    @Test
    fun `GetSimulationCfdQuery rejects any blank simulationId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                GetSimulationCfdQuery(simulationId = blank, callerOrganizationId = FIXTURE_ORGANIZATION_ID)
                    .validate()
                    .isLeft()
            }
        }
    }

    @Test
    fun `GetSimulationCfdQuery rejects any blank callerOrganizationId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                GetSimulationCfdQuery(simulationId = "sim-1", callerOrganizationId = blank).validate().isLeft()
            }
        }
    }

    @Test
    fun `GetSimulationCfdQuery accepts any non-blank simulationId`() {
        runBlocking {
            forAll(ARB_NON_BLANK, ARB_NON_BLANK) { id, callerOrgId ->
                GetSimulationCfdQuery(simulationId = id, callerOrganizationId = callerOrgId).validate().isRight()
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
