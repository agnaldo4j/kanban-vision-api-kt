package com.kanbanvision.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Fitness function do espelho dos gates de qualidade (GAP-FA).
 *
 * Os números viviam em quatro fontes e duas apodreceram sem ninguém notar: a ADR-0038 quebrou
 * `:domain` em três módulos e a `kotlin-quality-pipeline/SKILL.md` seguiu falando em "os quatro
 * módulos" com uma linha `domain`; a ADR-0029 subiu a cobertura de 97% para 98% e a mesma skill ficou
 * em 97% — e em `0.96` num bloco de código. O sinal não foi a divergência aparecer: foi ninguém notar
 * por meses, porque a fonte que menos se lê é a que apodrece.
 *
 * A topologia que estas regras impõem: a VERDADE é o Gradle, o ÚNICO espelho em doc é o
 * `.claude/rules/stack.md`, e qualquer outro texto aponta para lá em vez de repetir número.
 *
 * Molde do [ProjectDependencyGraphTest] — Konsist não lê configuração de Gradle, então é parse de
 * texto. Limite honesto: casa a forma literal `mutationThreshold.set(N)`; um valor vindo de variável
 * escaparia. É a mesma troca que o `ProjectDependencyGraphTest` faz, e o passo 3 abaixo é o que impede
 * que isso vire silêncio — um módulo cujo gate o parser não enxergasse cairia fora do conjunto e
 * quebraria a igualdade com o `pitestAll`.
 */
class QualityGateMirrorTest {
    @Test
    fun `o espelho em stack md declara o gate de mutacao de cada modulo`() {
        assertEquals(
            gatesDeMutacaoNoGradle(),
            gatesDeMutacaoNoEspelho(),
            "o gate de mutação divergiu entre os build.gradle.kts e o espelho em $ESPELHO. " +
                "A verdade é o Gradle: acerte o espelho, não o contrário.",
        )
    }

    @Test
    fun `o gate de cobertura e o mesmo no convention plugin, no override do http_api e no espelho`() {
        // O `http_api` redeclara o `minimum` para reaplicar os próprios excludes de JaCoCo. Hoje os dois
        // números concordam por disciplina do autor; nada garantia isso.
        val doConventionPlugin = coberturaEm(CONVENTION_PLUGIN)
        assertEquals(
            doConventionPlugin,
            coberturaEm("http_api/build.gradle.kts"),
            "o override de JaCoCo do http_api divergiu do convention plugin",
        )
        assertEquals(
            doConventionPlugin,
            coberturaNoEspelho(),
            "o gate de cobertura divergiu entre o convention plugin e o espelho em $ESPELHO",
        )
    }

    @Test
    fun `todo modulo com gate de mutacao esta no pitestAll, e vice-versa`() {
        // Par NÃO-VÁCUO das duas regras acima: elas comparam dois conjuntos, e dois conjuntos VAZIOS são
        // iguais. Um parser quebrado — ou um `mutationThreshold` escrito de forma que o regex não casa —
        // deixaria as duas verdes sem ter olhado nada. O `pitestAll` é a terceira testemunha, e é a que
        // de fato roda no CI: um módulo com gate fora dele não é medido em PR nenhum.
        val comGate = gatesDeMutacaoNoGradle().keys
        assertTrue(comGate.isNotEmpty(), "nenhum mutationThreshold lido dos build.gradle.kts — o parser quebrou")
        assertEquals(
            modulosDoPitestAll(),
            comGate,
            "há módulo com gate de mutação fora do pitestAll (não medido no CI) ou vice-versa",
        )
    }

    @Test
    fun `o stripper de comentarios nao confunde glob dentro de string com abertura de bloco`() {
        // Regressão medida: a forma anterior (regex `/\*.*?\*/`) tratava o `/**` do glob como abertura
        // de bloco e engolia ~150 linhas do http_api/build.gradle.kts, incluindo o gate. As duas regras
        // acima ficariam vermelhas — mas por um módulo "sem gate", diagnóstico que aponta para o lugar
        // errado. Este teste é o que nomeia a causa.
        val script =
            """
            exclude("META-INF/native-image/com.kanbanvision/http_api/**")
            /* bloco de verdade some */
            mutationThreshold.set(45) // trailing some, o código antes fica
            val raw = ""${'"'}glob /** dentro de raw string""${'"'}
            """.trimIndent()

        val limpo = script.semComentarios()

        assertTrue("mutationThreshold.set(45)" in limpo, "o gate foi engolido pelo glob: $limpo")
        assertTrue("META-INF" in limpo, "a string do exclude deve sobreviver inteira: $limpo")
        assertTrue("glob /** dentro de raw string" in limpo, "raw string deve sobreviver inteira: $limpo")
        assertTrue("bloco de verdade" !in limpo, "comentário de bloco real deveria ter saído: $limpo")
        assertTrue("trailing some" !in limpo, "comentário de linha deveria ter saído: $limpo")
    }

