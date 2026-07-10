package com.kanbanvision.httpapi.routes

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.httpapi.adapters.callerOrganizationId
import com.kanbanvision.httpapi.adapters.requiredPathParam
import com.kanbanvision.httpapi.adapters.respondWithDomainError
import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import com.kanbanvision.httpapi.dtos.ValidationErrorResponse
import com.kanbanvision.usecases.simulation.GetSimulationCfdUseCase
import com.kanbanvision.usecases.simulation.GetSimulationDaysUseCase
import com.kanbanvision.usecases.simulation.ListSimulationsUseCase
import com.kanbanvision.usecases.simulation.queries.GetSimulationCfdQuery
import com.kanbanvision.usecases.simulation.queries.GetSimulationDaysQuery
import com.kanbanvision.usecases.simulation.queries.ListSimulationsQuery
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import org.slf4j.MDC

private const val DEFAULT_PAGE = 1
private const val DEFAULT_PAGE_SIZE = 20

internal fun listSimulationsSpec(): RouteConfig.() -> Unit =
    {
        operationId = "listSimulations"
        summary = "Lista simulações paginadas da organização do chamador"
        tags("simulations")
        description = "Retorna a lista paginada de simulações da organização do chamador (derivada do claim " +
            "organizationId do JWT). O escopo de tenancy vem sempre do token, nunca de parâmetro de query."
        applyBearerAuthSecurity()
        request {
            queryParameter<Int>("page") {
                description = "Número da página (padrão 1, mínimo 1)."
                required = false
                example("default") { value = DEFAULT_PAGE }
            }
            queryParameter<Int>("size") {
                description = "Tamanho da página (padrão 20, máximo 100)."
                required = false
                example("default") { value = DEFAULT_PAGE_SIZE }
            }
        }
        applyListSimulationsResponses()
    }

private fun RouteConfig.applyListSimulationsResponses() {
    response {
        code(HttpStatusCode.OK) {
            description = "Lista de simulações retornada com sucesso."
            body<SimulationListResponse> {
                example("default") { value = SimulationListResponse.example }
            }
            header<String>("X-Request-ID") {
                description = "Correlation ID para rastreamento de logs."
            }
        }
        code(HttpStatusCode.BadRequest) {
            description = "Parâmetros de paginação inválidos."
            body<ValidationErrorResponse>()
            header<String>("X-Request-ID") { description = "Correlation ID para rastreamento de logs." }
        }
        code(HttpStatusCode.InternalServerError) {
            description = "Erro de persistência inesperado."
            body<DomainErrorResponse>()
            header<String>("X-Request-ID") { description = "Correlation ID para rastreamento de logs." }
        }
    }
}

internal fun getSimulationDaysSpec(): RouteConfig.() -> Unit =
    {
        operationId = "getSimulationDays"
        summary = "Retorna série temporal de métricas da simulação"
        tags("simulations")
        description = "Retorna todos os snapshots diários executados de uma simulação."
        applyBearerAuthSecurity()
        request {
            pathParameter<String>("simulationId") {
                description = "UUID da simulação."
                required = true
                example("default") { value = "550e8400-e29b-41d4-a716-446655440001" }
            }
        }
        applyCrossTenantForbiddenResponse()
        applySimulationDaysResponses()
    }

internal fun getSimulationCfdSpec(): RouteConfig.() -> Unit =
    {
        operationId = "getSimulationCfd"
        summary = "Retorna dados para CFD (Cumulative Flow Diagram)"
        tags("simulations")
        description = "Retorna a acumulação de throughput e WIP por dia para geração de CFD."
        applyBearerAuthSecurity()
        request {
            pathParameter<String>("simulationId") {
                description = "UUID da simulação."
                required = true
                example("default") { value = "550e8400-e29b-41d4-a716-446655440001" }
            }
        }
        applyCrossTenantForbiddenResponse()
        applySimulationCfdResponses()
    }

