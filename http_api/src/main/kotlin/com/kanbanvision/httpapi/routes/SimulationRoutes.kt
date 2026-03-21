package com.kanbanvision.httpapi.routes

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.DecisionType
import com.kanbanvision.httpapi.adapters.respondWithDomainError
import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import com.kanbanvision.httpapi.dtos.ValidationErrorResponse
import com.kanbanvision.httpapi.metrics.DomainMetrics
import com.kanbanvision.httpapi.plugins.withSpan
import com.kanbanvision.usecases.simulation.CreateSimulationUseCase
import com.kanbanvision.usecases.simulation.GetDailySnapshotUseCase
import com.kanbanvision.usecases.simulation.GetSimulationUseCase
import com.kanbanvision.usecases.simulation.RunDayUseCase
import com.kanbanvision.usecases.simulation.commands.CreateSimulationCommand
import com.kanbanvision.usecases.simulation.commands.RunDayCommand
import com.kanbanvision.usecases.simulation.queries.GetDailySnapshotQuery
import com.kanbanvision.usecases.simulation.queries.GetSimulationQuery
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import org.slf4j.MDC
import java.util.Locale
import java.util.UUID

fun Route.simulationRoutes() {
    val createSimulation: CreateSimulationUseCase by inject()
    val getSimulation: GetSimulationUseCase by inject()
    val runDay: RunDayUseCase by inject()
    val getDailySnapshot: GetDailySnapshotUseCase by inject()
    val domainMetrics: DomainMetrics by inject()

    route("/simulations") {
        post(createSimulationSpec()) { call.handleCreateSimulation(createSimulation) }

        route("/{simulationId}") {
            get(getSimulationSpec()) { call.handleGetSimulation(getSimulation) }

            post("/run", runDaySpec()) { call.handleRunDay(runDay, domainMetrics) }

            get("/days/{day}/snapshot", getDailySnapshotSpec()) { call.handleGetDailySnapshot(getDailySnapshot) }
        }
    }
}

private fun createSimulationSpec(): RouteConfig.() -> Unit =
    {
        operationId = "createSimulation"
        summary = "Cria uma simulação Kanban"
        tags("simulations")
        description = "Cria uma nova simulação Kanban para uma organização."
        applyBearerAuthSecurity()
        request {
            body<CreateSimulationRequest> {
                description = "Configuração da simulação: organização, WIP limit, tamanho do time e semente aleatória."
                required = true
            }
        }
        applyCreateSimulationResponses()
    }

private fun RouteConfig.applyCreateSimulationResponses() {
    response {
        code(HttpStatusCode.Created) {
            description = "Simulação criada com sucesso."
            body<SimulationCreatedResponse>()
        }
        code(HttpStatusCode.BadRequest) {
            description = "Validação falhou — `errors` lista os campos inválidos e `requestId` identifica a requisição."
            body<ValidationErrorResponse>()
        }
        code(HttpStatusCode.NotFound) {
            description = "Organização não encontrada."
            body<DomainErrorResponse>()
        }
        code(HttpStatusCode.InternalServerError) {
            description = "Erro de persistência inesperado."
            body<DomainErrorResponse>()
        }
    }
}

private fun getSimulationSpec(): RouteConfig.() -> Unit =
    {
        operationId = "getSimulation"
        summary = "Retorna uma simulação pelo identificador"
        tags("simulations")
        description = "Retorna a simulação e seu estado atual pelo identificador."
        applyBearerAuthSecurity()
        request {
            pathParameter<String>("simulationId") {
                description = "UUID da simulação."
                required = true
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "Simulação encontrada."
                body<SimulationResponse>()
            }
            code(HttpStatusCode.NotFound) {
                description = "Simulação não encontrada para o `simulationId` informado."
                body<DomainErrorResponse>()
            }
            code(HttpStatusCode.InternalServerError) {
                description = "Erro de persistência inesperado."
                body<DomainErrorResponse>()
            }
        }
    }

private fun runDaySpec(): RouteConfig.() -> Unit =
    {
        operationId = "runDay"
        summary = "Executa um dia de simulação"
        tags("simulations")
        description = "Executa um dia de simulação, aplicando as decisões fornecidas pelo usuário."
        applyBearerAuthSecurity()
        request {
            pathParameter<String>("simulationId") {
                description = "UUID da simulação."
                required = true
            }
            body<RunDayRequest> {
                description = "Lista de decisões a aplicar no dia corrente."
                required = true
            }
        }
        applyRunDayResponses()
    }

