package com.kanbanvision.httpapi.routes

import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Step
import com.kanbanvision.httpapi.adapters.respondWithDomainError
import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import com.kanbanvision.httpapi.dtos.ValidationErrorResponse
import com.kanbanvision.usecases.step.CreateStepUseCase
import com.kanbanvision.usecases.step.GetStepUseCase
import com.kanbanvision.usecases.step.ListStepsByBoardUseCase
import com.kanbanvision.usecases.step.commands.CreateStepCommand
import com.kanbanvision.usecases.step.queries.GetStepQuery
import com.kanbanvision.usecases.step.queries.ListStepsByBoardQuery
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.stepRoutes() {
    val createStep: CreateStepUseCase by inject()
    val getStep: GetStepUseCase by inject()
    val listStepsByBoard: ListStepsByBoardUseCase by inject()

    route("/steps") {
        post(createStepSpec()) { call.handleCreateStep(createStep, getStep) }
        get("/{id}", getStepByIdSpec()) { call.handleGetStep(getStep) }
    }

    route("/boards/{boardId}/steps") {
        get(listStepsByBoardSpec()) { call.handleListSteps(listStepsByBoard) }
    }
}

private fun createStepSpec(): RouteConfig.() -> Unit =
    {
        operationId = "createStep"
        summary = "Cria uma nova etapa em um quadro"
        tags("steps")
        description = "Cria uma nova etapa (step) em um quadro Kanban."
        applyBearerAuthSecurity()
        request {
            body<CreateStepRequest> {
                description = "Dados da etapa a ser criada."
                required = true
            }
        }
        applyCreateStepResponses()
    }

private fun RouteConfig.applyCreateStepResponses() {
    response {
        code(HttpStatusCode.Created) {
            description = "Etapa criada com sucesso."
            body<StepResponse>()
        }
        code(HttpStatusCode.BadRequest) {
            description = "Validação falhou — `errors` lista os campos inválidos e `requestId` identifica a requisição."
            body<ValidationErrorResponse>()
        }
        code(HttpStatusCode.NotFound) {
            description = "Quadro (`boardId`) não encontrado."
            body<DomainErrorResponse>()
        }
        code(HttpStatusCode.InternalServerError) {
            description = "Erro de persistência inesperado."
            body<DomainErrorResponse>()
        }
    }
}

private fun getStepByIdSpec(): RouteConfig.() -> Unit =
    {
        operationId = "getStepById"
        summary = "Retorna uma etapa pelo identificador"
        tags("steps")
        description = "Busca uma etapa pelo seu identificador único."
        applyBearerAuthSecurity()
        request {
            pathParameter<String>("id") {
                description = "UUID da etapa."
                required = true
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "Etapa encontrada."
                body<StepResponse>()
            }
            code(HttpStatusCode.NotFound) {
                description = "Etapa não encontrada para o `id` informado."
                body<DomainErrorResponse>()
            }
            code(HttpStatusCode.InternalServerError) {
                description = "Erro de persistência inesperado."
                body<DomainErrorResponse>()
            }
        }
    }

private fun listStepsByBoardSpec(): RouteConfig.() -> Unit =
    {
        operationId = "listStepsByBoard"
        summary = "Lista todas as etapas de um quadro"
        tags("steps")
        description = "Lista todas as etapas de um quadro."
        applyBearerAuthSecurity()
        request {
            pathParameter<String>("boardId") {
                description = "UUID do quadro."
                required = true
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "Lista de etapas do quadro."
                body<List<StepResponse>>()
            }
            code(HttpStatusCode.NotFound) {
                description = "Quadro (`boardId`) não encontrado."
                body<DomainErrorResponse>()
            }
            code(HttpStatusCode.InternalServerError) {
                description = "Erro de persistência inesperado."
                body<DomainErrorResponse>()
            }
        }
    }

private suspend fun ApplicationCall.handleCreateStep(
    createStep: CreateStepUseCase,
    getStep: GetStepUseCase,
) {
    val request = receive<CreateStepRequest>()
    either<DomainError, StepResponse> {
        val stepId =
            createStep
                .execute(
                    CreateStepCommand(
                        boardId = request.boardId,
                        name = request.name,
                        requiredAbility = request.requiredAbility,
                    ),
                ).bind()
        val step = getStep.execute(GetStepQuery(id = stepId)).bind()
        step.toResponse()
    }.fold(
        ifLeft = { error -> respondWithDomainError(error) },
        ifRight = { response -> respond(HttpStatusCode.Created, response) },
    )
}

private suspend fun ApplicationCall.handleGetStep(getStep: GetStepUseCase) {
    val id = parameters["id"] ?: return respondWithDomainError(DomainError.ValidationError("Missing step id"))
    getStep.execute(GetStepQuery(id = id)).fold(
        ifLeft = { error -> respondWithDomainError(error) },
        ifRight = { step -> respond(step.toResponse()) },
    )
}

private suspend fun ApplicationCall.handleListSteps(listStepsByBoard: ListStepsByBoardUseCase) {
    val boardId = parameters["boardId"] ?: return respondWithDomainError(DomainError.ValidationError("Missing board id"))
    listStepsByBoard.execute(ListStepsByBoardQuery(boardId = boardId)).fold(
        ifLeft = { error -> respondWithDomainError(error) },
        ifRight = { steps -> respond(steps.map { it.toResponse() }) },
    )
}

private fun Step.toResponse(): StepResponse =
    StepResponse(
        id = id,
        boardId = boardId,
        name = name,
        position = position,
        requiredAbility = requiredAbility,
    )

@Serializable
data class CreateStepRequest(
    val boardId: String,
    val name: String,
    val requiredAbility: AbilityName,
)

@Serializable
data class StepResponse(
    val id: String,
    val boardId: String,
    val name: String,
    val position: Int,
    val requiredAbility: AbilityName,
)
