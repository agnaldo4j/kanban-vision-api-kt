package com.kanbanvision.httpapi.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.Date

private val log = LoggerFactory.getLogger("AuthRoutes")

fun Route.authRoutes(
    secret: String,
    issuer: String,
    audience: String,
    ttlMs: Long,
) {
    route("/auth") {
        post("/token", issueTokenSpec()) {
            val request = call.receive<IssueTokenRequest>()
            val token =
                JWT
                    .create()
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .withSubject(request.subject)
                    .withClaim("tenantId", request.tenantId)
                    .withExpiresAt(Date(System.currentTimeMillis() + ttlMs))
                    .sign(Algorithm.HMAC256(secret))
            log.warn("DEV MODE: issued JWT for subject='{}' tenantId='{}'", request.subject, request.tenantId)
            call.respond(HttpStatusCode.OK, TokenResponse(token = token))
        }
    }
}

private fun issueTokenSpec(): RouteConfig.() -> Unit =
    {
        operationId = "issueDevToken"
        summary = "Emite um JWT para desenvolvimento"
        tags("auth")
        description = "Endpoint disponível apenas com JWT_DEV_MODE=true. Gera um token JWT de desenvolvimento."
        request {
            body<IssueTokenRequest> {
                description = "Sujeito e tenantId para o token."
                required = true
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "Token JWT emitido com sucesso."
                body<TokenResponse>()
            }
        }
    }

@Serializable
data class IssueTokenRequest(
    val subject: String,
    val tenantId: String,
)

@Serializable
data class TokenResponse(
    val token: String,
)
