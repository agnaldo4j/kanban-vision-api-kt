package com.kanbanvision.architecture

/**
 * Parte PURA do grafo de injeção por construtor do Koin (AppModule), extraída para ser testável em
 * isolamento (fixtures em [DiWiringCycleTest]) — espelha o split [ClassNode] ↔ [ClassCycleTest].
 *
 * Fecha o blind spot de MAIOR severidade da skill `/circular-dependency-control`: um ciclo de injeção
 * por construtor (`A(B)`, `B(A)`) faz o Koin estourar `StackOverflowError` ao montar o objeto. Ele é
 * invisível ao [ClassCycleTest] (composição intra-pacote) e ao [PackageCycleTest] (grafo de import)
 * porque o wiring é montado no `AppModule`, cruzando pacotes/módulos e resolvido por TIPO (`get()`),
 * não por import. Estático (respeita "sem call-graph de runtime"): o grafo vem das assinaturas de
 * construtor + do mapa porta→impl declarado no `AppModule`, nunca de execução.
 */
internal data class KoinBindings(
    /** Nomes simples das classes concretas instanciadas por um `single { ... }`. */
    val components: Set<String>,
    /** Tipo declarado (porta/interface OU o próprio concreto) → impl concreta que o Koin resolve. */
    val resolvesTo: Map<String, String>,
)

/**
 * Conta as DECLARAÇÕES do DSL Koin no texto — oráculo INDEPENDENTE do parser abaixo.
 *
 * GAP-EX: a âncora anterior era `components.containsAll(3 nomes)` — um piso, não completude. O parser
 * já era cego a `single<Clock> { Clock.systemUTC() }` (13 de 14 bindings do AppModule), e `singleOf`,
 * `factory` e lambda com chaves aninhadas passavam invisíveis: um ciclo real entre dois `singleOf`
 * daria VERDE aqui e `StackOverflowError` em produção. Comparando esta contagem com o que o parser
 * extraiu, uma forma nova do DSL reprova o gate em vez de sumir dele.
 */
internal fun countKoinDeclarations(appModuleText: String): Int = DECLARACAO_DSL.findAll(stripKotlinComments(appModuleText)).count()

// O tipo declarado é casado com aninhamento de UM nível (`<Port<Foo>>`) — `<[^>]*>` parava no `>`
// interno, a declaração inteira sumia do parser E do contador, então a completude não a pegava
// (Codex P2 no #398).
private val DECLARACAO_DSL = Regex("""\b(?:single|factory|scoped)(?:Of)?\s*(?:<[^<>]*(?:<[^<>]*>)?[^<>]*>)?\s*[({]""")

private fun stripKotlinComments(text: String): String =
    Regex("""/\*.*?\*/""", setOf(RegexOption.DOT_MATCHES_ALL))
        .replace(text, "")
        .lines()
        .filterNot { it.trimStart().startsWith("//") }
        .joinToString("\n")

/**
 * Extrai [KoinBindings] do TEXTO do `AppModule`. Reconhece as formas do DSL:
 *  - `single<Porta> { Impl(...) }`            → resolvesTo[Porta] = Impl
 *  - `single { Impl(...) } bind Porta::class` → resolvesTo[Porta] = Impl
 *  - `single { Impl(...) }`                   → resolvesTo[Impl]  = Impl
 *  - `singleOf(::Impl)` / `factoryOf(::Impl)` → resolvesTo[Impl]  = Impl
 *  - `single<Porta> { Porta.factory() }`      → resolvesTo[Porta] = Porta   (sink: sem construtor)
 * Em todas, o concreto também resolve a si mesmo (o Koin registra o tipo concreto além do bound).
 *
 * O corpo da lambda é delimitado por CASAMENTO DE CHAVES, não por `[^{}]*` — a versão anterior pulava
 * silenciosamente qualquer binding com chaves aninhadas (`single { X(get()).also { … } }`).
 */
