package com.kanbanvision.httpapi.routes

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.decision.Decision
import com.kanbanvision.domain.model.decision.DecisionId
import com.kanbanvision.domain.model.decision.DecisionType
import com.kanbanvision.httpapi.adapters.respondWithDomainError
import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import com.kanbanvision.httpapi.dtos.ValidationErrorResponse
import com.kanbanvision.httpapi.metrics.DomainMetrics
import com.kanbanvision.httpapi.plugins.withSpan
import com.kanbanvision.usecases.scenario.CreateScenarioUseCase
import com.kanbanvision.usecases.scenario.GetDailySnapshotUseCase
import com.kanbanvision.usecases.scenario.GetScenarioUseCase
import com.kanbanvision.usecases.scenario.RunDayUseCase
import com.kanbanvision.usecases.scenario.commands.CreateScenarioCommand
import com.kanbanvision.usecases.scenario.commands.RunDayCommand
import com.kanbanvision.usecases.scenario.queries.GetDailySnapshotQuery
import com.kanbanvision.usecases.scenario.queries.GetScenarioQuery
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

fun Route.scenarioRoutes() {
    val createScenario: CreateScenarioUseCase by inject()
    val getScenario: GetScenarioUseCase by inject()
    val runDay: RunDayUseCase by inject()
    val getDailySnapshot: GetDailySnapshotUseCase by inject()
    val domainMetrics: DomainMetrics by inject()

    route("/scenarios") {
        post(createScenarioSpec()) { call.handleCreateScenario(createScenario) }

        route("/{scenarioId}") {
            get(getScenarioSpec()) { call.handleGetScenario(getScenario) }

            post("/run", runDaySpec()) { call.handleRunDay(runDay, domainMetrics) }

            get("/days/{day}/snapshot", getDailySnapshotSpec()) { call.handleGetDailySnapshot(getDailySnapshot) }
        }
    }
}

private fun createScenarioSpec(): RouteConfig.() -> Unit =
    {
        operationId = "createScenario"
        summary = "Cria um cenário de simulação Kanban"
        tags("scenarios")
        description = "Cria um novo cenário de simulação Kanban para um tenant."
        applyBearerAuthSecurity()
        request {
            body<CreateScenarioRequest> {
                description = "Configuração do cenário: tenant, WIP limit, tamanho do time e semente aleatória."
                required = true
            }
        }
        applyCreateScenarioResponses()
    }

private fun RouteConfig.applyCreateScenarioResponses() {
    response {
        code(HttpStatusCode.Created) {
            description = "Cenário criado com sucesso."
            body<ScenarioCreatedResponse>()
        }
        code(HttpStatusCode.BadRequest) {
            description = "Validação falhou — `errors` lista os campos inválidos e `requestId` identifica a requisição."
            body<ValidationErrorResponse>()
        }
        code(HttpStatusCode.NotFound) {
            description = "Tenant não encontrado."
            body<DomainErrorResponse>()
        }
        code(HttpStatusCode.InternalServerError) {
            description = "Erro de persistência inesperado."
            body<DomainErrorResponse>()
        }
    }
}

