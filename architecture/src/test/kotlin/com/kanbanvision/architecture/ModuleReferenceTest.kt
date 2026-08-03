package com.kanbanvision.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Fitness functions das REFERÊNCIAS a módulo Gradle em doc e config (GAP-FB/FC).
 *
 * Separada do [QualityGateMirrorTest] quando ele estourou o `LargeClass` — e a separação vale por si:
 * aquele compara **valores de gate**, este compara **nomes de módulo**. Guard de igualdade contra guard
 * de existência, que é a distinção que o GAP-FB aprendeu: a ADR-0038 dividiu `:domain` em três e a
 * documentação seguiu mandando rodar `./gradlew :domain:pitest` por quase um ano — nenhuma regra de
 * igualdade pegaria isso, e esta pega no dia.
 *
 * **Limite declarado, não escondido:** a lista de formas de citação é incompleta por construção. O que
 * substitui a esperança é o método — rodar o reconhecedor contra o repo inteiro e olhar o que ele
 * **não** acusou. Foi assim que `mod/build.gradle.kts` e `COPY mod/` apareceram (GAP-FC), vivos no
 * merge do PR anterior, um deles no arquivo que aquele PR curou.
 */
class ModuleReferenceTest {
    @Test
    fun `nenhuma doc ou config viva cita modulo Gradle que nao existe`() {
        val existentes = modulosDoSettings().toSet()
        val fantasmas =
            docsVivas().flatMap { arquivo ->
                arquivo.readText().lines().withIndex().flatMap { (i, linha) ->
                    val local = "${arquivo.relativeTo(raizDoRepo).path}:${i + 1}"
                    modulosCitadosEm(linha)
                        .filterNot { it in existentes }
                        .map { "$local — cita o módulo `$it`" } +
                        diretoriosCopiadosEm(linha)
                            .filterNot { File(raizDoRepo, it).isDirectory }
                            .map { "$local — copia o diretório `$it/`, que não existe" }
                }
            }
        assertTrue(fantasmas.isEmpty()) {
            "doc/config viva citando módulo Gradle inexistente (o settings declara $existentes):\n" +
                fantasmas.joinToString("\n")
        }
    }

    @Test
    fun `o ci enxerga exatamente os mesmos modulos que o guard`() {
        // O GAP-FB trocou cópia por DERIVAÇÃO no ci.yml — e criou um SEGUNDO parser do mesmo
        // settings.gradle.kts, com regex mais estreito (`":[a-z_-]+"`) que o daqui. Hoje concordam
        // por acaso: os 7 módulos são minúsculos com `_-`. Um módulo com dígito, maiúscula ou aspas
        // simples sumiria EM SILÊNCIO das duas tabelas de report do CI — que é exatamente o modo de
        // falha da lista hardcoded que o GAP-FB removeu, reintroduzido um nível abaixo.
        //
        // O regex vem do PRÓPRIO workflow, não de uma cópia aqui: derivação, não terceira fonte.
        val regexDoCi = regexDeModulosNoWorkflow()
        val vistosPeloCi =
            regexDoCi
                .findAll(ler("settings.gradle.kts"))
                .map { it.value.trim('"', ':') }
                .toSet()
        assertEquals(
            modulosDoSettings().toSet(),
            vistosPeloCi,
            "o `grep -oE` do ci.yml enumera um conjunto de módulos diferente do guard (regex do CI: ${regexDoCi.pattern}). " +
                "Um módulo fora dele some das tabelas de PITest e Detekt sem aviso.",
        )
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
                // GAP-FC: a forma MAIS comum de citar módulo neste repo, e a que o GAP-FB não via —
                // `CAMINHO_DE_MODULO` exige `build/` com barra, e aqui é `build.`.
                "// domain-simulation/build.gradle.kts" to listOf("domain-simulation"),
                "veja usecases/build.gradle.kts para o gate" to listOf("usecases"),
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

        // A checagem de `COPY` é por EXISTÊNCIA no disco, não por settings — `gradle/` e `config/` são
        // diretórios reais e não são módulos Gradle, e a primeira versão da regra os acusou (medido).
        assertEquals(listOf("domain"), diretoriosCopiadosEm("COPY domain/ domain/"), "origem e destino são o mesmo nome")
        assertEquals(listOf("gradle"), diretoriosCopiadosEm("COPY gradle/ gradle/"))
        assertEquals(emptyList<String>(), diretoriosCopiadosEm("RUN rm -rf domain/"), "só linha COPY conta")
        assertEquals(emptyList<String>(), diretoriosCopiadosEm("COPY gradlew gradlew.bat ./"), "sem `/` no fim não é diretório")
    }

