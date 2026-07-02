package com.kanbanvision.httpapi.routes

import com.kanbanvision.httpapi.plugins.configureOpenApi
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val SIMULATIONS_PATH = "/api/v1/simulations"
private const val SIMULATION_BY_ID_PATH = "$SIMULATIONS_PATH/{simulationId}"

internal class OpenApiSpecTest {
    private fun withSpec(assertions: (JsonObject) -> Unit) =
        testApplication {
            application {
                configureOpenApi()
                configureSimulationApi(SimulationApiMocks())
            }
            val response = client.get("/api.json")
            assertEquals(HttpStatusCode.OK, response.status)
            assertions(Json.parseToJsonElement(response.bodyAsText()).jsonObject)
        }

    private fun JsonObject.at(vararg keys: String): JsonObject? {
        var current: JsonObject = this
        for (key in keys) {
            current = (current[key] as? JsonObject) ?: return null
        }
        return current
    }

    private fun JsonObject.requestExamples(
        path: String,
        method: String,
    ): JsonObject? = at("paths", path, method, "requestBody", "content", "application/json", "examples")

    private fun JsonObject.responseExamples(
        path: String,
        method: String,
        status: String,
    ): JsonObject? = at("paths", path, method, "responses", status, "content", "application/json", "examples")

    @Test
    fun `spec exposes request body examples for POST simulations and POST run`() =
        withSpec { spec ->
            val createExamples = spec.requestExamples(SIMULATIONS_PATH, "post")
            assertNotNull(createExamples, "POST $SIMULATIONS_PATH request body must declare examples")
            assertTrue(createExamples!!.containsKey("padrão"))

            val runExamples = spec.requestExamples("$SIMULATION_BY_ID_PATH/run", "post")
            assertNotNull(runExamples, "POST $SIMULATION_BY_ID_PATH/run request body must declare examples")
            assertTrue(runExamples!!.containsKey("sem decisões"))
            assertTrue(runExamples.containsKey("mover item"))
        }

    @Test
    fun `spec exposes default response example on success code of all seven simulation endpoints`() =
        withSpec { spec ->
            val successResponses =
                listOf(
                    Triple(SIMULATIONS_PATH, "get", "200"),
                    Triple(SIMULATIONS_PATH, "post", "201"),
                    Triple(SIMULATION_BY_ID_PATH, "get", "200"),
                    Triple("$SIMULATION_BY_ID_PATH/run", "post", "200"),
                    Triple("$SIMULATION_BY_ID_PATH/days/{day}/snapshot", "get", "200"),
                    Triple("$SIMULATION_BY_ID_PATH/days", "get", "200"),
                    Triple("$SIMULATION_BY_ID_PATH/cfd", "get", "200"),
                )
            for ((path, method, status) in successResponses) {
                val examples = spec.responseExamples(path, method, status)
                assertNotNull(examples, "$method $path $status must declare response examples")
                assertTrue(
                    examples!!.containsKey("default"),
                    "$method $path $status must declare the 'default' response example",
                )
            }
        }

    @Test
    fun `spec does not declare response examples for undocumented status codes`() =
        withSpec { spec ->
            assertNotNull(spec.at("paths", SIMULATIONS_PATH, "post", "responses", "201"))
            assertNull(spec.responseExamples(SIMULATIONS_PATH, "post", "202"))
        }

    @Test
    fun `spec declares a server entry referencing the versioning policy`() =
        withSpec { spec ->
            val servers = spec["servers"] as? JsonArray
            assertNotNull(servers, "spec must declare a servers block (ADR-0022)")
            assertTrue(
                servers!!.any {
                    it.jsonObject["description"]
                        ?.jsonPrimitive
                        ?.content
                        ?.contains("/api/v1") == true
                },
                "server description must reference the /api/v1 versioning",
            )
        }
}
