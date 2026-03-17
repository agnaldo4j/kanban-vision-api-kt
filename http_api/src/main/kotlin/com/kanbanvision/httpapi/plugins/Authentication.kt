package com.kanbanvision.httpapi.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Authentication")

private const val DEFAULT_SECRET_PLACEHOLDER = "dev-secret-change-in-production"

fun Application.configureAuthentication() {
    val config = environment.config
    val secret = config.property("jwt.secret").getString()
    if (secret == DEFAULT_SECRET_PLACEHOLDER) {
        log.warn("JWT secret is set to the default placeholder. Set JWT_SECRET env var in production.")
    }
    configureAuthentication(
        secret = secret,
        issuer = config.property("jwt.issuer").getString(),
        audience = config.property("jwt.audience").getString(),
        realm = config.property("jwt.realm").getString(),
    )
}

fun Application.configureAuthentication(
    secret: String,
    issuer: String,
    audience: String,
    realm: String,
) {
    authentication {
        jwt("jwt-auth") {
            this.realm = realm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(secret))
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .build(),
            )
            validate { credential ->
                if (credential.payload.audience.contains(audience)) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                val requestId = call.attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown"
                log.warn("Unauthorized request [requestId={}]", requestId)
                call.respond(
                    HttpStatusCode.Unauthorized,
                    DomainErrorResponse(error = "Token inválido ou ausente", requestId = requestId),
                )
            }
        }
    }
}
