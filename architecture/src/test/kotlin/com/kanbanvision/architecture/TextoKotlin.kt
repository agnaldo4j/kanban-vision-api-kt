package com.kanbanvision.architecture

/*
 * Varredura textual de fonte Kotlin, compartilhada pelas fitness functions.
 *
 * As regras deste módulo são shape-based por construção — o Konsist expõe declarações, não uma AST de
 * expressões. O que estas funções fazem é tirar do texto o que **não é código** (comentário, literal) e
 * percorrer cadeias de chamada respeitando ANINHAMENTO, que é onde todo regex ingênuo falha:
 * `[^}]*` termina na primeira `}`, e uma lambda dentro de outra dribla o guard (Codex P2 no #400).
 */

/**
 * Remove comentários e o TEXTO de literais, preservando a interpolação `${'$'}{…}`.
 *
 * A interpolação é código executável: `"""${'$'}{org.slf4j.LoggerFactory.getLogger("a")}"""` cria a
 * dependência que o guard de pureza existe para rejeitar, e apagar a raw string inteira a esconderia
 * (Codex P2 no #400).
 *
 * Ordem deliberada: literais ANTES de comentários. O inverso faz `"http://exemplo"` perder o resto da
 * linha para o descarte de `//`.
 */
internal fun String.semComentariosEStrings(): String =
    replace(RAW_STRING) { conteudoInterpolado(it.value) }
        .replace(STRING_SIMPLES) { conteudoInterpolado(it.value) }
        .replace(BLOCO_DE_COMENTARIO, " ")
        .replace(LINHA_DE_COMENTARIO, " ")

/**
 * Remove comentários e PRESERVA literais — para quem lê valor, não forma.
 *
 * Não dá para reusar a [semComentariosEStrings] acima: aquela apaga o texto de string, e um gate como
 * `minimum = "0.98"` está DENTRO de uma string.
 *
 * **Varredura, não regex: um glob dentro de string abre um bloco de comentário que não existe.** A
 * forma anterior — regex de bloco não-guloso mais descarte das linhas iniciadas por barra-barra —
 * estava copiada verbatim em `ContextBoundaryTest`, `ContractPackageTest` e
 * `ProjectDependencyGraphTest`, e todas as três carregam o mesmo defeito: em
 * `http_api/build.gradle.kts` o `exclude` de `META-INF/native-image` termina em barra-asterisco-asterisco
 * e abre um bloco que só fecha ~150 linhas abaixo, **engolindo o `mutationThreshold` no caminho**
 * (medido no GAP-FA — o [QualityGateMirrorTest] nasceu vermelho por isso, e o teste do stripper lá
 * fixa o caso). Aqui a string é copiada inteira antes de qualquer decisão sobre comentário.
 *
 * Nota de escrita: este KDoc evita a sequência literal do glob de propósito — dentro de comentário ela
 * abriria um bloco ANINHADO e o arquivo pararia de compilar, que é a armadilha vizinha já registrada
 * em `.claude/rules/kotlin-quality.md`. Custou uma compilação quebrada aqui.
 */
internal fun String.semComentarios(): String {
    val saida = StringBuilder(length)
    var i = 0
    while (i < length) {
        i =
            when {
                startsWith("//", i) -> pulaAteFimDaLinha(i)
                startsWith("/*", i) -> pulaBloco(i)
                startsWith("\"\"\"", i) -> copiaLiteral(saida, i, "\"\"\"", escapavel = false)
                this[i] == '"' -> copiaLiteral(saida, i, "\"", escapavel = true)
                else -> {
                    saida.append(this[i])
                    i + 1
                }
            }
    }
    return saida.toString()
}

/** O `\n` fica: descartar a linha inteira juntaria tokens de linhas vizinhas. */
private fun String.pulaAteFimDaLinha(de: Int): Int = indexOf('\n', de).takeIf { it >= 0 } ?: length

private fun String.pulaBloco(de: Int): Int = indexOf("*/", de + 2).takeIf { it >= 0 }?.plus(2) ?: length

/** Copia o literal inteiro, delimitadores incluídos, e devolve o índice logo após ele. */
private fun String.copiaLiteral(
    saida: StringBuilder,
    de: Int,
    delimitador: String,
    escapavel: Boolean,
): Int {
    var i = de + delimitador.length
    saida.append(delimitador)
    while (i < length) {
        if (escapavel && this[i] == '\\' && i + 1 < length) {
            saida.append(this, i, i + 2)
            i += 2
            continue
        }
        if (startsWith(delimitador, i)) {
            saida.append(delimitador)
            return i + delimitador.length
        }
        // String simples não atravessa linha: sem isso, uma aspa desbalanceada engoliria o resto do arquivo.
        if (escapavel && this[i] == '\n') return i
        saida.append(this[i])
        i++
    }
    return length
}

