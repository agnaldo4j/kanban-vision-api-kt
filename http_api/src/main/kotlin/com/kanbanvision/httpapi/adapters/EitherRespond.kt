package com.kanbanvision.httpapi.adapters

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import com.kanbanvision.httpapi.dtos.ValidationErrorResponse
import com.kanbanvision.httpapi.plugins.REQUEST_ID_KEY
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun ApplicationCall.respondWithDomainError(error: DomainError) {
    val requestId = attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown"
    return when (error) {
        is DomainError.ValidationError ->
            respond(HttpStatusCode.BadRequest, ValidationErrorResponse(errors = error.messages, requestId = requestId))
        is DomainError.BoardNotFound, is DomainError.CardNotFound, is DomainError.ColumnNotFound,
        is DomainError.StepNotFound,
        is DomainError.OrganizationNotFound, is DomainError.ScenarioNotFound,
        ->
            respond(HttpStatusCode.NotFound, DomainErrorResponse(error = notFoundMessage(error), requestId = requestId))
        is DomainError.PersistenceError ->
            respond(HttpStatusCode.InternalServerError, DomainErrorResponse(error = "Internal server error", requestId = requestId))
        is DomainError.InvalidDecision ->
            respond(HttpStatusCode.BadRequest, DomainErrorResponse(error = error.reason, requestId = requestId))
        is DomainError.DayAlreadyExecuted ->
            respond(HttpStatusCode.Conflict, DomainErrorResponse(error = "Day ${error.day} was already executed", requestId = requestId))
    }
}

private fun notFoundMessage(error: DomainError): String =
    when (error) {
        is DomainError.BoardNotFound -> "Board not found"
        is DomainError.CardNotFound -> "Card not found"
        is DomainError.ColumnNotFound -> "Column not found"
        is DomainError.StepNotFound -> "Step not found"
        is DomainError.OrganizationNotFound -> "Organization not found"
        is DomainError.ScenarioNotFound -> "Scenario not found"
        else -> "Resource not found"
    }
