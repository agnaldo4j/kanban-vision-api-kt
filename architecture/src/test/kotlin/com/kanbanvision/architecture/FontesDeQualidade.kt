package com.kanbanvision.architecture

import java.io.File

/*
 * Leitura das FONTES que os guards de qualidade comparam — compartilhada por
 * [QualityGateMirrorTest] (valores de gate) e [ModuleReferenceTest] (nomes de módulo).
 *
 * Mora aqui, e não numa das classes, porque as duas leem o mesmo conjunto de arquivos: duplicar o
 * leitor faria as duas divergirem no dia em que uma ganhasse um arquivo novo — que é literalmente a
 * classe de defeito que estes guards existem para pegar (`testing.md`: fixture de classe dividida vai
 * para o `*TestSupport`, não para cada cópia).
 */

/** `workingDir` do teste é o projectDir do módulo; a raiz vem por systemProperty (ver build.gradle.kts). */
internal val raizDoRepo: File get() = System.getProperty("rootDir")?.let(::File) ?: File("..")

internal const val ESPELHO = ".claude/rules/stack.md"
internal const val CONVENTION_PLUGIN = "buildSrc/src/main/kotlin/kanban.kotlin-common.gradle.kts"

/**
 * Config de raiz que repete gate ou nomeia módulo.
 *
 * O `ci.yml` entra porque nomeia módulo nos caminhos de relatório; os gates dele passaram a ser
 * DERIVADOS do Gradle (GAP-FB), então não há mais número a espelhar lá.
 */
internal val CONFIGS_VIVAS =
    listOf(
        "docs/politicas-explicitas.md",
        "codecov.yml",
        "README.md",
        ".github/workflows/ci.yml",
    )

internal fun ler(caminhoRelativo: String): String {
    val arquivo = File(raizDoRepo, caminhoRelativo)
    require(arquivo.isFile) { "arquivo não encontrado: ${arquivo.absolutePath}" }
    return arquivo.readText()
}

/**
 * Doc e config que descrevem o estado VIGENTE: a árvore `.claude`, as configs de raiz e os scripts de
 * build.
 *
 * `codecov.yml` e `README.md` entraram no GAP-FB: o guard do GAP-FA só varria `.md` sob `.claude`, e o
 * codecov dizia "JaCoCo >= 96%" — duas subidas atrasadas — sem ninguém notar.
 *
 * `adr` e `docs/quality` ficam DE FORA de propósito: ADR é imutável por política e scorecard/audit são
 * snapshots datados — os 97% deles são fato histórico, não drift.
 *
 * Sem o glob escrito por extenso: em KDoc ele abre um bloco aninhado e o arquivo para de compilar.
 */
internal fun docsVivas(): List<File> {
    val docs =
        File(raizDoRepo, ".claude").walkTopDown().filter { it.isFile && it.extension == "md" }.toList() +
            CONFIGS_VIVAS.map { File(raizDoRepo, it) } +
            scriptsDeBuild()
    require(docs.size > CONFIGS_VIVAS.size) { "nenhuma doc viva encontrada — o walk quebrou" }
    docs.forEach { require(it.isFile) { "config viva não encontrada: ${it.absolutePath}" } }
    return docs
}

/**
 * Os `build.gradle.kts` — a VERDADE dos gates, e por isso mesmo o único ponto cego garantido.
 *
 * Ficavam fora da varredura por serem a fonte, e o guard roda [semComentarios] ao lê-los como fonte —
 * então a **prosa** deles não era olhada por ponta nenhuma. `http_api/build.gradle.kts` dizia
 * "JaCoCo 97%" num comentário (colheita do #406). Aqui entram com o texto cru: a linha
 * `minimum = "0.98"` bate com o autoritativo e passa; um comentário stale reprova.
 */
internal fun scriptsDeBuild(): List<File> =
    (modulosDoSettings() + "buildSrc/src/main/kotlin").map { File(raizDoRepo, it) }.flatMap { alvo ->
        when {
            alvo.isDirectory -> alvo.walkTopDown().filter { it.name.endsWith(".gradle.kts") }.toList()
            else -> listOf(File(alvo, "build.gradle.kts")).filter { it.isFile }
        }
    }

internal fun modulosDoSettings(): List<String> =
    MODULO_INCLUIDO
        .findAll(ler("settings.gradle.kts").semComentarios())
        .map { it.groupValues[1] }
        .toList()
        .also { require(it.isNotEmpty()) { "nenhum módulo lido do settings.gradle.kts — o parser quebrou" } }

internal fun modulosDoPitestAll(): Set<String> =
    PITEST_ALL_DEP
        .findAll(ler("build.gradle.kts").semComentarios())
        .map { it.groupValues[1] }
        .toSet()

internal fun gatesDeMutacaoNoGradle(): Map<String, Int> =
    modulosDoSettings()
        .mapNotNull { modulo ->
            MUTATION_THRESHOLD
                .find(ler("$modulo/build.gradle.kts").semComentarios())
                ?.let { modulo to it.groupValues[1].toInt() }
        }.toMap()

/** `minimum = "0.98"` → 98. Percentual inteiro para comparar com o texto do espelho sem float. */
internal fun coberturaEm(caminho: String): Int {
    val bruto =
        requireNotNull(JACOCO_MINIMUM.find(ler(caminho).semComentarios())) {
            "nenhum `minimum = \"…\"` de JaCoCo encontrado em $caminho"
        }.groupValues[1]
    return (bruto.toBigDecimal() * PERCENTUAL).toInt()
}

internal fun linhaDoEspelho(rotulo: String): String =
    ler(ESPELHO)
        .lines()
        .singleOrNull { it.startsWith("| $rotulo |") }
        ?: error("$ESPELHO deve ter exatamente uma linha de tabela começando por `| $rotulo |`")

internal val PERCENTUAL = 100.toBigDecimal()
internal val MUTATION_THRESHOLD = Regex("""mutationThreshold\s*\.\s*set\s*\(\s*(\d+)\s*\)""")
internal val JACOCO_MINIMUM = Regex("""minimum\s*=\s*"([\d.]+)"""")
internal val MODULO_INCLUIDO = Regex("""["']\s*:([A-Za-z0-9_\-]+)\s*["']""")
internal val PITEST_ALL_DEP = Regex("""["']\s*:([A-Za-z0-9_\-]+):pitest\s*["']""")
