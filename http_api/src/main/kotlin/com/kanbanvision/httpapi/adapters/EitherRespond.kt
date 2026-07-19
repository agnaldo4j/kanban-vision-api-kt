package com.kanbanvision.httpapi.adapters

import com.kanbanvision.domain.common.errors.CommonError
import com.kanbanvision.domain.common.errors.DomainError
import com.kanbanvision.domain.errors.KanbanError
import com.kanbanvision.domain.errors.SimulationError
import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import com.kanbanvision.httpapi.dtos.ValidationErrorResponse
import com.kanbanvision.httpapi.support.REQUEST_ID_KEY
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond

/**
 * Extrai um path parameter obrigatório respondendo 400 quando ausente.
 * O caminho nulo é defensivo: o Ktor só invoca o handler quando o segmento
 * casou (ausência vira 404 no roteamento) — centralizar aqui elimina as 6
 * cópias de guard inalcançável que existiam nos handlers (ADR-0029).
 */
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

/**
 * Resolve o organizationId do JWT do chamador (fonte da verdade de tenancy). O plugin de
 * autenticação (`validate`) já rejeita tokens sem o claim ou com claim em branco, então aqui basta
 * tratar a ausência de principal — caminho defensivo (rota mal-configurada fora de `authenticate`):
 * responde 401 (fail closed) em vez de assumir acesso (security.md §A10). Um claim eventualmente em
 * branco que escapasse ainda é barrado pela validação de `isNotBlank` na query/command a jusante.
 */
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
        is KanbanError.BoardNotFound, is KanbanError.CardNotFound,
        is KanbanError.StepNotFound,
        is KanbanError.OrganizationNotFound, is SimulationError.SimulationNotFound,
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
        // Fail-closed (ADR-0038 / security.md §Fail Closed): DomainError é interface aberta, então um
        // erro não previsto vira 500 genérico em vez de vazar detalhe ou escapar sem resposta. Cada
        // grupo (CommonError/KanbanError/SimulationError) é sealed, então uma variante nova dentro de
        // um grupo é pega no code review; um erro fora dos grupos cai aqui, seguro.
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
        else -> "Resource not found"
    }
