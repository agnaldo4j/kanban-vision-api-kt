package com.kanbanvision.httpapi.plugins

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class OpenApiGateTest {
    @Test
    fun `given swagger disabled when requesting api json then not found`() =
        testApplication {
            application { configureOpenApi(enabled = false) }

            assertEquals(HttpStatusCode.NotFound, client.get("/api.json").status)
        }

    @Test
    fun `given swagger disabled when requesting swagger ui then not found`() =
        testApplication {
            application { configureOpenApi(enabled = false) }

            assertEquals(HttpStatusCode.NotFound, client.get("/swagger").status)
        }

    @Test
    fun `swaggerEnabled is true only when env value lowercases to true`() {
        assertTrue(swaggerEnabled { "true" })
        assertTrue(swaggerEnabled { "TRUE" })
        assertFalse(swaggerEnabled { "false" })
        assertFalse(swaggerEnabled { "1" })
        assertFalse(swaggerEnabled { null })
    }

    @Test
    fun `swaggerEnabled defaults to reading the process environment`() {
        // ENABLE_SWAGGER is unset in the test JVM — default seam must resolve to false.
        assertFalse(swaggerEnabled())
    }
}
