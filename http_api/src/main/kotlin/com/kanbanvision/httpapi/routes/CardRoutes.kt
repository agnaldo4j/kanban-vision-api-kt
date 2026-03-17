package com.kanbanvision.httpapi.routes

import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.httpapi.adapters.respondWithDomainError
import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import com.kanbanvision.httpapi.dtos.ValidationErrorResponse
import com.kanbanvision.usecases.card.CreateCardUseCase
import com.kanbanvision.usecases.card.GetCardUseCase
import com.kanbanvision.usecases.card.MoveCardUseCase
import com.kanbanvision.usecases.card.commands.CreateCardCommand
import com.kanbanvision.usecases.card.commands.MoveCardCommand
import com.kanbanvision.usecases.card.queries.GetCardQuery
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.cardRoutes() {
    val createCard: CreateCardUseCase by inject()
    val moveCard: MoveCardUseCase by inject()
    val getCard: GetCardUseCase by inject()

    route("/cards") {
        post(createCardSpec()) { call.handleCreateCard(createCard, getCard) }

        get("/{id}", getCardByIdSpec()) { call.handleGetCard(getCard) }

        patch("/{id}/move", moveCardSpec()) { call.handleMoveCard(moveCard, getCard) }
    }
}

private fun getCardByIdSpec(): RouteConfig.() -> Unit =
    {
        operationId = "getCardById"
        summary = "Retorna um cartão pelo identificador"
        tags("cards")
        description = "Busca um cartão pelo seu identificador único."
        applyBearerAuthSecurity()
        request {
            pathParameter<String>("id") {
                description = "UUID do cartão."
                required = true
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "Cartão encontrado."
                body<CardResponse>()
            }
            code(HttpStatusCode.NotFound) {
                description = "Cartão não encontrado para o `id` informado."
                body<DomainErrorResponse>()
            }
            code(HttpStatusCode.InternalServerError) {
                description = "Erro de persistência inesperado."
                body<DomainErrorResponse>()
            }
        }
    }

private fun createCardSpec(): RouteConfig.() -> Unit =
    {
        operationId = "createCard"
        summary = "Cria um novo cartão em uma coluna"
        tags("cards")
        description = "Cria um novo cartão em uma coluna do quadro."
        applyBearerAuthSecurity()
        request {
            body<CreateCardRequest> {
                description = "Dados do cartão a ser criado."
                required = true
            }
        }
        applyCreateCardResponses()
    }

private fun RouteConfig.applyCreateCardResponses() {
    response {
        code(HttpStatusCode.Created) {
            description = "Cartão criado com sucesso."
            body<CardResponse>()
        }
        code(HttpStatusCode.BadRequest) {
            description = "Validação falhou — `errors` lista os campos inválidos e `requestId` identifica a requisição."
            body<ValidationErrorResponse>()
        }
        code(HttpStatusCode.NotFound) {
            description = "Coluna (`columnId`) não encontrada."
            body<DomainErrorResponse>()
        }
        code(HttpStatusCode.InternalServerError) {
            description = "Erro de persistência inesperado."
            body<DomainErrorResponse>()
        }
    }
}

private fun moveCardSpec(): RouteConfig.() -> Unit =
    {
        operationId = "moveCard"
        summary = "Move um cartão para outra coluna ou posição"
        tags("cards")
        description = "Move um cartão para outra coluna ou posição dentro do quadro."
        applyBearerAuthSecurity()
        request {
            pathParameter<String>("id") {
                description = "UUID do cartão a ser movido."
                required = true
            }
            body<MoveCardRequest> {
                description = "Coluna destino e nova posição do cartão."
                required = true
            }
        }
        applyMoveCardResponses()
    }

private fun RouteConfig.applyMoveCardResponses() {
    response {
        code(HttpStatusCode.OK) {
            description = "Cartão movido com sucesso."
            body<CardResponse>()
        }
        code(HttpStatusCode.NotFound) {
            description = "Cartão ou coluna destino não encontrado."
            body<DomainErrorResponse>()
        }
        code(HttpStatusCode.BadRequest) {
            description = "Validação falhou — `errors` lista os campos inválidos e `requestId` identifica a requisição."
            body<ValidationErrorResponse>()
        }
        code(HttpStatusCode.InternalServerError) {
            description = "Erro de persistência inesperado."
            body<DomainErrorResponse>()
        }
    }
}

private suspend fun ApplicationCall.handleGetCard(getCard: GetCardUseCase) {
    val id =
        parameters["id"]
            ?: return respondWithDomainError(DomainError.ValidationError("Missing card id"))
    getCard.execute(GetCardQuery(id = id)).fold(
        ifLeft = { error -> respondWithDomainError(error) },
        ifRight = { card ->
            respond(CardResponse(card.id.value, card.columnId.value, card.title, card.description, card.position))
        },
    )
}

private suspend fun ApplicationCall.handleCreateCard(
    createCard: CreateCardUseCase,
    getCard: GetCardUseCase,
) {
    val request = receive<CreateCardRequest>()
    either<DomainError, CardResponse> {
        val cardId =
            createCard
                .execute(
                    CreateCardCommand(
                        columnId = request.columnId,
                        title = request.title,
                        description = request.description,
                    ),
                ).bind()
        val card = getCard.execute(GetCardQuery(id = cardId.value)).bind()
        CardResponse(card.id.value, card.columnId.value, card.title, card.description, card.position)
    }.fold(
        ifLeft = { error -> respondWithDomainError(error) },
        ifRight = { response -> respond(HttpStatusCode.Created, response) },
    )
}

private suspend fun ApplicationCall.handleMoveCard(
    moveCard: MoveCardUseCase,
    getCard: GetCardUseCase,
) {
    val id =
        parameters["id"]
            ?: return respondWithDomainError(DomainError.ValidationError("Missing card id"))
    val request = receive<MoveCardRequest>()
    either<DomainError, CardResponse> {
        moveCard
            .execute(
                MoveCardCommand(
                    cardId = id,
                    targetColumnId = request.columnId,
                    newPosition = request.position,
                ),
            ).bind()
        val card = getCard.execute(GetCardQuery(id = id)).bind()
        CardResponse(card.id.value, card.columnId.value, card.title, card.description, card.position)
    }.fold(
        ifLeft = { error -> respondWithDomainError(error) },
        ifRight = { response -> respond(response) },
    )
}

@Serializable
data class CreateCardRequest(
    val columnId: String,
    val title: String,
    val description: String = "",
)

@Serializable
data class MoveCardRequest(
    val columnId: String,
    val position: Int,
)

@Serializable
data class CardResponse(
    val id: String,
    val columnId: String,
    val title: String,
    val description: String,
    val position: Int,
)