/** Só as expressões `${'$'}{…}` do literal — o texto literal vira nada. */
private fun conteudoInterpolado(literal: String): String =
    INTERPOLACAO
        .findAll(literal)
        .joinToString(" ") { it.groupValues[1] }
        .ifEmpty { "\"\"" }

/**
 * O [receptor] alcança [chamada]? Cobre a chamada direta, a cadeia de scope functions e a chamada
 * DENTRO do bloco — com casamento de chaves, então lambda aninhada não escapa.
 *
 * Exige operador de encadeamento explícito (`.`/`?.`/`::`) depois de cada bloco: sem isso o padrão
 * atravessava fronteira de statement e `repo.let { … }` seguido de um `findById(…)` solto na linha de
 * baixo virava falso-positivo (Copilot no #400).
 */
internal fun alcanca(
    corpo: String,
    receptor: String,
    chamada: String,
): Boolean {
    val inicio = Regex("""\b${Regex.escape(receptor)}\b""")
    return inicio.findAll(corpo).any { ocorrencia ->
        cadeiaAlcanca(corpo, ocorrencia.range.last + 1, chamada)
    }
}

private fun cadeiaAlcanca(
    corpo: String,
    apos: Int,
    chamada: String,
): Boolean {
    var i = apos
    while (true) {
        val elo = eloEm(corpo, i) ?: return false
        // A chamada é o próprio elo (`repo.let { … }.findById(…)`) ou está DENTRO de um bloco dele
        // (`repo.let { it.findById(id) }`).
        if (elo.nome == chamada || elo.blocos.any { it.contains(chamada) }) return true
        i = elo.fim
    }
}

/** Um elo da cadeia: `.nome` seguido dos blocos `{…}`/`(…)` que o acompanham. */
private data class Elo(
    val nome: String,
    val blocos: List<String>,
    val fim: Int,
)

private fun eloEm(
    corpo: String,
    apos: Int,
): Elo? {
    val noOperador = pulaEspacos(corpo, apos)
    val operador = OPERADOR_DE_CADEIA.matchAt(corpo, noOperador) ?: return null
    val nome = IDENTIFICADOR.matchAt(corpo, pulaEspacos(corpo, noOperador + operador.value.length)) ?: return null
    val blocos = blocosApos(corpo, pulaEspacos(corpo, nome.range.last + 1))
    return blocos?.let { (textos, fim) -> Elo(nome.value, textos, fim) }
}

/** Blocos consecutivos a partir de [de], com casamento; `null` se desbalanceado. */
private fun blocosApos(
    corpo: String,
    de: Int,
): Pair<List<String>, Int>? {
    var i = de
    val blocos = mutableListOf<String>()
    while (i < corpo.length && (corpo[i] == '{' || corpo[i] == '(')) {
        val fecha = fechamentoDe(corpo, i) ?: return null
        blocos += corpo.substring(i, fecha)
        i = pulaEspacos(corpo, fecha + 1)
    }
    return blocos to i
}

private fun pulaEspacos(
    texto: String,
    de: Int,
): Int {
    var i = de
    while (i < texto.length && texto[i].isWhitespace()) i++
    return i
}

/** Índice do delimitador que fecha o aberto em [inicio], respeitando aninhamento. */
private fun fechamentoDe(
    texto: String,
    inicio: Int,
): Int? {
    val abre = texto[inicio]
    val fecha = if (abre == '{') '}' else ')'
    var nivel = 0
    for (i in inicio until texto.length) {
        when (texto[i]) {
            abre -> nivel++
            fecha -> {
                nivel--
                if (nivel == 0) return i
            }
        }
    }
    return null
}

private val RAW_STRING = Regex(""""{3}[\s\S]*?"{3}""")
private val STRING_SIMPLES = Regex(""""(?:[^"\\\n]|\\.)*"""")
private val BLOCO_DE_COMENTARIO = Regex("""/\*[\s\S]*?\*/""")
private val LINHA_DE_COMENTARIO = Regex("""//[^\n]*""")
private val INTERPOLACAO = Regex("""\$\{([^{}]*)}""")
private val OPERADOR_DE_CADEIA = Regex("""\?\.|::|\.""")
private val IDENTIFICADOR = Regex("""[A-Za-z_]\w*""")
