package com.kanbanvision.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fitness functions dos VALORES de gate de qualidade (GAP-FA/FB).
 *
 * Os números viviam em quatro fontes e duas apodreceram sem ninguém notar: a ADR-0038 quebrou
 * `:domain` em três módulos e a `kotlin-quality-pipeline/SKILL.md` seguiu falando em "os quatro
 * módulos"; a ADR-0029 subiu a cobertura de 97% para 98% e a mesma skill ficou em 97% — e em `0.96`
 * num bloco de código. O sinal não foi a divergência aparecer: foi ninguém notar por meses, porque a
 * fonte que menos se lê é a que apodrece.
 *
 * A topologia que estas regras impõem: a VERDADE é o Gradle, o ÚNICO espelho em doc é o
 * `.claude/rules/stack.md`, e qualquer outro texto aponta para lá em vez de repetir número.
 *
 * O par desta classe é o [ModuleReferenceTest], que compara **nomes de módulo** em vez de valores —
 * guard de existência, que pega o que nenhum guard de igualdade pegaria.
 *
 * Molde do [ProjectDependencyGraphTest] — Konsist não lê configuração de Gradle, então é parse de
 * texto. Limite honesto: casa a forma literal `mutationThreshold.set(N)`; um valor vindo de variável
 * escaparia. O teste do `pitestAll` abaixo é o que impede que isso vire silêncio.
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
    fun `nenhuma doc viva declara um gate de cobertura diferente do autoritativo`() {
        // Codex P2 no #405: corrigir os dígitos à mão NÃO é conserto. Aquele PR acertou 97→98 em oito
        // sítios e deixou todos como cópias FORA do guard — mudar o convention plugin para 99 as
        // deixaria stale de novo, com o guard verde. Pior: a varredura manual perdeu SETE cópias, em
        // quatro arquivos que nem foram abertos, incluindo um bloco de código ainda em `0.97`.
        //
        // Diferente do gate de mutação (que é número POR MÓDULO, e por isso virou ponteiro), a
        // cobertura é UM número — dá para exigir que toda menção viva bata, sem ambiguidade.
        val autoritativo = coberturaEm(CONVENTION_PLUGIN)
        val divergentes =
            docsVivas().flatMap { arquivo ->
                arquivo.readText().lines().withIndex().mapNotNull { (i, linha) ->
                    mençãoDivergente(linha, autoritativo)?.let { "${arquivo.relativeTo(raizDoRepo).path}:${i + 1} — $it" }
                }
            }
        assertTrue(divergentes.isEmpty()) {
            "doc viva citando gate de cobertura diferente de $autoritativo% (a verdade é $CONVENTION_PLUGIN):\n" +
                divergentes.joinToString("\n")
        }
    }

    @Test
    fun `o stripper de comentarios nao confunde glob dentro de string com abertura de bloco`() {
        // Regressão medida: a forma anterior (regex `/\*.*?\*/`) tratava o `/**` do glob como abertura
        // de bloco e engolia ~150 linhas do http_api/build.gradle.kts, incluindo o gate. As regras
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

    /**
     * A menção é divergente se fala de cobertura, declara **um** percentual e ele não é o autoritativo.
     *
     * "Um" é o que separa declaração de gate de frase histórica: `docs/politicas-explicitas.md` diz
     * *"ADR-0029 raised coverage from 97% to 98%"*, legítimo dentro de doc viva, e cita DOIS. Uma
     * declaração de gate afirma um número só — e é essa que tem de bater. Testar "contém o
     * autoritativo" não serviria: passaria hoje e acusaria a mesma frase histórica no dia em que o
     * valor mudasse.
     */
    private fun mençãoDivergente(
        linha: String,
        autoritativo: Int,
    ): String? {
        // `NO_COVERAGE` é vocabulário de PITest e aparece ao lado de percentual de MUTAÇÃO — sem tirá-lo
        // antes, metade do texto de mutação viraria falso-positivo.
        val texto = linha.replace("NO_COVERAGE", "")
        if (!FALA_DE_COBERTURA.containsMatchIn(texto)) return null
        return declaracaoDeGate(texto)?.takeIf { (valor, _) -> valor != autoritativo }?.second
    }

    /** O valor declarado e como reportá-lo, ou `null` se a linha não declara UM gate. */
    private fun declaracaoDeGate(texto: String): Pair<Int, String>? {
        val literal = MINIMUM_LITERAL.find(texto)?.groupValues?.get(1)
        if (literal != null) {
            return (literal.toBigDecimal() * PERCENTUAL).toInt() to "minimum = \"$literal\""
        }
        val unico =
            PERCENTUAL_CITADO
                .findAll(texto)
                .map { it.groupValues[1].toInt() }
                .toList()
                .singleOrNull()
        return unico?.let { it to "declara $it%" }
    }

    /** A linha `| Mutation testing | … gates: `mod` N% · … |` da tabela de stack, e só ela. */
    private fun gatesDeMutacaoNoEspelho(): Map<String, Int> =
        MODULO_COM_PERCENTUAL
            .findAll(linhaDoEspelho("Mutation testing"))
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }

    private fun coberturaNoEspelho(): Int =
        requireNotNull(COBERTURA_NO_ESPELHO.find(linhaDoEspelho("Coverage"))) {
            "a linha Coverage de $ESPELHO não declara um `≥ N%`"
        }.groupValues[1].toInt()

    private companion object {
        // A linha fala do gate de COBERTURA? Os três vocabulários em uso no repo. O `NO_COVERAGE` do
        // PITest casaria `coverage` e por isso é removido da linha antes — sem isso, metade do texto
        // de mutação viraria falso-positivo.
        val FALA_DE_COBERTURA = Regex("""(?i)\bjacoco|\bcobertura\b|\bcoverage\b""")

        // `(?<!\d)` obrigatório: sem ele o `\d{2}` casa o "00" de "100%" e vira falso-positivo em
        // "cobertura de 100% dos arquivos" (medido — dois sítios reprovaram indevidamente).
        val PERCENTUAL_CITADO = Regex("""(?<!\d)(\d{2})\s*%""")
        val MINIMUM_LITERAL = Regex("""minimum\s*=\s*"([\d.]+)"""")

        // No espelho o módulo vem em crase e o gate logo depois: `` `usecases` 55% ``.
        val MODULO_COM_PERCENTUAL = Regex("""`([A-Za-z0-9_\-]+)`\s*(\d+)%""")
        val COBERTURA_NO_ESPELHO = Regex("""≥\s*(\d+)%""")
    }
}
