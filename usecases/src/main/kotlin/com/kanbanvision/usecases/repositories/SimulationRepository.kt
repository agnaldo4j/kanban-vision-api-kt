package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Simulation

interface SimulationRepository {
    suspend fun save(simulation: Simulation): Either<DomainError, Simulation>

    suspend fun findById(id: String): Either<DomainError, Simulation>

    suspend fun findAll(
        organizationId: String,
        page: Int,
        size: Int,
    ): Either<DomainError, List<Simulation>>

    suspend fun countByOrganization(organizationId: String): Either<DomainError, Long>
}
