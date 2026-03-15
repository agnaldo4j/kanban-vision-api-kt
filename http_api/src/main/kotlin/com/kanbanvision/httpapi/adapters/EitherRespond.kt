package com.kanbanvision.httpapi.adapters

import com.kanbanvision.domain.errors.DomainError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun ApplicationCall.respondWithDomainError(error: DomainError): Unit =
    when (error) {
        is DomainError.ValidationError ->
            respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
        is DomainError.BoardNotFound, is DomainError.CardNotFound, is DomainError.ColumnNotFound ->
            respond(HttpStatusCode.NotFound, mapOf("error" to error.toString()))
        is DomainError.PersistenceError ->
            respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
    }