    /**
     * Nomes de módulo citados numa linha: a task `:nome:alvo`, o caminho `nome/src` ou `nome/build`, e
     * o script `nome/build.gradle.kts`.
     *
     * As formas em que a citação ENVELHECE — comando para copiar e caminho de arquivo. Uma menção em
     * prosa ("o domínio") fica de fora, deliberadamente: o alvo é o que o leitor executa.
     *
     * A task exige `gradlew` na linha. Sem essa âncora, `:([a-z…]+):` casa **timestamp**: `HH:mm:ss`
     * vira o módulo `mm` (medido — dois falso-positivos).
     */
    private fun modulosCitadosEm(linha: String): List<String> {
        val tasks = if (linha.contains("gradlew")) TASK_GRADLE.findAll(linha) else emptySequence()
        return (tasks + CAMINHO_DE_MODULO.findAll(linha) + SCRIPT_DE_MODULO.findAll(linha))
            .map { it.groupValues[1] }
            .filterNot { it in METAVARIAVEIS || it in MODULOS_HIPOTETICOS }
            .distinct()
            .toList()
    }

    /**
     * Diretórios copiados numa linha `COPY` de Dockerfile — checados por EXISTÊNCIA no disco, não por
     * pertencimento ao settings.
     *
     * Medido: a primeira versão desta regra usava o settings e acusou `gradle/` e `config/`, que são
     * diretórios reais e não são módulos Gradle. O defeito que ela existe para pegar — `COPY domain/`
     * num snippet que se diz espelho do Dockerfile real — é **diretório que não existe mais**, e é
     * isso que a checagem tem de perguntar. Guard novo acusando sítio legítimo é falso-positivo da
     * PERGUNTA, não do sítio: conserte o predicado antes de criar exceção.
     */
    private fun diretoriosCopiadosEm(linha: String): List<String> =
        if (!linha.trimStart().startsWith("COPY ")) {
            emptyList()
        } else {
            DIRETORIO_COPIADO
                .findAll(linha)
                .map { it.groupValues[1] }
                .distinct()
                .toList()
        }

    /** O literal do `grep -oE '…'` que o workflow usa para enumerar módulos. */
    private fun regexDeModulosNoWorkflow(): Regex {
        val padroes =
            GREP_DE_MODULOS
                .findAll(ler(".github/workflows/ci.yml"))
                .map { it.groupValues[1] }
                .toSet()
        // Um só, e conhecido: se o workflow passar a ter duas formas diferentes de enumerar módulo,
        // esta regra tem de falhar aqui em vez de escolher uma e seguir.
        return Regex(
            padroes.singleOrNull()
                ?: error("esperava exatamente um `grep -oE` de módulos no ci.yml, achei $padroes"),
        )
    }

    private companion object {
        // `:modulo:task` — a forma que o leitor copia e cola. Só vale em linha com `gradlew`.
        val TASK_GRADLE = Regex(""":([a-z][a-z0-9_-]*):[a-zA-Z]""")

        // Metavariáveis de documentação: `./gradlew :modulo:test` ensina a FORMA, não um módulo.
        // Lista explícita e curta de propósito — placeholder novo reprova o build, e aí é decisão
        // consciente de quem escreve, não silêncio.
        val METAVARIAVEIS = setOf("modulo", "módulo", "mod", "module", "nome-do-modulo")

        // Módulos que a `/microservices-modular-monolith` PROPÕE num esboço de extração futura — não
        // existem, e não deviam: o esboço perde o sentido se citar só o que já existe.
        val MODULOS_HIPOTETICOS = setOf("usecases-api", "usecases-impl")

        // `modulo/src` ou `modulo/build` — caminho de relatório. Exige começo de linha ou separador
        // antes, senão `com/kanbanvision/domain/src` casaria o segmento errado (por isso `/` fica DE
        // FORA do conjunto). O `:` entrou por P2 do Codex no #406: o `codecov.yml` separa origem e
        // destino com `::`, então NENHUMA mapeação dele casava — a config entrava na varredura e saía
        // verde sem ser olhada, que é pior do que não varrer, porque parece cobertura.
        val CAMINHO_DE_MODULO = Regex("""(?:^|[\s"'`(:])([a-z][a-z0-9_-]*)/(?:src|build)/""")

        // `modulo/build.gradle.kts` — a forma MAIS comum de citar módulo neste repo, e a que o
        // GAP-FB não viu: o regex acima exige `build/` com barra, e aqui é `build.` (#406, colheita).
        val SCRIPT_DE_MODULO = Regex("""(?:^|[\s"'`(:])([a-z][a-z0-9_-]*)/build\.gradle\.kts""")

        // `COPY modulo/ modulo/` — forma de Dockerfile. Restrita a linha `COPY` de propósito: um
        // `nome/` solto é comum demais em prosa para virar sinal.
        val DIRETORIO_COPIADO = Regex("""(?:^|\s)([a-z][a-z0-9_-]*)/(?=\s|$)""")

        // O `grep -oE '<padrão>' settings.gradle.kts` do workflow, com o padrão no grupo 1.
        val GREP_DE_MODULOS = Regex("""grep -oE '([^']+)' settings\.gradle\.kts""")
    }
}
