package com.kanbanvision.httpapi.routes

import com.kanbanvision.usecases.card.CreateCardUseCase
import com.kanbanvision.usecases.card.GetCardUseCase
import com.kanbanvision.usecases.card.MoveCardUseCase
import com.kanbanvision.usecases.card.commands.CreateCardCommand
import com.kanbanvision.usecases.card.commands.MoveCardCommand
import com.kanbanvision.usecases.card.queries.GetCardQuery
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
        post({
            tags("cards")
            description = "Cria um novo cartão em uma coluna do quadro."
            request {
                body<CreateCardRequest> {
                    description = "Dados do cartão a ser criado."
                    required = true
                }
            }
            response {
                code(HttpStatusCode.Created) {
                    description = "Cartão criado com sucesso."
                    body<CardResponse>()
                }
                code(HttpStatusCode.BadRequest) {
                    description = "Dados do cartão inválidos."
                }
            }
        }) { call.handleCreateCard(createCard, getCard) }

        patch("/{id}/move", {
            tags("cards")
            description = "Move um cartão para outra coluna ou posição dentro do quadro."
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
            response {
                code(HttpStatusCode.OK) {
                    description = "Cartão movido com sucesso."
                    body<CardResponse>()
                }
                code(HttpStatusCode.NotFound) {
                    description = "Cartão não encontrado."
                }
                code(HttpStatusCode.BadRequest) {
                    description = "Dados de movimentação inválidos."
                }
            }
        }) { call.handleMoveCard(moveCard, getCard) }
    }
}

private suspend fun ApplicationCall.handleCreateCard(
    createCard: CreateCardUseCase,
    getCard: GetCardUseCase,
) {
    val request = receive<CreateCardRequest>()
    val cardId =
        createCard.execute(
            CreateCardCommand(
                columnId = request.columnId,
                title = request.title,
                description = request.description,
            ),
        )
    val card = getCard.execute(GetCardQuery(id = cardId.value))
    respond(
        HttpStatusCode.Created,
        CardResponse(card.id.value, card.columnId.value, card.title, card.description, card.position),
    )
}

private suspend fun ApplicationCall.handleMoveCard(
    moveCard: MoveCardUseCase,
    getCard: GetCardUseCase,
) {
    val id = parameters["id"] ?: throw IllegalArgumentException("Missing card id")
    val request = receive<MoveCardRequest>()
    moveCard.execute(
        MoveCardCommand(
            cardId = id,
            targetColumnId = request.columnId,
            newPosition = request.position,
        ),
    )
    val card = getCard.execute(GetCardQuery(id = id))
    respond(CardResponse(card.id.value, card.columnId.value, card.title, card.description, card.position))
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
