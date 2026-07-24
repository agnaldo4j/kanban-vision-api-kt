package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Fitness function de aciclicidade do grafo de INJEÇÃO POR CONSTRUTOR do Koin (ADR-0026).
 *
 * Os gates de ciclo existentes não cobrem o wiring de DI: [PackageCycleTest] olha o grafo de import
 * (o wiring resolve por tipo via `get()`, sem import) e [ClassCycleTest] olha composição INTRA-pacote
 * (o `AppModule` cruza pacotes/módulos). A skill `/circular-dependency-control` classifica o ciclo de
 * injeção por construtor como o de MAIOR severidade — `A(B)` + `B(A)` estoura `StackOverflowError` no
 * Koin ao montar o objeto. Este gate constrói o grafo `impl → tipos-do-construtor` (resolvendo porta→impl
 * pelas bindings do `AppModule`) e o percorre com o mesmo DFS [findCycle] das outras fitness functions.
 *
 * Verde hoje (as deps fluem numa direção só: use case → porta → adapter, cada adapter é sink) — o valor
 * é de GUARDA DE REGRESSÃO contra uma futura inversão de wiring, como [ProjectDependencyGraphTest].
 * Estático (respeita "sem call-graph de runtime"): lê assinaturas de construtor (Konsist) + o texto do
 * `AppModule`; a parte pura ([parseKoinBindings]/[buildInjectionGraph]) tem fixtures próprios abaixo.
 */
class DiWiringCycleTest {
    @Test
    fun `o grafo de injecao por construtor do AppModule e aciclico`() {
        val bindings = parseKoinBindings(appModuleText())
        // Anti-vacuidade: o parser realmente enxergou o wiring (senão o gate ficaria verde à toa).
        assertTrue(bindings.components.containsAll(ANCHORS)) {
            "parseKoinBindings não reconheceu o wiring esperado do AppModule; encontrou ${bindings.components}"
        }

        val ctorParams = konsistCtorParamTypes()
        val cycle = findCycle(buildInjectionGraph(bindings) { ctorParams[it].orEmpty() })

        assertNull(cycle) {
            "Ciclo de injeção por construtor (Koin) detectado — StackOverflowError no wiring:\n  " +
                cycle?.joinToString(" -> ") +
                "\nQuebre com inversão de dependência ou um mediador (skill /circular-dependency-control)."
        }
    }

    @Test
    fun `parseKoinBindings reconhece as tres formas de single do DSL`() {
        val text =
            """
            module {
                single { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) } bind MeterRegistry::class
                single<EventPublisherPort> { MicrometerEventPublisher(get()) }
                single { CreateSimulationUseCase(get(), get(), get()) }
            }
            """.trimIndent()

        val bindings = parseKoinBindings(text)

        assertEquals(
            setOf("PrometheusMeterRegistry", "MicrometerEventPublisher", "CreateSimulationUseCase"),
            bindings.components,
        )
        assertEquals("PrometheusMeterRegistry", bindings.resolvesTo["MeterRegistry"])
        assertEquals("MicrometerEventPublisher", bindings.resolvesTo["EventPublisherPort"])
        assertEquals("CreateSimulationUseCase", bindings.resolvesTo["CreateSimulationUseCase"])
    }

    @Test
    fun `buildInjectionGraph resolve porta para impl e o detector acha o ciclo`() {
        // A injeta a porta PB (→ B) e B injeta a porta PA (→ A): ciclo de wiring clássico.
        val bindings =
            KoinBindings(
                components = setOf("A", "B"),
                resolvesTo = mapOf("A" to "A", "B" to "B", "PA" to "A", "PB" to "B"),
            )
        val ctor = mapOf("A" to listOf("PB"), "B" to listOf("PA"))

        val graph = buildInjectionGraph(bindings) { ctor[it].orEmpty() }

        assertNotNull(findCycle(graph)) { "deveria detectar A → B → A" }
    }

    @Test
    fun `buildInjectionGraph preserva self-injection como ciclo`() {
        // single { A(get()) } com class A(other: A): o Koin estoura montando A → A (self-loop real).
        val bindings = KoinBindings(components = setOf("A"), resolvesTo = mapOf("A" to "A"))

        val graph = buildInjectionGraph(bindings) { if (it == "A") listOf("A") else emptyList() }

        assertEquals(mapOf("A" to setOf("A")), graph)
        assertNotNull(findCycle(graph)) { "self-injection A → A deve ser detectada" }
    }

    @Test
    fun `buildInjectionGraph nao fabrica ciclo num DAG e ignora tipos externos`() {
        val bindings =
            KoinBindings(
                components = setOf("UseCase", "Repo"),
                resolvesTo = mapOf("UseCase" to "UseCase", "Repo" to "Repo", "RepoPort" to "Repo"),
            )
        // UseCase injeta RepoPort (→ Repo) e um MeterRegistry externo (não-componente → ignorado).
        val ctor = mapOf("UseCase" to listOf("RepoPort", "MeterRegistry"), "Repo" to emptyList())

        val graph = buildInjectionGraph(bindings) { ctor[it].orEmpty() }

        assertEquals(mapOf("UseCase" to setOf("Repo")), graph)
        assertNull(findCycle(graph))
    }

    /** Nome simples da classe → tipos dos parâmetros do seu construtor primário (nome simples). */
    private fun konsistCtorParamTypes(): Map<String, List<String>> =
        Konsist
            .scopeFromProduction()
            .classes(includeNested = true)
            .associate { klass ->
                klass.name to
                    klass.primaryConstructor
                        ?.parameters
                        ?.map { it.type.name }
                        .orEmpty()
            }

    private fun appModuleText(): String {
        val root = System.getProperty("rootDir")?.let(::File) ?: File("..")
        val file = File(root, APP_MODULE)
        require(file.isFile) {
            "AppModule.kt não encontrado em ${file.absolutePath} — o wiring do Koin mudou de lugar? Atualize este gate."
        }
        return file.readText()
    }

    private companion object {
        private const val APP_MODULE = "http_api/src/main/kotlin/com/kanbanvision/httpapi/di/AppModule.kt"

        // Âncoras de sanidade: se o parser não achar estas bindings, o gate está cego — falhe alto.
        private val ANCHORS = setOf("CreateSimulationUseCase", "JdbcOrganizationRepository", "MicrometerEventPublisher")
    }
}
