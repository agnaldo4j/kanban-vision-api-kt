package com.kanbanvision.httpapi.routes

import com.kanbanvision.usecases.card.CreateCardUseCase
import com.kanbanvision.usecases.card.GetCardUseCase
import com.kanbanvision.usecases.card.MoveCardUseCase
import com.kanbanvision.usecases.card.commands.CreateCardCommand
import com.kanbanvision.usecases.card.commands.MoveCardCommand
import com.kanbanvision.usecases.card.queries.GetCardQuery
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.cardRoutes() {
    val createCard: CreateCardUseCase by inject()
    val moveCard: MoveCardUseCase by inject()
    val getCard: GetCardUseCase by inject()

    route("/cards") {
        post { call.handleCreateCard(createCard, getCard) }
        patch("/{id}/move") { call.handleMoveCard(moveCard, getCard) }
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
