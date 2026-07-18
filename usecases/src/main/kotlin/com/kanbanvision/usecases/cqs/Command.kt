package com.kanbanvision.usecases.cqs

import arrow.core.Either
import com.kanbanvision.domain.errors.CommonError

interface Command {
    fun validate(): Either<CommonError.ValidationError, Unit>
}
