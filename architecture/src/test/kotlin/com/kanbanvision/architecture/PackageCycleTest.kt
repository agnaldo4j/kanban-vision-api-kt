package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Fitness function de aciclicidade de pacotes (ADR-0026). As regras de layer
 * (`HexagonalArchitectureTest`) já garantem ausência de ciclo ENTRE módulos, mas não pegam
 * ciclos entre pacotes dentro de um mesmo módulo (ex.: `httpapi.routes ↔ httpapi.plugins`).
 * Konsist 0.17.3 não tem primitivo de ciclo de pacote — o grafo import→pacote é construído
 * manualmente e percorrido pelo DFS compartilhado [findCycle].
 *
 * Trade-off (igual às demais fitness functions): a detecção é baseada em `import` directives —
 * referências totalmente qualificadas sem import não entram no grafo. A granularidade é o
 * pacote FQN: cada sub-pacote é um nó distinto. Ciclos classe↔classe DENTRO de um mesmo pacote
 * (sem import) são cobertos, um nível abaixo, por [ClassCycleTest].
 */
class PackageCycleTest {
    @Test
    fun `nao ha ciclos entre pacotes de producao`() {
        val graph = buildPackageGraph(Konsist.scopeFromProduction().files)
        val cycle = findCycle(graph)
        assertNull(cycle) { "Ciclo de pacote detectado: ${cycle.orEmpty().joinToString(" -> ")}" }
    }

    /** Arestas pacote→pacote derivadas dos imports internos (`com.kanbanvision`), sem self-loops. */
    private fun buildPackageGraph(files: List<KoFileDeclaration>): Map<String, Set<String>> {
        val knownPackages = files.mapNotNull { it.packagee?.name }.toSet()
        val edges = mutableMapOf<String, MutableSet<String>>()
        for (file in files) {
            val source = file.packagee?.name ?: continue
            for (import in file.imports) {
                val target = resolvePackage(import.name, knownPackages)
                if (target != null && target != source) {
                    edges.getOrPut(source) { mutableSetOf() }.add(target)
                }
            }
        }
        return edges
    }

    /** Pacote-alvo de um import = o pacote conhecido mais longo do qual o import é membro. */
    private fun resolvePackage(
        importName: String,
        knownPackages: Set<String>,
    ): String? {
        if (!importName.startsWith(PROJECT_ROOT)) return null
        return knownPackages
            .filter { importName.startsWith("$it.") }
            .maxByOrNull { it.length }
    }

    private companion object {
        private const val PROJECT_ROOT = "com.kanbanvision"
    }
}