private fun RouteConfig.applySimulationDaysResponses() {
    response {
        code(HttpStatusCode.OK) {
            description = "Série temporal de métricas retornada com sucesso."
            body<SimulationDaysResponse> {
                example("default") { value = SimulationDaysResponse.example }
            }
            header<String>("X-Request-ID") {
                description = "Correlation ID para rastreamento de logs."
            }
        }
        code(HttpStatusCode.BadRequest) {
            description = "Parâmetro `simulationId` inválido."
            body<ValidationErrorResponse>()
            header<String>("X-Request-ID") { description = "Correlation ID para rastreamento de logs." }
        }
        code(HttpStatusCode.InternalServerError) {
            description = "Erro de persistência inesperado."
            body<DomainErrorResponse>()
            header<String>("X-Request-ID") { description = "Correlation ID para rastreamento de logs." }
        }
    }
}

private fun RouteConfig.applySimulationCfdResponses() {
    response {
        code(HttpStatusCode.OK) {
            description = "Dados CFD retornados com sucesso."
            body<SimulationCfdResponse> {
                example("default") { value = SimulationCfdResponse.example }
            }
            header<String>("X-Request-ID") {
                description = "Correlation ID para rastreamento de logs."
            }
        }
        code(HttpStatusCode.BadRequest) {
            description = "Parâmetro `simulationId` inválido."
            body<ValidationErrorResponse>()
            header<String>("X-Request-ID") { description = "Correlation ID para rastreamento de logs." }
        }
        code(HttpStatusCode.InternalServerError) {
            description = "Erro de persistência inesperado."
            body<DomainErrorResponse>()
            header<String>("X-Request-ID") { description = "Correlation ID para rastreamento de logs." }
        }
    }
}

internal suspend fun ApplicationCall.handleListSimulations(listSimulations: ListSimulationsUseCase) {
    val organizationId = callerOrganizationId() ?: return
    val pageParam = request.queryParameters["page"]
    val page =
        if (pageParam == null) {
            DEFAULT_PAGE
        } else {
            pageParam.toIntOrNull()
                ?: return respondWithDomainError(DomainError.ValidationError("Invalid page parameter"))
        }
    val sizeParam = request.queryParameters["size"]
    val size =
        if (sizeParam == null) {
            DEFAULT_PAGE_SIZE
        } else {
            sizeParam.toIntOrNull()
                ?: return respondWithDomainError(DomainError.ValidationError("Invalid size parameter"))
        }
    listSimulations.execute(ListSimulationsQuery(organizationId = organizationId, page = page, size = size)).fold(
        ifLeft = { error -> respondWithDomainError(error) },
        ifRight = { result -> respond(result.toListResponse()) },
    )
}

internal suspend fun ApplicationCall.handleGetSimulationDays(getSimulationDays: GetSimulationDaysUseCase) {
    val simulationId = requiredPathParam("simulationId", "Missing simulation id") ?: return
    val callerOrganizationId = callerOrganizationId() ?: return
    MDC.putCloseable("simulationId", simulationId).use {
        getSimulationDays
            .execute(GetSimulationDaysQuery(simulationId = simulationId, callerOrganizationId = callerOrganizationId))
            .fold(
                ifLeft = { error -> respondWithDomainError(error) },
                ifRight = { snapshots -> respond(snapshots.toDaysResponse(simulationId)) },
            )
    }
}

internal suspend fun ApplicationCall.handleGetSimulationCfd(getSimulationCfd: GetSimulationCfdUseCase) {
    val simulationId = requiredPathParam("simulationId", "Missing simulation id") ?: return
    val callerOrganizationId = callerOrganizationId() ?: return
    MDC.putCloseable("simulationId", simulationId).use {
        getSimulationCfd
            .execute(GetSimulationCfdQuery(simulationId = simulationId, callerOrganizationId = callerOrganizationId))
            .fold(
                ifLeft = { error -> respondWithDomainError(error) },
                ifRight = { result -> respond(result.toResponse()) },
            )
    }
}
