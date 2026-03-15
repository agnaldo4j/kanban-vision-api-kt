package com.kanbanvision.httpapi.routes

import com.kanbanvision.usecases.board.CreateBoardUseCase
import com.kanbanvision.usecases.board.GetBoardUseCase
import com.kanbanvision.usecases.board.commands.CreateBoardCommand
import com.kanbanvision.usecases.board.queries.GetBoardQuery
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Suppress("LongMethod")
fun Route.boardRoutes() {
    val createBoard: CreateBoardUseCase by inject()
    val getBoard: GetBoardUseCase by inject()

    route("/boards") {
        post({
            tags("boards")
            description = "Cria um novo quadro Kanban."
            request {
                body<CreateBoardRequest> {
                    description = "Nome do quadro a ser criado."
                    required = true
                }
            }
            response {
                code(HttpStatusCode.Created) {
                    description = "Quadro criado com sucesso."
                    body<BoardResponse>()
                }
                code(HttpStatusCode.BadRequest) {
                    description = "Nome do quadro inválido ou em branco."
                }
            }
        }) {
            val request = call.receive<CreateBoardRequest>()
            val boardId = createBoard.execute(CreateBoardCommand(name = request.name))
            val board = getBoard.execute(GetBoardQuery(id = boardId.value))
            call.respond(HttpStatusCode.Created, BoardResponse(board.id.value, board.name))
        }

        get("/{id}", {
            tags("boards")
            description = "Busca um quadro pelo seu identificador único."
            request {
                pathParameter<String>("id") {
                    description = "UUID do quadro."
                    required = true
                }
            }
            response {
                code(HttpStatusCode.OK) {
                    description = "Quadro encontrado."
                    body<BoardResponse>()
                }
                code(HttpStatusCode.NotFound) {
                    description = "Quadro não encontrado."
                }
            }
        }) {
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
