package com.kanbanvision.httpapi

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.kanbanvision.httpapi.plugins.configureAuthentication
import io.ktor.server.application.Application
import java.util.Date

object JwtTestHelper {
    const val TEST_SECRET = "test-secret-for-unit-tests-only"
    const val TEST_ISSUER = "kanban-vision-test"
    const val TEST_AUDIENCE = "kanban-vision-test-clients"
    const val TEST_REALM = "Test"

    fun generateToken(subject: String = "test-user"): String =
        JWT
            .create()
            .withAudience(TEST_AUDIENCE)
            .withIssuer(TEST_ISSUER)
            .withSubject(subject)
            .withClaim("tenantId", "00000000-0000-0000-0000-000000000001")
            .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000L))
            .sign(Algorithm.HMAC256(TEST_SECRET))

    fun generateExpiredToken(): String =
        JWT
            .create()
            .withAudience(TEST_AUDIENCE)
            .withIssuer(TEST_ISSUER)
            .withSubject("expired-user")
            .withExpiresAt(Date(System.currentTimeMillis() - 1_000L))
            .sign(Algorithm.HMAC256(TEST_SECRET))
}

fun Application.configureTestAuthentication() {
    configureAuthentication(
        JwtTestHelper.TEST_SECRET,
        JwtTestHelper.TEST_ISSUER,
        JwtTestHelper.TEST_AUDIENCE,
        JwtTestHelper.TEST_REALM,
    )
}
