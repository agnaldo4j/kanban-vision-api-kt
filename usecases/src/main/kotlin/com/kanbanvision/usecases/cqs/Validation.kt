package com.kanbanvision.usecases.cqs

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
import com.kanbanvision.domain.common.errors.CommonError

fun accumulateValidation(block: Raise<NonEmptyList<CommonError.ValidationError>>.() -> Unit): Either<CommonError.ValidationError, Unit> =
    either<NonEmptyList<CommonError.ValidationError>, Unit> { block() }
        .mapLeft { errors -> CommonError.ValidationError(errors.toList().flatMap { it.messages }) }

fun Raise<CommonError.ValidationError>.ensureSimulationId(simulationId: String) =
    ensure(simulationId.isNotBlank()) { CommonError.ValidationError("Simulation id must not be blank") }

fun Raise<CommonError.ValidationError>.ensureCallerOrganizationId(callerOrganizationId: String) =
    ensure(callerOrganizationId.isNotBlank()) { CommonError.ValidationError("Caller organization id must not be blank") }

fun validateSimulationRef(
    simulationId: String,
    callerOrganizationId: String,
): Either<CommonError.ValidationError, Unit> =
    accumulateValidation {
        zipOrAccumulate(
            { ensureSimulationId(simulationId) },
            { ensureCallerOrganizationId(callerOrganizationId) },
        ) { _, _ -> }
    }