private fun getScenarioSpec(): RouteConfig.() -> Unit =
    {
        operationId = "getScenario"
        summary = "Retorna um cenário pelo identificador"
        tags("scenarios")
        description = "Retorna o cenário e o estado atual da simulação pelo seu identificador."
        applyBearerAuthSecurity()
        request {
            pathParameter<String>("scenarioId") {
                description = "UUID do cenário."
                required = true
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "Cenário encontrado."
                body<ScenarioResponse>()
            }
            code(HttpStatusCode.NotFound) {
                description = "Cenário não encontrado para o `scenarioId` informado."
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
        tags("scenarios")
        description = "Executa um dia de simulação, aplicando as decisões fornecidas pelo usuário."
        applyBearerAuthSecurity()
        request {
            pathParameter<String>("scenarioId") {
                description = "UUID do cenário."
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
            description = "O dia já foi executado anteriormente. Verificar o dia atual via `GET /api/v1/scenarios/{scenarioId}`."
            body<DomainErrorResponse>()
        }
        code(HttpStatusCode.NotFound) {
            description = "Cenário não encontrado."
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
        tags("scenarios")
        description = "Retorna o snapshot de métricas e movimentações de um dia específico da simulação."
        applyBearerAuthSecurity()
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
        applyGetDailySnapshotResponses()
    }

private fun RouteConfig.applyGetDailySnapshotResponses() {
    response {
        code(HttpStatusCode.OK) {
            description = "Snapshot encontrado."
            body<DailySnapshotResponse>()
        }
        code(HttpStatusCode.NotFound) {
            description = "Cenário ou snapshot não encontrado — `scenarioId` inválido ou dia ainda não simulado."
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

private suspend fun ApplicationCall.handleCreateScenario(createScenario: CreateScenarioUseCase) {
    val request = receive<CreateScenarioRequest>()
    createScenario
        .execute(
            CreateScenarioCommand(
                tenantId = request.tenantId,
                wipLimit = request.wipLimit,
                teamSize = request.teamSize,
                seedValue = request.seedValue,
            ),
        ).fold(
            ifLeft = { error -> respondWithDomainError(error) },
            ifRight = { id ->
                MDC.putCloseable("scenarioId", id.value).use {
                    respond(HttpStatusCode.Created, ScenarioCreatedResponse(scenarioId = id.value))
                }
            },
        )
}

private suspend fun ApplicationCall.handleGetScenario(getScenario: GetScenarioUseCase) {
    val scenarioId =
        parameters["scenarioId"]
            ?: return respondWithDomainError(DomainError.ValidationError("Missing scenario id"))
    MDC.putCloseable("scenarioId", scenarioId).use {
        getScenario.execute(GetScenarioQuery(scenarioId = scenarioId)).fold(
            ifLeft = { error -> respondWithDomainError(error) },
            ifRight = { result ->
                respond(
                    ScenarioResponse(
                        scenarioId = result.scenario.id.value,
                        tenantId = result.scenario.tenantId.value,
                        wipLimit = result.scenario.config.wipLimit,
                        teamSize = result.scenario.config.teamSize,
                        seedValue = result.scenario.config.seedValue,
                        state =
                            SimulationStateResponse(
                                currentDay = result.state.currentDay.value,
                                wipLimit = result.state.policySet.wipLimit,
                                teamSize = result.scenario.config.teamSize,
                                itemCount = result.state.items.size,
                            ),
                    ),
                )
            },
        )
    }
}

private suspend fun ApplicationCall.handleRunDay(
    runDay: RunDayUseCase,
    domainMetrics: DomainMetrics,
) {
    val scenarioId =
        parameters["scenarioId"]
            ?: return respondWithDomainError(DomainError.ValidationError("Missing scenario id"))
    MDC.putCloseable("scenarioId", scenarioId).use {
        val request = receive<RunDayRequest>()
        val decisions =
            request.decisions.map { d ->
                val type =
                    runCatching { DecisionType.valueOf(d.type.uppercase(Locale.ROOT)) }.getOrElse {
                        return@use respondWithDomainError(DomainError.ValidationError("Unknown decision type: ${d.type}"))
                    }
                Decision(id = DecisionId.generate(), type = type, payload = d.payload)
            }
        withSpan("simulation.run_day") {
            runDay.execute(RunDayCommand(scenarioId = scenarioId, decisions = decisions)).fold(
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
            getDailySnapshot.execute(GetDailySnapshotQuery(scenarioId = scenarioId, day = day)).fold(
                ifLeft = { error -> respondWithDomainError(error) },
                ifRight = { snapshot -> respond(snapshot.toResponse()) },
            )
        }
    }
}
