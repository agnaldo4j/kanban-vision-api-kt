package com.kanbanvision.architecture

/**
 * Pipeline puro do grafo de composição classe→classe intra-pacote de [ClassCycleTest], extraído para
 * ser testável em isolamento (fixtures positivos em [ClassGraphTest]) — espelha [CycleDetection].
 *
 * **Identidade por FQN (GAP-CV).** Cada nó é o FQN da declaração (incl. enclosing: `pkg.Outer.Nested`),
 * NÃO o nome simples. Assim declarações aninhadas homônimas no mesmo pacote (`Outer.State` vs
 * `Other.State`) e `companion object` (FQN `...Owner.Companion`) não colapsam num único nó — o que
 * mascararia dep cross-tipo (falso-positivo) ou a esconderia como self-edge (falso-negativo).
 */
internal data class ClassNode(
    val fqn: String,
    val simpleName: String,
    val refs: Set<String>,
) {
    /** Escopo que contém a declaração: o FQN sem o último segmento (`pkg.Outer.State` → `pkg.Outer`). */
    val enclosing: String get() = fqn.substringBeforeLast('.')
}

/**
 * Grafo `fqn → {fqn alvo}` restrito a arestas intra-pacote, sem self-loops. Cada ref (nome simples,
 * tokenizado de `type.text` em [ClassCycleTest]) é resolvida a um nó do pacote por [resolveRef].
 */
internal fun buildClassGraph(nodes: List<ClassNode>): Map<String, Set<String>> {
    val bySimpleName = nodes.groupBy { it.simpleName }
    val edges = mutableMapOf<String, Set<String>>()
    for (node in nodes) {
        val targets = node.refs.mapNotNullTo(mutableSetOf()) { resolveRef(it, node, bySimpleName) } - node.fqn
        if (targets.isNotEmpty()) edges[node.fqn] = targets
    }
    return edges
}

/**
 * Resolve um nome simples referenciado a partir de [from] para o FQN de um nó do pacote:
 * - 0 candidatos → `null` (tipo externo/desconhecido: sem aresta);
 * - 1 candidato → aresta direta;
 * - >1 (homônimos) → o candidato visível do escopo do referente **mais próximo** (maior `enclosing`).
 *   "Visível de [from]" = o próprio [from] (self), um tipo aninhado DIRETO de [from] (a classe declarante
 *   é o escopo do seu aninhado: `from.fqn == enclosing`), ou um tipo cujo `enclosing` é ancestral de
 *   [from] (`from.fqn` começa com `enclosing + "."`). Empate genuíno no escopo mais próximo, ou nenhum
 *   candidato em escopo → `null` (aresta pulada). O viés é a falso-negativo (resíduo documentado), NUNCA
 *   a falso-positivo — um gate que reprova código limpo é pior que um que perde um ciclo homônimo raro.
 */
private fun resolveRef(
    ref: String,
    from: ClassNode,
    bySimpleName: Map<String, List<ClassNode>>,
): String? {
    val candidates = bySimpleName[ref].orEmpty()
    return when (candidates.size) {
        0 -> null
        1 -> candidates.single().fqn
        else -> {
            val inScope = candidates.filter { from.visibleScopeOf(it) }
            val nearest = inScope.maxOfOrNull { it.enclosing.length }
            inScope.singleOrNull { it.enclosing.length == nearest }?.fqn
        }
    }
}

/** [candidate] é visível do escopo de `this` referente? (self · aninhado direto de this · ancestral). */
private fun ClassNode.visibleScopeOf(candidate: ClassNode): Boolean =
    fqn == candidate.fqn || fqn == candidate.enclosing || fqn.startsWith(candidate.enclosing + ".")
