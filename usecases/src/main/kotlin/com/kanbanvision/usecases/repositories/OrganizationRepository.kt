package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Organization

interface OrganizationRepository {
    suspend fun findById(id: String): Either<DomainError, Organization>
}