internal fun parseKoinBindings(appModuleText: String): KoinBindings {
    val cleaned = stripKotlinComments(appModuleText)
    val components = mutableSetOf<String>()
    val resolvesTo = mutableMapOf<String, String>()

    DECLARACAO_DSL.findAll(cleaned).forEach { decl ->
        val tipoDeclarado = Regex("""<([\w.]+)>""").find(decl.value)?.groupValues?.get(1)
        val abre = decl.range.last

        val (impl, fim) =
            if (cleaned[abre] == '(') {
                val fecha = matchingBrace(cleaned, abre, '(', ')') ?: return@forEach
                val alvo = Regex("""::(\w+)""").find(cleaned.substring(abre, fecha))?.groupValues?.get(1)
                (alvo ?: tipoDeclarado) to fecha
            } else {
                val fecha = matchingBrace(cleaned, abre, '{', '}') ?: return@forEach
                val corpo = cleaned.substring(abre + 1, fecha)
                (construtorEm(corpo) ?: tipoDeclarado) to fecha
            }

        if (impl == null) return@forEach
        components += impl
        resolvesTo[impl] = impl
        tipoDeclarado?.let { resolvesTo[it] = impl }
        Regex("""^\s*bind\s+([\w.]+)::class""")
            .find(cleaned.substring(fim + 1))
            ?.groupValues
            ?.get(1)
            ?.let { resolvesTo[it] = impl }
    }
    return KoinBindings(components, resolvesTo)
}

/**
 * Nome do TIPO construído no corpo da lambda — a última chamada `Maiúscula(` do corpo, ou `null`.
 *
 * Três defeitos que a heurística "primeira chamada qualquer" tinha (Copilot + Codex P2 no #398):
 *  - `single<Clock> { Clock.systemUTC() }` extraía `systemUTC` como componente. O KDoc desta função
 *    dizia que esse caso vira sink pelo tipo declarado — e não virava.
 *  - `single { audit(); A(get()) }` extraía `audit`, deixando `A` e qualquer ciclo dele fora do grafo.
 *  - `single { run { A(get()) } }` só funcionava por acaso.
 *
 * A completude NÃO pegava nada disso: contava 1 componente por declaração, apenas o componente errado.
 *
 * Inicial maiúscula porque em Kotlin construtor e função são indistinguíveis na sintaxe de chamada —
 * a convenção de nomes é o único sinal disponível numa análise textual. `Clock.systemUTC()` é
 * descartado porque `systemUTC` é minúsculo, e `Foo.create(...)` cai no tipo declarado, que é o
 * comportamento correto para uma factory. Limite honesto: `single { Foo.Builder().build() }` extrairia
 * `Builder`; a completude continua garantindo 1 componente por declaração, e o grafo trata tipo fora
 * do escopo de produção como sink.
 */
private fun construtorEm(corpo: String): String? =
    Regex("""\b([A-Z]\w*)\s*\(""")
        .findAll(corpo)
        .lastOrNull()
        ?.groupValues
        ?.get(1)

/** Índice do delimitador que fecha o aberto em [inicio], respeitando aninhamento; `null` se desbalanceado. */
private fun matchingBrace(
    text: String,
    inicio: Int,
    abre: Char,
    fecha: Char,
): Int? {
    var nivel = 0
    for (i in inicio until text.length) {
        when (text[i]) {
            abre -> nivel++
            fecha -> {
                nivel--
                if (nivel == 0) return i
            }
        }
    }
    return null
}

/**
 * Grafo `impl → {impl}`: para cada componente, uma aresta a cada parâmetro de construtor cujo TIPO
 * resolve (via [KoinBindings.resolvesTo]) a um componente — **inclusive ao próprio** (`single { A(get()) }`
 * com `class A(other: A)` é uma self-injection que estoura o Koin: um ciclo real `A → A`, que o [findCycle]
 * detecta como self-loop). Isso difere do grafo de composição de classe ([buildClassGraph]), onde uma
 * self-ref (`TreeNode(children: List<TreeNode>)`) é legítima e por isso descartada. [ctorParamTypes]
 * devolve os nomes simples dos tipos dos parâmetros do construtor primário de um componente — vazio
 * quando a classe não está no escopo de produção (um tipo de biblioteca, ex.: `PrometheusMeterRegistry`,
 * vira sink).
 */
internal fun buildInjectionGraph(
    bindings: KoinBindings,
    ctorParamTypes: (String) -> List<String>,
): Map<String, Set<String>> {
    val edges = mutableMapOf<String, Set<String>>()
    for (impl in bindings.components) {
        val targets =
            ctorParamTypes(impl)
                .mapNotNull { bindings.resolvesTo[it] }
                .filterTo(mutableSetOf()) { it in bindings.components }
        if (targets.isNotEmpty()) edges[impl] = targets
    }
    return edges
}
