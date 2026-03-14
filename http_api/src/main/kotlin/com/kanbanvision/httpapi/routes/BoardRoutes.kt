package com.kanbanvision.httpapi.routes

import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.usecases.board.CreateBoardUseCase
import com.kanbanvision.usecases.board.GetBoardUseCase
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.boardRoutes() {
    val createBoard: CreateBoardUseCase by inject()
    val getBoard: GetBoardUseCase by inject()

    route("/boards") {
        post {
            val request = call.receive<CreateBoardRequest>()
            val board = createBoard.execute(request.name)
            call.respond(HttpStatusCode.Created, BoardResponse(board.id.value, board.name))
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing board id")
            val board = getBoard.execute(BoardId(id))
            call.respond(BoardResponse(board.id.value, board.name))
        }
    }
}

@Serializable
data class CreateBoardRequest(val name: String)

@Serializable
data class BoardResponse(val id: String, val name: String)
