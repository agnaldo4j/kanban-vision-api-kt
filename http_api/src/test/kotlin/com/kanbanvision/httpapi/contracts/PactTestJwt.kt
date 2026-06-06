package com.kanbanvision.httpapi.contracts

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.kanbanvision.httpapi.TEST_JWT_AUDIENCE
import com.kanbanvision.httpapi.TEST_JWT_ISSUER
import com.kanbanvision.httpapi.TEST_JWT_REALM
import com.kanbanvision.httpapi.TEST_JWT_SECRET
import java.time.Instant
import java.util.Date

internal const val PACT_JWT_SECRET = TEST_JWT_SECRET
internal const val PACT_JWT_ISSUER = TEST_JWT_ISSUER
internal const val PACT_JWT_AUDIENCE = TEST_JWT_AUDIENCE
internal const val PACT_JWT_REALM = TEST_JWT_REALM

// Long-lived token: valid until 2099 — same secret as route tests so provider can verify.
internal val PACT_BEARER_TOKEN: String by lazy {
    "Bearer " +
        JWT
            .create()
            .withAudience(PACT_JWT_AUDIENCE)
            .withIssuer(PACT_JWT_ISSUER)
            .withSubject("pact-consumer")
            .withClaim("organizationId", "org-1")
            .withExpiresAt(Date.from(Instant.parse("2099-12-31T23:59:59Z")))
            .sign(Algorithm.HMAC256(PACT_JWT_SECRET))
}
