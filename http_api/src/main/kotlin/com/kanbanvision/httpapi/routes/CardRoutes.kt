package com.kanbanvision.httpapi.routes

import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.card.CreateCardUseCase
import com.kanbanvision.usecases.card.MoveCardUseCase
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.cardRoutes() {
    val createCard: CreateCardUseCase by inject()
    val moveCard: MoveCardUseCase by inject()

    route("/cards") {
        post {
            val request = call.receive<CreateCardRequest>()
            val card = createCard.execute(ColumnId(request.columnId), request.title, request.description)
            call.respond(HttpStatusCode.Created, CardResponse(card.id.value, card.columnId.value, card.title, card.description, card.position))
        }

        patch("/{id}/move") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing card id")
            val request = call.receive<MoveCardRequest>()
            val card = moveCard.execute(CardId(id), ColumnId(request.columnId), request.position)
            call.respond(CardResponse(card.id.value, card.columnId.value, card.title, card.description, card.position))
        }
    }
}

@Serializable
data class CreateCardRequest(val columnId: String, val title: String, val description: String = "")

@Serializable
data class MoveCardRequest(val columnId: String, val position: Int)

@Serializable
data class CardResponse(val id: String, val columnId: String, val title: String, val description: String, val position: Int)
