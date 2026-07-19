package com.kanbanvision.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Prova anti-vacuidade do detector compartilhado [findCycle]: as fitness functions que o usam
 * ([ClassCycleTest], [PackageCycleTest]) só têm valor se o detector realmente pega um ciclo — caso
 * contrário passariam verde por estarem quebradas, não por o grafo ser acíclico.
 */
class CycleDetectionTest {
    @Test
    fun `detecta ciclo direto a-b-a`() {
        val cycle = findCycle(mapOf("a" to setOf("b"), "b" to setOf("a")))

        assertNotNull(cycle) { "esperava detectar o ciclo a -> b -> a" }
        assertEquals("a", cycle!!.first())
        assertEquals(cycle.first(), cycle.last()) { "o caminho deve fechar no nó de partida" }
    }

    @Test
    fun `detecta self-loop`() {
        assertNotNull(findCycle(mapOf("a" to setOf("a"))))
    }

    @Test
    fun `nao reporta ciclo em DAG`() {
        val dag = mapOf("a" to setOf("b", "c"), "b" to setOf("c"), "c" to emptySet())

        assertNull(findCycle(dag))
    }

    @Test
    fun `grafo vazio e aciclico`() {
        assertNull(findCycle(emptyMap()))
    }
}
