package com.kanbanvision.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Fixture positivo permanente do pipeline de grafo de [ClassCycleTest] (GAP-CV). O caso-verde real
 * (produção sem homônimos) só cobre [buildClassGraph] indiretamente; aqui provamos, com [ClassNode]s
 * sintéticos, exatamente os cenários que a identidade por FQN conserta e que o nome simples quebrava:
 * homônimos não colapsam, a resolução respeita o escopo enclosing, e a ambiguidade é pulada (green-safe).
 */
class ClassGraphTest {
    private fun node(
        fqn: String,
        refs: Set<String>,
    ) = ClassNode(fqn = fqn, simpleName = fqn.substringAfterLast('.'), refs = refs)

    @Test
    fun `resolucao 1-para-1 monta a aresta e remove self-ref`() {
        val graph =
            buildClassGraph(
                listOf(node("pkg.A", setOf("B", "A")), node("pkg.B", emptySet())),
            )

        assertEquals(setOf("pkg.B"), graph["pkg.A"]) { "A -> B; a self-ref A some" }
        assertNull(graph["pkg.B"]) { "B não referencia nada" }
    }

    @Test
    fun `homonimos aninhados no mesmo pacote NAO colapsam num no so`() {
        val graph =
            buildClassGraph(
                listOf(
                    node("pkg.OuterA.State", setOf("Foo")),
                    node("pkg.OuterC.State", setOf("Bar")),
                    node("pkg.Foo", emptySet()),
                    node("pkg.Bar", emptySet()),
                ),
            )

        // Com chave por nome simples, os dois `State` virariam um nó `{Foo, Bar}`. Por FQN, são distintos:
        assertEquals(setOf("pkg.Foo"), graph["pkg.OuterA.State"])
        assertEquals(setOf("pkg.Bar"), graph["pkg.OuterC.State"])
    }

    @Test
    fun `ref homonima resolve para o homonimo do escopo enclosing mais proximo`() {
        val graph =
            buildClassGraph(
                listOf(
                    node("pkg.OuterA.Inner", setOf("State")),
                    node("pkg.OuterA.State", emptySet()),
                    node("pkg.OuterC.State", emptySet()),
                ),
            )

        assertEquals(setOf("pkg.OuterA.State"), graph["pkg.OuterA.Inner"]) {
            "de dentro de OuterA, `State` é OuterA.State, não OuterC.State"
        }
    }

    @Test
    fun `ref homonima sem candidato em escopo e pulada (sem falso-positivo)`() {
        val graph =
            buildClassGraph(
                listOf(
                    node("pkg.X", setOf("State")),
                    node("pkg.OuterA.State", emptySet()),
                    node("pkg.OuterC.State", emptySet()),
                ),
            )

        assertNull(graph["pkg.X"]) {
            "`State` de pkg.X é ambíguo (dois aninhados, nenhum em escopo) → aresta pulada, green-safe"
        }
    }

    @Test
    fun `colapso de homonimo NAO inventa um ciclo (falso-positivo evitado)`() {
        // Nome simples: `State` (merge de OuterA/OuterC) -> Foo e Foo -> `State` = ciclo FALSO.
        val graph =
            buildClassGraph(
                listOf(
                    node("pkg.OuterA.State", setOf("Foo")),
                    node("pkg.OuterC.State", setOf("Foo")),
                    node("pkg.Foo", setOf("State")),
                ),
            )

        assertNull(findCycle(graph)) {
            "Foo -> State é ambíguo → pulado; sem os nós separados por FQN, seria um ciclo fabricado"
        }
    }

    @Test
    fun `ciclo real entre FQNs distintos e detectado apesar de um homonimo-isca`() {
        val graph =
            buildClassGraph(
                listOf(
                    node("pkg.A", setOf("B")),
                    node("pkg.B", setOf("A")),
                    node("pkg.Outer.A", emptySet()), // isca homônima de A, fora do escopo de pkg.B
                ),
            )

        assertNotNull(findCycle(graph)) { "pkg.A <-> pkg.B fecha um ciclo; a isca não interfere" }
    }
}
