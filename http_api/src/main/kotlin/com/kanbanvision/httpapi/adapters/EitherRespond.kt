package com.kanbanvision.httpapi.adapters

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.httpapi.plugins.REQUEST_ID_KEY
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun ApplicationCall.respondWithDomainError(error: DomainError) {
    val requestId = attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown"
    return when (error) {
        is DomainError.ValidationError ->
            respond(
                HttpStatusCode.BadRequest,
                buildJsonObject {
                    put("errors", buildJsonArray { error.messages.forEach { add(JsonPrimitive(it)) } })
                    put("requestId", requestId)
                },
            )
        is DomainError.BoardNotFound, is DomainError.CardNotFound, is DomainError.ColumnNotFound,
        is DomainError.TenantNotFound, is DomainError.ScenarioNotFound,
        ->
            respond(HttpStatusCode.NotFound, mapOf("error" to error.toString(), "requestId" to requestId))
        is DomainError.PersistenceError ->
            respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error", "requestId" to requestId))
    }
}
