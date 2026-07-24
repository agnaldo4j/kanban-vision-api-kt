package com.kanbanvision.architecture

/**
 * Parte PURA do grafo de injeção por construtor do Koin (AppModule), extraída para ser testável em
 * isolamento (fixtures em [DiWiringCycleTest]) — espelha o split [ClassNode] ↔ [ClassCycleTest].
 *
 * Fecha o blind spot de MAIOR severidade da skill `/circular-dependency-control`: um ciclo de injeção
 * por construtor (`A(B)`, `B(A)`) faz o Koin estourar `StackOverflowError` ao montar o objeto. Ele é
 * invisível ao [ClassCycleTest] (composição intra-pacote) e ao [PackageCycleTest] (grafo de import)
 * porque o wiring é montado no `AppModule`, cruzando pacotes/módulos e resolvido por TIPO (`get()`),
 * não por import. Estático (respeita "sem call-graph de runtime"): o grafo vem das assinaturas de
 * construtor + do mapa porta→impl declarado no `AppModule`, nunca de execução.
 */
internal data class KoinBindings(
    /** Nomes simples das classes concretas instanciadas por um `single { ... }`. */
    val components: Set<String>,
    /** Tipo declarado (porta/interface OU o próprio concreto) → impl concreta que o Koin resolve. */
    val resolvesTo: Map<String, String>,
)

/**
 * Extrai [KoinBindings] do TEXTO do `AppModule`. Reconhece as três formas do DSL:
 *  - `single<Porta> { Impl(...) }`            → resolvesTo[Porta] = Impl
 *  - `single { Impl(...) } bind Porta::class` → resolvesTo[Porta] = Impl
 *  - `single { Impl(...) }`                   → resolvesTo[Impl]  = Impl
 * Em todas, o concreto também resolve a si mesmo (o Koin registra o tipo concreto além do bound).
 * Limitação (viés a falso-negativo, como os demais gates): casa corpos de lambda sem chaves aninhadas
 * — exatamente o caso do `AppModule`; uma binding em forma exótica seria pulada, nunca inventada.
 */
internal fun parseKoinBindings(appModuleText: String): KoinBindings {
    val binding = Regex("""single\s*(?:<([\w.]+)>)?\s*\{\s*(\w+)\s*\([^{}]*\}\s*(?:bind\s+([\w.]+)::class)?""")
    val cleaned =
        Regex("""/\*.*?\*/""", setOf(RegexOption.DOT_MATCHES_ALL))
            .replace(appModuleText, "")
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")

    val components = mutableSetOf<String>()
    val resolvesTo = mutableMapOf<String, String>()
    binding.findAll(cleaned).forEach { match ->
        val declaredType = match.groupValues[1].ifBlank { null }
        val impl = match.groupValues[2]
        val boundType = match.groupValues[3].ifBlank { null }
        components += impl
        resolvesTo[impl] = impl
        declaredType?.let { resolvesTo[it] = impl }
        boundType?.let { resolvesTo[it] = impl }
    }
    return KoinBindings(components, resolvesTo)
}

/**
 * Grafo `impl → {impl}`: para cada componente, uma aresta a cada parâmetro de construtor cujo TIPO
 * resolve (via [KoinBindings.resolvesTo]) a OUTRO componente. [ctorParamTypes] devolve os nomes
 * simples dos tipos dos parâmetros do construtor primário de um componente — vazio quando a classe não
 * está no escopo de produção (um tipo de biblioteca, ex.: `PrometheusMeterRegistry`, vira sink).
 */
internal fun buildInjectionGraph(
    bindings: KoinBindings,
    ctorParamTypes: (String) -> List<String>,
): Map<String, Set<String>> {
    val edges = mutableMapOf<String, Set<String>>()
    for (impl in bindings.components) {
        val targets =
            ctorParamTypes(impl)
                .mapNotNull { bindings.resolvesTo[it] }
                .filterTo(mutableSetOf()) { it in bindings.components && it != impl }
        if (targets.isNotEmpty()) edges[impl] = targets
    }
    return edges
}
