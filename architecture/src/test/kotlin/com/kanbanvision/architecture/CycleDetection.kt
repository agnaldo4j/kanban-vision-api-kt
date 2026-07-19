package com.kanbanvision.architecture

/**
 * Detector de ciclos genérico, compartilhado pelas fitness functions de aciclicidade:
 * [PackageCycleTest] (grafo import→pacote) e [ClassCycleTest] (grafo classe→classe intra-pacote).
 *
 * DFS com coloração: um nó fora de [visited] é branco; enquanto está em [stack] é cinza (na cadeia
 * de recursão atual); ao sair da pilha vira preto. Encontrar um nó cinza fecha um ciclo. Retorna o
 * primeiro caminho fechado (`A -> B -> A`) ou `null` se o grafo é acíclico.
 */
internal fun findCycle(graph: Map<String, Set<String>>): List<String>? {
    val visited = mutableSetOf<String>()
    val stack = mutableListOf<String>()
    for (node in graph.keys) {
        val cycle = walk(node, graph, visited, stack)
        if (cycle != null) return cycle
    }
    return null
}

private fun walk(
    node: String,
    graph: Map<String, Set<String>>,
    visited: MutableSet<String>,
    stack: MutableList<String>,
): List<String>? {
    if (node in stack) return stack.subList(stack.indexOf(node), stack.size) + node
    if (node in visited) return null
    visited.add(node)
    stack.add(node)
    val cycle = graph[node].orEmpty().firstNotNullOfOrNull { walk(it, graph, visited, stack) }
    stack.removeAt(stack.size - 1)
    return cycle
}
