package com.kanbanvision.httpapi.routes

import com.kanbanvision.usecases.board.CreateBoardUseCase
import com.kanbanvision.usecases.board.GetBoardUseCase
import com.kanbanvision.usecases.board.commands.CreateBoardCommand
import com.kanbanvision.usecases.board.queries.GetBoardQuery
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.boardRoutes() {
    val createBoard: CreateBoardUseCase by inject()
    val getBoard: GetBoardUseCase by inject()

    route("/boards") {
        post {
            val request = call.receive<CreateBoardRequest>()
            val boardId = createBoard.execute(CreateBoardCommand(name = request.name))
            val board = getBoard.execute(GetBoardQuery(id = boardId.value))
            call.respond(HttpStatusCode.Created, BoardResponse(board.id.value, board.name))
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing board id")
            val board = getBoard.execute(GetBoardQuery(id = id))
            call.respond(BoardResponse(board.id.value, board.name))
        }
    }
}

@Serializable
data class CreateBoardRequest(
    val name: String,
)

@Serializable
data class BoardResponse(
    val id: String,
    val name: String,
)
