package com.kanbanvision.usecases.simulation

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.SimulationStatus
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.simulation.queries.ListSimulationsQuery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ListSimulationsUseCaseTest {
    private val simulationRepository = mockk<SimulationRepository>()
    private val useCase = ListSimulationsUseCase(simulationRepository)

    @Test
    fun `given valid query when listing simulations then page result with simulations is returned`() =
        runTest {
            val simulations = listOf(fixtureSimulation("s1"), fixtureSimulation("s2"))
            coEvery { simulationRepository.findAll("org-1", 1, 20) } returns simulations.right()
            coEvery { simulationRepository.countByOrganization("org-1") } returns 2L.right()

            val result = useCase.execute(ListSimulationsQuery(organizationId = "org-1", page = 1, size = 20))

            assertTrue(result.isRight())
            val page = result.getOrNull()!!
            assertEquals(2, page.data.size)
            assertEquals(1, page.page)
            assertEquals(20, page.size)
            assertEquals(2L, page.total)
            coVerify(exactly = 1) { simulationRepository.findAll("org-1", 1, 20) }
            coVerify(exactly = 1) { simulationRepository.countByOrganization("org-1") }
        }

    @Test
    fun `given blank organization id when listing simulations then validation error is returned`() =
        runTest {
            val result = useCase.execute(ListSimulationsQuery(organizationId = ""))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { simulationRepository.findAll(any(), any(), any()) }
        }

    @Test
    fun `given page zero when listing simulations then validation error is returned`() =
        runTest {
            val result = useCase.execute(ListSimulationsQuery(organizationId = "org-1", page = 0))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
        }

    @Test
    fun `given size above max when listing simulations then validation error is returned`() =
        runTest {
            val result = useCase.execute(ListSimulationsQuery(organizationId = "org-1", size = 101))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
        }

    @Test
    fun `given persistence error when listing simulations then error is propagated`() =
        runTest {
            coEvery { simulationRepository.findAll("org-1", 1, 20) } returns
                DomainError.PersistenceError("db error").left()
            coEvery { simulationRepository.countByOrganization("org-1") } returns 0L.right()

            val result = useCase.execute(ListSimulationsQuery(organizationId = "org-1"))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `given simulations with different statuses when listing then all statuses are preserved`() =
        runTest {
            val simulations =
                listOf(
                    fixtureSimulation("s1", status = SimulationStatus.DRAFT),
                    fixtureSimulation("s2", status = SimulationStatus.FINISHED),
                )
            coEvery { simulationRepository.findAll("org-1", 1, 20) } returns simulations.right()
            coEvery { simulationRepository.countByOrganization("org-1") } returns 2L.right()

            val result = useCase.execute(ListSimulationsQuery(organizationId = "org-1"))

            assertTrue(result.isRight())
            assertEquals(SimulationStatus.DRAFT, result.getOrNull()!!.data[0].status)
            assertEquals(SimulationStatus.FINISHED, result.getOrNull()!!.data[1].status)
        }
}
