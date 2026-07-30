package com.kanbanvision.httpapi.adapters

import com.kanbanvision.domain.common.errors.CommonError
import com.kanbanvision.domain.common.errors.DomainError
import com.kanbanvision.domain.model.kanban.KanbanError
import com.kanbanvision.domain.model.simulation.SimulationError
import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import com.kanbanvision.httpapi.dtos.ValidationErrorResponse
import com.kanbanvision.httpapi.support.REQUEST_ID_KEY
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond

suspend fun ApplicationCall.requiredPathParam(
    name: String,
    message: String,
): String? {
    val value = parameters[name]
    if (value == null) {
        respondWithDomainError(CommonError.ValidationError(message))
        return null
    }
    return value
}

suspend fun ApplicationCall.callerOrganizationId(): String? {
    val principal = principal<JWTPrincipal>()
    if (principal == null) {
        respondMissingOrganization()
        return null
    }
    val organizationId = principal.payload.getClaim("organizationId").asString()
    if (organizationId == null) {
        respondMissingOrganization()
        return null
    }
    return organizationId
}

private suspend fun ApplicationCall.respondMissingOrganization() {
    val requestId = attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown"
    respond(
        HttpStatusCode.Unauthorized,
        DomainErrorResponse(error = "Missing organization context", requestId = requestId),
    )
}

suspend fun ApplicationCall.respondWithDomainError(error: DomainError) {
    val requestId = attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown"
    return when (error) {
        is CommonError.ValidationError ->
            respond(HttpStatusCode.BadRequest, ValidationErrorResponse(errors = error.messages, requestId = requestId))
        is KanbanError.BoardNotFound, is KanbanError.CardNotFound, is KanbanError.StepNotFound,
        is KanbanError.OrganizationNotFound, is SimulationError.SimulationNotFound,
        is SimulationError.SnapshotNotFound,
        ->
            respond(HttpStatusCode.NotFound, DomainErrorResponse(error = notFoundMessage(error), requestId = requestId))
        is CommonError.PersistenceError ->
            respond(HttpStatusCode.InternalServerError, DomainErrorResponse(error = "Internal server error", requestId = requestId))
        is SimulationError.InvalidDecision ->
            respond(HttpStatusCode.BadRequest, DomainErrorResponse(error = error.reason, requestId = requestId))
        is CommonError.Forbidden ->
            respond(HttpStatusCode.Forbidden, DomainErrorResponse(error = "Forbidden", requestId = requestId))
        is SimulationError.DayAlreadyExecuted ->
            respond(HttpStatusCode.Conflict, DomainErrorResponse(error = "Day ${error.day} was already executed", requestId = requestId))
        is CommonError.ServiceUnavailable ->
            respond(
                HttpStatusCode.ServiceUnavailable,
                DomainErrorResponse(error = "Service temporarily unavailable", requestId = requestId),
            )
        else ->
            respond(
                HttpStatusCode.InternalServerError,
                DomainErrorResponse(error = "Internal server error", requestId = requestId),
            )
    }
}

private fun notFoundMessage(error: DomainError): String =
    when (error) {
        is KanbanError.BoardNotFound -> "Board not found"
        is KanbanError.CardNotFound -> "Card not found"
        is KanbanError.StepNotFound -> "Step not found"
        is KanbanError.OrganizationNotFound -> "Organization not found"
        is SimulationError.SimulationNotFound -> "Simulation not found"
        is SimulationError.SnapshotNotFound -> "Snapshot not found"
        else -> "Resource not found"
    }
