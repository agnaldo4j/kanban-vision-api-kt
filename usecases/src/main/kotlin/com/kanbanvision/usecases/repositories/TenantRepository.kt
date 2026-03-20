package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Tenant

interface TenantRepository {
    suspend fun findById(id: String): Either<DomainError, Tenant>
}