    private fun gatesDeMutacaoNoGradle(): Map<String, Int> =
        modulosDoSettings()
            .mapNotNull { modulo ->
                MUTATION_THRESHOLD
                    .find(ler("$modulo/build.gradle.kts").semComentarios())
                    ?.let { modulo to it.groupValues[1].toInt() }
            }.toMap()

    /** A linha `| Mutation testing | … gates: `mod` N% · … |` da tabela de stack, e só ela. */
    private fun gatesDeMutacaoNoEspelho(): Map<String, Int> =
        MODULO_COM_PERCENTUAL
            .findAll(linhaDoEspelho("Mutation testing"))
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }

    /** `minimum = "0.98"` → 98. Percentual inteiro para comparar com o texto do espelho sem float. */
    private fun coberturaEm(caminho: String): Int {
        val texto = ler(caminho).semComentarios()
        val bruto =
            requireNotNull(JACOCO_MINIMUM.find(texto)) { "nenhum `minimum = \"…\"` de JaCoCo encontrado em $caminho" }
                .groupValues[1]
        return (bruto.toBigDecimal() * PERCENTUAL).toInt()
    }

    private fun coberturaNoEspelho(): Int =
        requireNotNull(COBERTURA_NO_ESPELHO.find(linhaDoEspelho("Coverage"))) {
            "a linha Coverage de $ESPELHO não declara um `≥ N%`"
        }.groupValues[1].toInt()

    private fun linhaDoEspelho(rotulo: String): String =
        ler(ESPELHO)
            .lines()
            .singleOrNull { it.startsWith("| $rotulo |") }
            ?: error("$ESPELHO deve ter exatamente uma linha de tabela começando por `| $rotulo |`")

    private fun modulosDoSettings(): List<String> =
        MODULO_INCLUIDO
            .findAll(ler("settings.gradle.kts").semComentarios())
            .map { it.groupValues[1] }
            .toList()
            .also { require(it.isNotEmpty()) { "nenhum módulo lido do settings.gradle.kts — o parser quebrou" } }

    private fun modulosDoPitestAll(): Set<String> =
        PITEST_ALL_DEP
            .findAll(ler("build.gradle.kts").semComentarios())
            .map { it.groupValues[1] }
            .toSet()

    private fun ler(caminhoRelativo: String): String {
        val raiz = System.getProperty("rootDir")?.let(::File) ?: File("..")
        val arquivo = File(raiz, caminhoRelativo)
        require(arquivo.isFile) { "arquivo não encontrado: ${arquivo.absolutePath}" }
        return arquivo.readText()
    }

    private companion object {
        const val ESPELHO = ".claude/rules/stack.md"
        const val CONVENTION_PLUGIN = "buildSrc/src/main/kotlin/kanban.kotlin-common.gradle.kts"
        val PERCENTUAL = 100.toBigDecimal()

        val MUTATION_THRESHOLD = Regex("""mutationThreshold\s*\.\s*set\s*\(\s*(\d+)\s*\)""")
        val JACOCO_MINIMUM = Regex("""minimum\s*=\s*"([\d.]+)"""")
        val MODULO_INCLUIDO = Regex("""["']\s*:([A-Za-z0-9_\-]+)\s*["']""")
        val PITEST_ALL_DEP = Regex("""["']\s*:([A-Za-z0-9_\-]+):pitest\s*["']""")

        // No espelho o módulo vem em crase e o gate logo depois: `` `usecases` 55% ``.
        val MODULO_COM_PERCENTUAL = Regex("""`([A-Za-z0-9_\-]+)`\s*(\d+)%""")
        val COBERTURA_NO_ESPELHO = Regex("""≥\s*(\d+)%""")
    }
}
