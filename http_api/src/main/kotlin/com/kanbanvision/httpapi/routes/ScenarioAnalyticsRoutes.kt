package com.kanbanvision.httpapi.routes

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.movement.Movement
import com.kanbanvision.httpapi.adapters.respondWithDomainError
import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import com.kanbanvision.httpapi.dtos.ValidationErrorResponse
import com.kanbanvision.usecases.scenario.DailyMetrics
import com.kanbanvision.usecases.scenario.GetFlowMetricsRangeUseCase
import com.kanbanvision.usecases.scenario.GetMovementsByDayUseCase
import com.kanbanvision.usecases.scenario.queries.GetFlowMetricsRangeQuery
import com.kanbanvision.usecases.scenario.queries.GetMovementsByDayQuery
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import org.slf4j.MDC

fun Route.scenarioAnalyticsRoutes() {
    val getMovementsByDay: GetMovementsByDayUseCase by inject()
    val getFlowMetricsRange: GetFlowMetricsRangeUseCase by inject()

    route("/scenarios/{scenarioId}") {
        get("/days/{day}/movements", getMovementsByDaySpec()) { call.handleGetMovementsByDay(getMovementsByDay) }
        get("/metrics", getFlowMetricsRangeSpec()) { call.handleGetFlowMetricsRange(getFlowMetricsRange) }
    }
}

private fun getMovementsByDaySpec(): RouteConfig.() -> Unit =
    {
        operationId = "getMovementsByDay"
        summary = "Retorna movimentos de itens de um dia da simulação"
        tags("scenarios")
        description = "Retorna os movimentos de itens ocorridos em um dia específico da simulação."
        request {
            pathParameter<String>("scenarioId") {
                description = "UUID do cenário."
                required = true
            }
            pathParameter<Int>("day") {
                description = "Número do dia da simulação (começa em 1)."
                required = true
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "Lista de movimentos do dia."
                body<List<MovementResponse>>()
            }
            code(HttpStatusCode.NotFound) {
                description = "Snapshot não encontrado para o `day` informado."
                body<DomainErrorResponse>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "Parâmetro `day` inválido — deve ser inteiro ≥ 1."
                body<ValidationErrorResponse>()
            }
            code(HttpStatusCode.InternalServerError) {
                description = "Erro de persistência inesperado."
                body<DomainErrorResponse>()
            }
        }
    }

private fun getFlowMetricsRangeSpec(): RouteConfig.() -> Unit =
    {
        operationId = "getFlowMetricsRange"
        summary = "Retorna métricas de fluxo Kanban por intervalo de dias"
        tags("scenarios")
        description = "Retorna as métricas de fluxo agregadas por dia em um intervalo da simulação."
        request {
            pathParameter<String>("scenarioId") {
                description = "UUID do cenário."
                required = true
            }
            queryParameter<Int>("fromDay") {
                description = "Primeiro dia do intervalo (inclusivo, começa em 1)."
                required = true
            }
            queryParameter<Int>("toDay") {
                description = "Último dia do intervalo (inclusivo, deve ser ≥ fromDay)."
                required = true
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "Lista de métricas diárias no intervalo solicitado."
                body<List<DailyMetricsResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "Parâmetros `fromDay`/`toDay` inválidos — devem ser inteiros com `toDay ≥ fromDay`."
                body<ValidationErrorResponse>()
            }
            code(HttpStatusCode.InternalServerError) {
                description = "Erro de persistência inesperado."
                body<DomainErrorResponse>()
            }
        }
    }

private suspend fun ApplicationCall.handleGetMovementsByDay(getMovementsByDay: GetMovementsByDayUseCase) {
    val scenarioId =
        parameters["scenarioId"]
            ?: return respondWithDomainError(DomainError.ValidationError("Missing scenario id"))
    val dayStr =
        parameters["day"]
            ?: return respondWithDomainError(DomainError.ValidationError("Missing day"))
    val day =
        dayStr.toIntOrNull()
            ?: return respondWithDomainError(DomainError.ValidationError("Day must be an integer"))
    MDC.putCloseable("scenarioId", scenarioId).use {
        MDC.putCloseable("day", day.toString()).use {
            getMovementsByDay.execute(GetMovementsByDayQuery(scenarioId = scenarioId, day = day)).fold(
                ifLeft = { error -> respondWithDomainError(error) },
                ifRight = { movements -> respond(movements.map { it.toResponse() }) },
            )
        }
    }
}

private suspend fun ApplicationCall.handleGetFlowMetricsRange(getFlowMetricsRange: GetFlowMetricsRangeUseCase) {
    val scenarioId =
        parameters["scenarioId"]
            ?: return respondWithDomainError(DomainError.ValidationError("Missing scenario id"))
    val fromDayResult = parseIntQueryParam("fromDay")
    val toDayResult = parseIntQueryParam("toDay")
    if (fromDayResult.isLeft()) return respondWithDomainError(fromDayResult.leftOrNull()!!)
    if (toDayResult.isLeft()) return respondWithDomainError(toDayResult.leftOrNull()!!)
    val fromDay = fromDayResult.getOrNull()!!
    val toDay = toDayResult.getOrNull()!!
    MDC.putCloseable("scenarioId", scenarioId).use {
        getFlowMetricsRange
            .execute(
                GetFlowMetricsRangeQuery(scenarioId = scenarioId, fromDay = fromDay, toDay = toDay),
            ).fold(
                ifLeft = { error -> respondWithDomainError(error) },
                ifRight = { dailyMetrics -> respond(dailyMetrics.map { it.toResponse() }) },
            )
    }
}

private fun ApplicationCall.parseIntQueryParam(name: String): Either<DomainError.ValidationError, Int> {
    val raw = request.queryParameters[name] ?: return DomainError.ValidationError("Missing $name").left()
    return raw.toIntOrNull()?.right() ?: DomainError.ValidationError("$name must be an integer").left()
}

private fun Movement.toResponse() = MovementResponse(type = type.name, workItemId = workItemId.value, day = day.value, reason = reason)

private fun DailyMetrics.toResponse() =
    DailyMetricsResponse(
        day = day,
        throughput = metrics.throughput,
        wipCount = metrics.wipCount,
        blockedCount = metrics.blockedCount,
        avgAgingDays = metrics.avgAgingDays,
    )

@Serializable
data class DailyMetricsResponse(
    val day: Int,
    val throughput: Int,
    val wipCount: Int,
    val blockedCount: Int,
    val avgAgingDays: Double,
)
