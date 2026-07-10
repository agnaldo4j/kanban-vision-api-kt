package com.kanbanvision.usecases.simulation

import com.kanbanvision.usecases.simulation.commands.CreateSimulationCommand
import com.kanbanvision.usecases.simulation.queries.GetDailySnapshotQuery
import com.kanbanvision.usecases.simulation.queries.ListSimulationsQuery
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class CommandValidationPropertyTest {
    @Test
    fun `CreateSimulationCommand rejects any blank organizationId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                CreateSimulationCommand(organizationId = blank, wipLimit = 1, teamSize = 1, seedValue = 0L)
                    .validate()
                    .isLeft()
            }
        }
    }

    @Test
    fun `CreateSimulationCommand rejects wipLimit of zero or negative`() {
        runBlocking {
            forAll(Arb.int(NEG_BOUND..0)) { invalid ->
                CreateSimulationCommand(organizationId = "org-1", wipLimit = invalid, teamSize = 1, seedValue = 0L)
                    .validate()
                    .isLeft()
            }
        }
    }

    @Test
    fun `CreateSimulationCommand rejects teamSize of zero or negative`() {
        runBlocking {
            forAll(Arb.int(NEG_BOUND..0)) { invalid ->
                CreateSimulationCommand(organizationId = "org-1", wipLimit = 1, teamSize = invalid, seedValue = 0L)
                    .validate()
                    .isLeft()
            }
        }
    }

    @Test
    fun `CreateSimulationCommand accepts valid inputs`() {
        runBlocking {
            forAll(ARB_NON_BLANK, Arb.int(1..POS_BOUND), Arb.int(1..POS_BOUND)) { orgId, wip, team ->
                CreateSimulationCommand(organizationId = orgId, wipLimit = wip, teamSize = team, seedValue = 0L)
                    .validate()
                    .isRight()
            }
        }
    }

    @Test
    fun `ListSimulationsQuery rejects any blank organizationId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                ListSimulationsQuery(organizationId = blank).validate().isLeft()
            }
        }
    }

    @Test
    fun `ListSimulationsQuery rejects page less than 1`() {
        runBlocking {
            forAll(Arb.int(NEG_BOUND..0)) { invalid ->
                ListSimulationsQuery(organizationId = "org-1", page = invalid).validate().isLeft()
            }
        }
    }

    @Test
    fun `ListSimulationsQuery rejects size outside 1 to 100`() {
        runBlocking {
            forAll(Arb.int(NEG_BOUND..0)) { invalid ->
                ListSimulationsQuery(organizationId = "org-1", size = invalid).validate().isLeft()
            }
        }
    }

    @Test
    fun `ListSimulationsQuery rejects size above 100`() {
        runBlocking {
            forAll(Arb.int(101..POS_BOUND)) { tooBig ->
                ListSimulationsQuery(organizationId = "org-1", size = tooBig).validate().isLeft()
            }
        }
    }

    @Test
    fun `GetDailySnapshotQuery rejects any blank simulationId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                GetDailySnapshotQuery(simulationId = blank, day = 1, callerOrganizationId = FIXTURE_ORGANIZATION_ID)
                    .validate()
                    .isLeft()
            }
        }
    }

    @Test
    fun `GetDailySnapshotQuery rejects day less than 1`() {
        runBlocking {
            forAll(Arb.int(NEG_BOUND..0)) { invalid ->
                GetDailySnapshotQuery(simulationId = "sim-1", day = invalid, callerOrganizationId = FIXTURE_ORGANIZATION_ID)
                    .validate()
                    .isLeft()
            }
        }
    }

    @Test
    fun `GetDailySnapshotQuery rejects any blank callerOrganizationId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                GetDailySnapshotQuery(simulationId = "sim-1", day = 1, callerOrganizationId = blank)
                    .validate()
                    .isLeft()
            }
        }
    }

    @Test
    fun `GetDailySnapshotQuery accepts valid inputs`() {
        runBlocking {
            forAll(ARB_NON_BLANK, Arb.int(1..POS_BOUND), ARB_NON_BLANK) { simId, day, callerOrgId ->
                GetDailySnapshotQuery(simulationId = simId, day = day, callerOrganizationId = callerOrgId)
                    .validate()
                    .isRight()
            }
        }
    }

    private companion object {
        const val NAME_MAX = 50
        const val NEG_BOUND = -1000
        const val POS_BOUND = 1000
        val ARB_BLANK: Arb<String> = Arb.of("", " ", "   ", "\t", "\n")
        val ARB_NON_BLANK: Arb<String> =
            Arb.string(minSize = 1, maxSize = NAME_MAX).filter { it.isNotBlank() }
    }
}
