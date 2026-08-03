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
    /** `workingDir` do teste é o projectDir do módulo; a raiz vem por systemProperty (ver build.gradle.kts). */
    private val raiz = System.getProperty("rootDir")?.let(::File) ?: File("..")

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
        // Codex P2 no #405: corrigir os dígitos à mão NÃO é conserto. A primeira versão deste PR
        // acertou 97→98 em oito sítios e deixou todos como cópias FORA do guard — mudar o convention
        // plugin para 99 as deixaria stale de novo, com o guard verde, que é exatamente o drift que
        // este PR existe para matar. Pior: a varredura manual perdeu SETE cópias, em quatro arquivos
        // que nem foram abertos, incluindo um segundo bloco de código ainda em `0.97`.
        //
        // Diferente do gate de mutação (que é número POR MÓDULO, e por isso virou ponteiro), a
        // cobertura é UM número — dá para exigir que toda menção viva bata, sem ambiguidade.
        val autoritativo = coberturaEm(CONVENTION_PLUGIN)
        val divergentes =
            docsVivas().flatMap { arquivo ->
                arquivo.readText().lines().withIndex().mapNotNull { (i, linha) ->
                    mençãoDivergente(linha, autoritativo)?.let { "${arquivo.relativeTo(raiz).path}:${i + 1} — $it" }
                }
            }
        assertTrue(divergentes.isEmpty()) {
            "doc viva citando gate de cobertura diferente de $autoritativo% (a verdade é $CONVENTION_PLUGIN):\n" +
                divergentes.joinToString("\n")
        }
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

    @Test
    fun `nenhuma doc ou config viva cita modulo Gradle que nao existe`() {
        // A ADR-0038 dividiu `:domain` em três e a documentação seguiu mandando rodar
        // `./gradlew :domain:pitest` e abrir `domain/build/reports/…` por QUASE UM ANO — a mesma
        // podridão que o GAP-FA citou como motivação, sobrevivendo no arquivo que ele curou. Esta
        // regra teria pego aquilo no dia, e é a que faltava: o guard do GAP-FA prova que o espelho
        // CONCORDA com a verdade, não que não existe outra cópia falando de outra coisa.
        val existentes = modulosDoSettings().toSet()
        val fantasmas =
            docsVivas().flatMap { arquivo ->
                arquivo.readText().lines().withIndex().flatMap { (i, linha) ->
                    modulosCitadosEm(linha)
                        .filterNot { it in existentes }
                        .map { "${arquivo.relativeTo(raiz).path}:${i + 1} — cita `$it`" }
                }
            }
        assertTrue(fantasmas.isEmpty()) {
            "doc/config viva citando módulo Gradle inexistente (o settings declara $existentes):\n" +
                fantasmas.joinToString("\n")
        }
    }

    /**
     * Nomes de módulo citados numa linha: a task `:nome:alvo` e o caminho `nome/src` ou `nome/build`.
     *
     * As duas formas em que a citação ENVELHECE — comando para copiar e caminho de relatório. Uma
     * menção em prosa ("o domínio") não é citação de módulo e fica de fora, deliberadamente: o alvo
     * aqui é o que o leitor executa, não o que ele lê.
     *
     * A task exige `gradlew` na linha. Sem essa âncora, `:([a-z…]+):` casa **timestamp**: `HH:mm:ss`
     * vira o módulo `mm` (medido — dois falso-positivos).
     */
    private fun modulosCitadosEm(linha: String): List<String> {
        val tasks = if (linha.contains("gradlew")) TASK_GRADLE.findAll(linha) else emptySequence()
        return (tasks + CAMINHO_DE_MODULO.findAll(linha))
            .map { it.groupValues[1] }
            .filterNot { it in METAVARIAVEIS }
            .toList()
    }

    /**
     * Doc e config que descrevem o estado VIGENTE: a árvore `.claude`, a política, e as configs de
     * raiz que repetem gate ou nomeiam módulo.
     *
     * `codecov.yml` e `README.md` entraram no GAP-FB: o guard do GAP-FA só varria `.md` sob `.claude`,
     * e o codecov dizia "JaCoCo >= 96%" — DUAS subidas atrasadas — sem ninguém notar.
     *
     * Sem o glob escrito por extenso: em KDoc ele abre um bloco aninhado e o arquivo para de compilar.
     */
    private fun docsVivas(): List<File> {
        val docs =
            File(raiz, ".claude").walkTopDown().filter { it.isFile && it.extension == "md" }.toList() +
                CONFIGS_VIVAS.map { File(raiz, it) }
        // `adr` e `docs/quality` ficam DE FORA de propósito: ADR é imutável por política e
        // scorecard/audit são snapshots datados — os 97% deles são fato histórico, não drift.
        require(docs.size > CONFIGS_VIVAS.size) { "nenhuma doc viva encontrada — o walk quebrou" }
        docs.forEach { require(it.isFile) { "config viva não encontrada: ${it.absolutePath}" } }
        return docs
    }

    @Test
    fun `o parser reconhece as formas em que um modulo e citado, e so elas`() {
        // A regra acima varre arquivo inteiro; este teste fixa o PARSER, que é onde ela cega em
        // silêncio. Cada linha abaixo custou um falso-negativo ou um falso-positivo medido.
        val reconhecidas =
            mapOf(
                "./gradlew :domain-simulation:pitest" to listOf("domain-simulation"),
                "open domain-simulation/build/reports/pitest/index.html" to listOf("domain-simulation"),
                // P2 do Codex no #406: o separador `::` do codecov.yml. Sem o `:` no conjunto de
                // prefixos, NENHUMA mapeação do codecov casava e a config passava verde sem ser lida.
                """  - "com/kanbanvision/domain/common/::domain-common/src/main/kotlin/x/"""" to listOf("domain-common"),
                "  usecases/src/main/kotlin/com/kanbanvision/usecases/ \\" to listOf("usecases"),
            )
        reconhecidas.forEach { (linha, esperado) ->
            assertEquals(esperado, modulosCitadosEm(linha), "não reconheceu o módulo em: $linha")
        }

        val ignoradas =
            listOf(
                // Timestamp: sem a âncora em `gradlew`, `HH:mm:ss` vira o módulo `mm` (medido).
                "logback pattern: %d{HH:mm:ss.SSS} [%thread]",
                // Metavariável de documentação — ensina a forma, não um módulo.
                "| `./gradlew :modulo:test` | Apenas testes do módulo |",
                // Segmento de PACOTE, não de módulo: `/` fica fora dos prefixos de propósito.
                "com/kanbanvision/domain/src/legado",
                // Menção em prosa não é citação executável.
                "o domínio é puro — zero imports de framework",
            )
        ignoradas.forEach { linha ->
            assertEquals(emptyList<String>(), modulosCitadosEm(linha), "reconheceu módulo onde não há: $linha")
        }
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
        val arquivo = File(raiz, caminhoRelativo)
        require(arquivo.isFile) { "arquivo não encontrado: ${arquivo.absolutePath}" }
        return arquivo.readText()
    }

    private companion object {
        const val ESPELHO = ".claude/rules/stack.md"
        const val CONVENTION_PLUGIN = "buildSrc/src/main/kotlin/kanban.kotlin-common.gradle.kts"

        // Config de raiz que repete gate ou nomeia módulo. O `ci.yml` entra porque nomeia módulo nos
        // caminhos de relatório; os gates dele passaram a ser DERIVADOS do Gradle (GAP-FB), então
        // não há mais número a espelhar lá.
        val CONFIGS_VIVAS =
            listOf(
                "docs/politicas-explicitas.md",
                "codecov.yml",
                "README.md",
                ".github/workflows/ci.yml",
            )

        // `:modulo:task` — a forma que o leitor copia e cola. Só vale em linha com `gradlew`.
        val TASK_GRADLE = Regex(""":([a-z][a-z0-9_-]*):[a-zA-Z]""")

        // Metavariáveis de documentação: `./gradlew :modulo:test` ensina a FORMA, não um módulo.
        // Lista explícita e curta de propósito — placeholder novo reprova o build, e aí é decisão
        // consciente de quem escreve, não silêncio.
        val METAVARIAVEIS = setOf("modulo", "módulo", "mod", "module", "nome-do-modulo")

        // `modulo/src` ou `modulo/build` — a forma que aparece em caminho de relatório. Exige começo
        // de linha ou separador antes, senão `com/kanbanvision/domain/src` casaria o segmento errado
        // (por isso `/` fica DE FORA do conjunto, de propósito).
        //
        // O `:` entrou por P2 do Codex no #406: o `codecov.yml` separa origem e destino com `::`
        // (`"com/kanbanvision/domain/common/::domain-common/src/main/kotlin/…"`), então NENHUMA
        // mapeação dele casava — a config entrava na varredura e saía verde sem ser olhada, que é
        // pior do que não varrer, porque parece cobertura.
        val CAMINHO_DE_MODULO = Regex("""(?:^|[\s"'`(:])([a-z][a-z0-9_-]*)/(?:src|build)/""")
        val PERCENTUAL = 100.toBigDecimal()

        // A linha fala do gate de COBERTURA? Os três vocabulários em uso no repo. O `NO_COVERAGE` do
        // PITest casaria `coverage` e por isso é removido da linha antes — sem isso, metade do texto
        // de mutação viraria falso-positivo.
        val FALA_DE_COBERTURA = Regex("""(?i)\bjacoco|\bcobertura\b|\bcoverage\b""")

        // `(?<!\d)` obrigatório: sem ele o `\d{2}` casa o "00" de "100%" e vira falso-positivo em
        // "cobertura de 100% dos arquivos" (medido — dois sítios reprovaram indevidamente).
        val PERCENTUAL_CITADO = Regex("""(?<!\d)(\d{2})\s*%""")
        val MINIMUM_LITERAL = Regex("""minimum\s*=\s*"([\d.]+)"""")

        val MUTATION_THRESHOLD = Regex("""mutationThreshold\s*\.\s*set\s*\(\s*(\d+)\s*\)""")
        val JACOCO_MINIMUM = Regex("""minimum\s*=\s*"([\d.]+)"""")
        val MODULO_INCLUIDO = Regex("""["']\s*:([A-Za-z0-9_\-]+)\s*["']""")
        val PITEST_ALL_DEP = Regex("""["']\s*:([A-Za-z0-9_\-]+):pitest\s*["']""")

        // No espelho o módulo vem em crase e o gate logo depois: `` `usecases` 55% ``.
        val MODULO_COM_PERCENTUAL = Regex("""`([A-Za-z0-9_\-]+)`\s*(\d+)%""")
        val COBERTURA_NO_ESPELHO = Regex("""≥\s*(\d+)%""")
    }
}