private fun RouteConfig.applyRunDayResponses() {
    response {
        code(HttpStatusCode.OK) {
            description = "Dia executado com sucesso. Retorna o snapshot do dia."
            body<DailySnapshotResponse>()
        }
        code(HttpStatusCode.Conflict) {
            description = "O dia já foi executado anteriormente. Verificar o dia atual via `GET /api/v1/simulations/{simulationId}`."
            body<DomainErrorResponse>()
        }
        code(HttpStatusCode.NotFound) {
            description = "Simulação não encontrada."
            body<DomainErrorResponse>()
        }
        code(HttpStatusCode.BadRequest) {
            description = "Tipo de decisão inválido ou dados malformados. `errors` lista os campos inválidos."
            body<ValidationErrorResponse>()
        }
        code(HttpStatusCode.InternalServerError) {
            description = "Erro de persistência inesperado."
            body<DomainErrorResponse>()
        }
    }
}

private fun getDailySnapshotSpec(): RouteConfig.() -> Unit =
    {
        operationId = "getDailySnapshot"
        summary = "Retorna o snapshot de métricas de um dia da simulação"
        tags("simulations")
        description = "Retorna o snapshot de métricas e movimentações de um dia específico da simulação."
        applyBearerAuthSecurity()
        request {
            pathParameter<String>("simulationId") {
                description = "UUID da simulação."
                required = true
            }
            pathParameter<Int>("day") {
                description = "Número do dia da simulação (começa em 1)."
                required = true
            }
        }
        applyGetDailySnapshotResponses()
    }

private fun RouteConfig.applyGetDailySnapshotResponses() {
    response {
        code(HttpStatusCode.OK) {
            description = "Snapshot encontrado."
            body<DailySnapshotResponse>()
        }
        code(HttpStatusCode.NotFound) {
            description = "Simulação ou snapshot não encontrado — `simulationId` inválido ou dia ainda não simulado."
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

private suspend fun ApplicationCall.handleCreateSimulation(createSimulation: CreateSimulationUseCase) {
    val request = receive<CreateSimulationRequest>()
    createSimulation
        .execute(
            CreateSimulationCommand(
                organizationId = request.organizationId,
                wipLimit = request.wipLimit,
                teamSize = request.teamSize,
                seedValue = request.seedValue,
            ),
        ).fold(
            ifLeft = { error -> respondWithDomainError(error) },
            ifRight = { id ->
                MDC.putCloseable("simulationId", id).use {
                    respond(HttpStatusCode.Created, SimulationCreatedResponse(simulationId = id))
                }
            },
        )
}

private suspend fun ApplicationCall.handleGetSimulation(getSimulation: GetSimulationUseCase) {
    val simulationId =
        parameters["simulationId"]
            ?: return respondWithDomainError(DomainError.ValidationError("Missing simulation id"))
    MDC.putCloseable("simulationId", simulationId).use {
        getSimulation.execute(GetSimulationQuery(simulationId = simulationId)).fold(
            ifLeft = { error -> respondWithDomainError(error) },
            ifRight = { result -> respond(result.toSimulationResponse()) },
        )
    }
}

private suspend fun ApplicationCall.handleRunDay(
    runDay: RunDayUseCase,
    domainMetrics: DomainMetrics,
) {
    val simulationId =
        parameters["simulationId"]
            ?: return respondWithDomainError(DomainError.ValidationError("Missing simulation id"))
    MDC.putCloseable("simulationId", simulationId).use {
        val request = receive<RunDayRequest>()
        val decisions =
            request.decisions.map { d ->
                val type =
                    runCatching { DecisionType.valueOf(d.type.uppercase(Locale.ROOT)) }.getOrElse {
                        return@use respondWithDomainError(DomainError.ValidationError("Unknown decision type: ${d.type}"))
                    }
                Decision(id = UUID.randomUUID().toString(), type = type, payload = d.payload)
            }
        withSpan("simulation.run_day") {
            runDay.execute(RunDayCommand(simulationId = simulationId, decisions = decisions)).fold(
                ifLeft = { error -> respondWithDomainError(error) },
                ifRight = { snapshot ->
                    domainMetrics.recordSimulationDayExecuted()
                    MDC.putCloseable("day", snapshot.day.value.toString()).use {
                        respond(snapshot.toResponse())
                    }
                },
            )
        }
    }
}

private suspend fun ApplicationCall.handleGetDailySnapshot(getDailySnapshot: GetDailySnapshotUseCase) {
    val simulationId =
        parameters["simulationId"]
            ?: return respondWithDomainError(DomainError.ValidationError("Missing simulation id"))
    val dayStr =
        parameters["day"]
            ?: return respondWithDomainError(DomainError.ValidationError("Missing day"))
    val day =
        dayStr.toIntOrNull()
            ?: return respondWithDomainError(DomainError.ValidationError("Day must be an integer"))
    MDC.putCloseable("simulationId", simulationId).use {
        MDC.putCloseable("day", day.toString()).use {
            getDailySnapshot.execute(GetDailySnapshotQuery(simulationId = simulationId, day = day)).fold(
                ifLeft = { error -> respondWithDomainError(error) },
                ifRight = { snapshot -> respond(snapshot.toResponse()) },
            )
        }
    }
}
