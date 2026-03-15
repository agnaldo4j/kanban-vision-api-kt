package com.kanbanvision.usecases.cqs

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError

interface Query {
    fun validate(): Either<DomainError.ValidationError, Unit>
}
