package com.kanbanvision.httpapi.routes

import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.httpapi.adapters.respondWithDomainError
import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import com.kanbanvision.httpapi.dtos.ValidationErrorResponse
import com.kanbanvision.usecases.column.CreateColumnUseCase
import com.kanbanvision.usecases.column.GetColumnUseCase
import com.kanbanvision.usecases.column.ListColumnsByBoardUseCase
import com.kanbanvision.usecases.column.commands.CreateColumnCommand
import com.kanbanvision.usecases.column.queries.GetColumnQuery
import com.kanbanvision.usecases.column.queries.ListColumnsByBoardQuery
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

fun Route.columnRoutes() {
    val createColumn: CreateColumnUseCase by inject()
    val getColumn: GetColumnUseCase by inject()
    val listColumnsByBoard: ListColumnsByBoardUseCase by inject()

    route("/columns") {
        post(createColumnSpec()) { call.handleCreateColumn(createColumn, getColumn) }
        get("/{id}", getColumnByIdSpec()) { call.handleGetColumn(getColumn) }
    }

    route("/boards/{boardId}/columns") {
        get(listColumnsByBoardSpec()) { call.handleListColumns(listColumnsByBoard) }
    }
}

private fun createColumnSpec(): RouteConfig.() -> Unit =
    {
        operationId = "createColumn"
        summary = "Cria uma nova coluna em um quadro"
        tags("columns")
        description = "Cria uma nova coluna em um quadro Kanban."
        applyBearerAuthSecurity()
        request {
            body<CreateColumnRequest> {
                description = "Dados da coluna a ser criada."
                required = true
            }
        }
        applyCreateColumnResponses()
    }

private fun RouteConfig.applyCreateColumnResponses() {
    response {
        code(HttpStatusCode.Created) {
            description = "Coluna criada com sucesso."
            body<ColumnResponse>()
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

private fun getColumnByIdSpec(): RouteConfig.() -> Unit =
    {
        operationId = "getColumnById"
        summary = "Retorna uma coluna pelo identificador"
        tags("columns")
        description = "Busca uma coluna pelo seu identificador único."
        applyBearerAuthSecurity()
        request {
            pathParameter<String>("id") {
                description = "UUID da coluna."
                required = true
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "Coluna encontrada."
                body<ColumnResponse>()
            }
            code(HttpStatusCode.NotFound) {
                description = "Coluna não encontrada para o `id` informado."
                body<DomainErrorResponse>()
            }
            code(HttpStatusCode.InternalServerError) {
                description = "Erro de persistência inesperado."
                body<DomainErrorResponse>()
            }
        }
    }

private fun listColumnsByBoardSpec(): RouteConfig.() -> Unit =
    {
        operationId = "listColumnsByBoard"
        summary = "Lista todas as colunas de um quadro"
        tags("columns")
        description = "Lista todas as colunas de um quadro."
        applyBearerAuthSecurity()
        request {
            pathParameter<String>("boardId") {
                description = "UUID do quadro."
                required = true
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "Lista de colunas do quadro."
                body<List<ColumnResponse>>()
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

private suspend fun ApplicationCall.handleCreateColumn(
    createColumn: CreateColumnUseCase,
    getColumn: GetColumnUseCase,
) {
    val request = receive<CreateColumnRequest>()
    either<DomainError, ColumnResponse> {
        val columnId = createColumn.execute(CreateColumnCommand(boardId = request.boardId, name = request.name)).bind()
        val column = getColumn.execute(GetColumnQuery(id = columnId.value)).bind()
        ColumnResponse(column.id.value, column.boardId.value, column.name, column.position)
    }.fold(
        ifLeft = { error -> respondWithDomainError(error) },
        ifRight = { response -> respond(HttpStatusCode.Created, response) },
    )
}

private suspend fun ApplicationCall.handleGetColumn(getColumn: GetColumnUseCase) {
    val id =
        parameters["id"]
            ?: return respondWithDomainError(DomainError.ValidationError("Missing column id"))
    getColumn.execute(GetColumnQuery(id = id)).fold(
        ifLeft = { error -> respondWithDomainError(error) },
        ifRight = { column -> respond(ColumnResponse(column.id.value, column.boardId.value, column.name, column.position)) },
    )
}

private suspend fun ApplicationCall.handleListColumns(listColumnsByBoard: ListColumnsByBoardUseCase) {
    val boardId =
        parameters["boardId"]
            ?: return respondWithDomainError(DomainError.ValidationError("Missing board id"))
    listColumnsByBoard.execute(ListColumnsByBoardQuery(boardId = boardId)).fold(
        ifLeft = { error -> respondWithDomainError(error) },
        ifRight = { columns ->
            respond(columns.map { ColumnResponse(it.id.value, it.boardId.value, it.name, it.position) })
        },
    )
}

@Serializable
data class CreateColumnRequest(
    val boardId: String,
    val name: String,
)

@Serializable
data class ColumnResponse(
    val id: String,
    val boardId: String,
    val name: String,
    val position: Int,
)
