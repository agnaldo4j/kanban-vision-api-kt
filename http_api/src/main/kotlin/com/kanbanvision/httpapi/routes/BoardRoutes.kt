package com.kanbanvision.httpapi.routes

import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.httpapi.adapters.respondWithDomainError
import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import com.kanbanvision.httpapi.dtos.ValidationErrorResponse
import com.kanbanvision.usecases.board.CreateBoardUseCase
import com.kanbanvision.usecases.board.GetBoardUseCase
import com.kanbanvision.usecases.board.commands.CreateBoardCommand
import com.kanbanvision.usecases.board.queries.GetBoardQuery
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.boardRoutes() {
    val createBoard: CreateBoardUseCase by inject()
    val getBoard: GetBoardUseCase by inject()

    route("/boards") {
        post(createBoardSpec()) {
            val request = call.receive<CreateBoardRequest>()
            either<DomainError, BoardResponse> {
                val boardId = createBoard.execute(CreateBoardCommand(name = request.name)).bind()
                val board = getBoard.execute(GetBoardQuery(id = boardId.value)).bind()
                BoardResponse(board.id.value, board.name)
            }.fold(
                ifLeft = { error -> call.respondWithDomainError(error) },
                ifRight = { response -> call.respond(HttpStatusCode.Created, response) },
            )
        }

        get("/{id}", getBoardByIdSpec()) {
            val id =
                call.parameters["id"]
                    ?: return@get call.respondWithDomainError(DomainError.ValidationError(listOf("Missing board id")))
            getBoard.execute(GetBoardQuery(id = id)).fold(
                ifLeft = { error -> call.respondWithDomainError(error) },
                ifRight = { board -> call.respond(BoardResponse(board.id.value, board.name)) },
            )
        }
    }
}

private fun createBoardSpec(): RouteConfig.() -> Unit =
    {
        operationId = "createBoard"
        summary = "Cria um novo quadro Kanban"
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
                description = "Validação falhou — `errors` lista os campos inválidos e `requestId` identifica a requisição."
                body<ValidationErrorResponse>()
            }
            code(HttpStatusCode.InternalServerError) {
                description = "Erro de persistência inesperado."
                body<DomainErrorResponse>()
            }
        }
    }

private fun getBoardByIdSpec(): RouteConfig.() -> Unit =
    {
        operationId = "getBoardById"
        summary = "Retorna um quadro pelo identificador"
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
                description = "Quadro não encontrado para o `id` informado."
                body<DomainErrorResponse>()
            }
            code(HttpStatusCode.InternalServerError) {
                description = "Erro de persistência inesperado."
                body<DomainErrorResponse>()
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
