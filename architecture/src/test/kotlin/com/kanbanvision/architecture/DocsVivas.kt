package com.kanbanvision.architecture

import java.io.File

/*
 * O conjunto de doc/config que descreve o estado VIGENTE do repo — compartilhado pelas fitness
 * functions que caçam drift ([QualityGateMirrorTest], [StackVersionMirrorTest]).
 *
 * Mora aqui, e não privado em cada teste, porque "quais arquivos contam como doc viva" é exatamente o
 * tipo de lista que apodrece em duplicata: dois guards com listas próprias divergem no primeiro arquivo
 * novo, e o que sobra é um guard que varre menos do que o leitor supõe. Foi o defeito do GAP-FA — o
 * guard varria só `.claude`, e o `codecov.yml` dizia "JaCoCo >= 96%" havia duas subidas.
 */

/**
 * `adr/` e `docs/quality/` ficam DE FORA de propósito: ADR é imutável por política e scorecard/audit são
 * snapshots datados — um número velho lá é fato histórico, não drift.
 */
internal fun docsVivas(raiz: File): List<File> {
    val docs =
        File(raiz, ".claude").walkTopDown().filter { it.isFile && it.extension == "md" }.toList() +
            CONFIGS_VIVAS.map { File(raiz, it) }
    require(docs.size > CONFIGS_VIVAS.size) { "nenhuma doc viva encontrada — o walk quebrou" }
    docs.forEach { require(it.isFile) { "config viva não encontrada: ${it.absolutePath}" } }
    return docs
}

/**
 * Config de raiz que repete gate, nomeia módulo ou declara versão.
 *
 * O `ci.yml` entra porque nomeia módulo nos caminhos de relatório e fixa a versão da action de SCA; os
 * gates dele passaram a ser DERIVADOS do Gradle (GAP-FB) e o rodapé do Supply Chain Report passou a
 * derivar a versão do scanner do próprio `uses:` (GAP-FF).
 *
 * O `CLAUDE.md` entrou no GAP-FF: é a porta de entrada do repo, declara a versão do Gradle, e nenhum
 * guard olhava para ele.
 */
internal val CONFIGS_VIVAS =
    listOf(
        "docs/politicas-explicitas.md",
        "codecov.yml",
        "README.md",
        ".github/workflows/ci.yml",
        "CLAUDE.md",
    )
