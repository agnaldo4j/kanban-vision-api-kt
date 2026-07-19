package com.kanbanvision.usecases.cqs

import arrow.core.Either
import com.kanbanvision.domain.common.errors.CommonError

interface Query {
    fun validate(): Either<CommonError.ValidationError, Unit>
}
