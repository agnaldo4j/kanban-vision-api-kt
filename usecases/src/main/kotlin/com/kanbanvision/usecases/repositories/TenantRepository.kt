package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.tenant.Tenant
import com.kanbanvision.domain.model.valueobjects.TenantId

interface TenantRepository {
    suspend fun findById(id: TenantId): Either<DomainError, Tenant>
}
