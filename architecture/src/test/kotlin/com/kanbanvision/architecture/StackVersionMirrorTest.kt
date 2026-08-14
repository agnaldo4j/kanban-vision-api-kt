package com.kanbanvision.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Fitness function do espelho de VERSÕES (GAP-FF).
 *
 * Irmã do [QualityGateMirrorTest], que amarra os *gates*; esta amarra os *números de versão*, e nasceu
 * do mesmo modo de falha visto de novo: a rodada de dependabot #410/#408/#411 subiu Ktor, Flyway,
 * JUnit, CycloneDX, osv-scanner, OTel e o wrapper do Gradle, e deixou **sete** divergências só no
 * espelho — mais nove coordenadas apodrecidas em três skills que ninguém abriu (logstash 8.0 quando o
 * build já estava em 9.0, micrometer 1.14.4 contra 1.17.0, Flyway 12.10.0 contra 13.2.0).
 *
 * O dependabot atualiza `build.gradle.kts` e a linha `uses:` do workflow. **Prosa ele não vê** — e é
 * por isso que a cópia em doc não pode ser mantida à mão. A lição do Codex P2 no #405 é literal aqui:
 * corrigir os dígitos não é conserto, é adiar o próximo apodrecimento com o guard verde.
 *
 * Topologia imposta, a mesma do GAP-FA: a VERDADE é o Gradle (mais o `uses:` do `ci.yml` para action e
 * o `gradle-wrapper.properties` para o wrapper), e todo texto que declara versão tem de bater com ela.
 *
 * Limite honesto: é casamento textual, como todo guard deste módulo — o Konsist não lê Gradle. O que
 * impede isso de virar silêncio é a exigência de casamento ÚNICO por componente: reescreveu a linha do
 * espelho, o teste fica vermelho pedindo para acertar o padrão, em vez de passar sem ter olhado. Foi
 * assim que ele nasceu vermelho — `Gradle plugin` casava o do PITest e o do CycloneDX ao mesmo tempo.
 */
class StackVersionMirrorTest {
    /** `workingDir` do teste é o projectDir do módulo; a raiz vem por systemProperty (ver build.gradle.kts). */
    private val raiz = System.getProperty("rootDir")?.let(::File) ?: File("..")

    @Test
    fun `o espelho em stack md declara a versao vigente de cada componente rastreado`() {
        val espelho = ler(ESPELHO)
        val divergentes =
            componentes().mapNotNull { (nome, padrao, vigente) ->
                val declaradas = padrao.findAll(espelho).map { it.groupValues[1] }.toList()
                val declarada =
                    declaradas.singleOrNull()
                        ?: return@mapNotNull "$nome — o padrão casou ${declaradas.size}x em $ESPELHO " +
                            "(esperado 1); a linha mudou de forma, acerte o padrão"
                "$nome — espelho diz $declarada, o build diz $vigente".takeIf { declarada != vigente }
            }
        assertTrue(divergentes.isEmpty()) {
            "versão divergente entre o build e o espelho em $ESPELHO. A verdade é o build:\n" +
                divergentes.joinToString("\n")
        }
    }

    @Test
    fun `nenhuma doc viva declara coordenada Gradle com versao divergente`() {
        // Uma coordenada `grupo:artefato:versão` em doc é snippet para copiar e colar — declaração de
        // estado vigente, não prosa. Por isso a regra é exata e sem heurística de "menção histórica":
        // se o leitor copiar aquilo, tem de compilar contra o que o repo usa hoje.
        val vigentes = coordenadasDoGradle()
        val divergentes =
            docsVivas(raiz).flatMap { arquivo ->
                arquivo.readText().lines().withIndex().flatMap { (i, linha) ->
                    COORDENADA.findAll(linha).mapNotNull { achado ->
                        val (grupo, artefato, versao) = achado.destructured
                        val vigente = vigentes["$grupo:$artefato"] ?: return@mapNotNull null
                        "${arquivo.relativeTo(raiz).path}:${i + 1} — $grupo:$artefato declara $versao, o build usa $vigente"
                            .takeIf { versao != vigente }
                    }
                }
            }
        assertTrue(divergentes.isEmpty()) {
            "doc viva declarando coordenada Gradle com versão que o build não usa:\n" +
                divergentes.joinToString("\n")
        }
    }

    @Test
    fun `nenhuma doc viva cita a action de SCA sem nomear a versao que o ci roda`() {
        // Regra deliberadamente mais frouxa que a de coordenada: aqui a menção HISTÓRICA é legítima e
        // precisa sobreviver — o pitfall do `github-ci-health` registra um comportamento medido no
        // v2.3.8, e apagar a versão medida destruiria a evidência. O que a regra exige é que a linha
        // não fique falando SÓ da versão velha: se cita a action, a versão vigente tem de aparecer
        // junto. Mesmo espírito do "declara UM percentual" do QualityGateMirrorTest.
        val vigente = versaoDaActionDeSca()
        val obsoletas =
            docsVivas(raiz).flatMap { arquivo ->
                arquivo.readText().lines().withIndex().mapNotNull { (i, linha) ->
                    val citadas = ACTION_DE_SCA.findAll(linha).map { it.groupValues[1] }.toList()
                    "${arquivo.relativeTo(raiz).path}:${i + 1} — cita $citadas e o ci roda v$vigente"
                        .takeIf { citadas.isNotEmpty() && vigente !in citadas }
                }
            }
        assertTrue(obsoletas.isEmpty()) {
            "doc viva citando a action de SCA sem nomear a versão vigente (v$vigente):\n" +
                obsoletas.joinToString("\n")
        }
    }

    @Test
    fun `o rodape do supply chain report deriva a versao do scanner em vez de repeti-la`() {
        // O rodapé anunciou `v2.3.8` por três PRs depois do bump para v2.5.0, porque era literal. Esta
        // regra é o que impede alguém de "consertar" o derivado de volta para um dígito escrito à mão —
        // que passaria nas outras regras deste arquivo, já que o `ci.yml` é doc viva e a linha `uses:`
        // continuaria por perto satisfazendo a regra acima.
        val rodape =
            ler(CI)
                .lines()
                .singleOrNull { it.contains("SBOM: artifact") }
                ?: error("$CI deve ter exatamente uma linha de rodapé do Supply Chain Report")
        assertTrue(VERSAO_LITERAL.containsMatchIn(rodape).not()) {
            "o rodapé do Supply Chain Report voltou a fixar a versão do scanner à mão: $rodape"
        }
    }

    @Test
    fun `o exposedVersion do exemplo em architecture md e o do sql_persistence`() {
        // `architecture.md` é o ÚNICO texto que declara Exposed — o espelho não a lista. Um segundo
        // espelho existindo é aceitável; existindo SEM guard foi como ele ficou em 1.3.1 com o build
        // em 1.4.0.
        assertEquals(
            versaoNomeada("sql_persistence/build.gradle.kts", "exposedVersion"),
            versaoNomeada(ARQUITETURA, "exposedVersion"),
            "o exemplo de Exposed em $ARQUITETURA divergiu do sql_persistence",
        )
    }

    @Test
    fun `o parser reconhece as formas em que uma versao e declarada, e so elas`() {
        // Anti-vácuo das regras acima: elas comparam textos, e comparação entre dois "não achei" passa.
        // Cada linha aqui é uma forma que o espelho realmente usa.
        assertEquals(listOf("3.5.2"), versoesDe("Ktor", "| HTTP | Ktor 3.5.2 (Netty engine) |"))
        assertEquals(listOf("2.4.10"), versoesDe("Kotlin", "| Kotlin | 2.4.10 |"))
        assertEquals(listOf("2.0.0-alpha.5"), versoesDe("Detekt", "| Static analysis | Detekt 2.0.0-alpha.5 (`dev.detekt`) |"))
        assertEquals(
            listOf("42.7.13"),
            versoesDe("org.postgresql:postgresql", "driver `org.postgresql:postgresql` 42.7.13) |"),
        )
        assertEquals(listOf("2.5.0"), versoesDe("google/osv-scanner-action@", "(`google/osv-scanner-action@v2.5.0`) — gate"))

        // E o que NÃO pode casar: `Gradle` seguido de palavra é o plugin do PITest/CycloneDX, não o wrapper.
        assertEquals(emptyList<String>(), versoesDe("Gradle", "| Mutation testing | PITest core 1.25.3 / Gradle plugin 1.19.0 |"))
        assertEquals(listOf("9.7.0"), versoesDe("Gradle", "| Java | Java 25 LTS (Gradle 9.7.0 wrapper; Foojay) |"))

        // Dois "Gradle plugin" no espelho — o do PITest e o do CycloneDX. A barra desambigua; sem ela o
        // padrão casa os dois e a regra reprova por ambiguidade em vez de comparar a versão errada.
        val linhaPitest = "| Mutation testing | PITest core 1.25.3 / Gradle plugin 1.19.0 |"
        val linhaSbom = "| SBOM | CycloneDX Gradle plugin 3.4.1 (`org.cyclonedx.bom`) |"
        assertEquals(listOf("1.19.0", "3.4.1"), versoesDe("Gradle plugin", "$linhaPitest\n$linhaSbom"))
        assertEquals(listOf("1.19.0"), versoesDe("/ Gradle plugin", "$linhaPitest\n$linhaSbom"))
        assertEquals(listOf("3.4.1"), versoesDe("CycloneDX Gradle plugin", "$linhaPitest\n$linhaSbom"))
    }

    private fun versoesDe(
        nome: String,
        linha: String,
    ): List<String> = declaracaoDe(nome).findAll(linha).map { it.groupValues[1] }.toList()

    /**
     * O componente aparece no espelho como `Nome <versão>`, com variações de pontuação da tabela: uma
     * crase de fechamento (`` `org.postgresql:postgresql` 42.7.13 ``), o pipe da coluna
     * (`| Kotlin | 2.4.10 |`) ou o `v` da tag de action (`@v2.5.0`).
     */
    private fun declaracaoDe(nome: String): Regex = Regex("""\Q$nome\E`?\s*\|?\s*v?([0-9][0-9A-Za-z.\-]*)""")

    private fun componentes(): List<Triple<String, Regex, String>> {
        val coord = coordenadasDoGradle()

        fun de(coordenada: String): String =
            requireNotNull(coord[coordenada]) { "coordenada não encontrada nos build.gradle.kts: $coordenada" }
        return listOf(
            "Ktor" to de("io.ktor:ktor-server-core-jvm"),
            "Koin" to de("io.insert-koin:koin-core"),
            "HikariCP" to de("com.zaxxer:HikariCP"),
            "Flyway" to de("org.flywaydb:flyway-core"),
            "org.postgresql:postgresql" to de("org.postgresql:postgresql"),
            "Arrow-kt" to de("io.arrow-kt:arrow-core"),
            "JUnit Jupiter" to de("org.junit.jupiter:junit-jupiter-api"),
            "MockK" to de("io.mockk:mockk"),
            "Konsist" to de("com.lemonappdev:konsist"),
            "Detekt" to de("dev.detekt:dev.detekt.gradle.plugin"),
            "ktor-openapi" to de("io.github.smiley4:ktor-openapi"),
            "ktor-swagger-ui" to de("io.github.smiley4:ktor-swagger-ui"),
            "Kotlin" to de("org.jetbrains.kotlin:kotlin-gradle-plugin"),
            "PITest core" to versaoChamada("http_api/build.gradle.kts", "pitestVersion"),
            // A barra é o que separa do "CycloneDX Gradle plugin": sem ela o padrão casa os DOIS e a
            // exigência de casamento único reprova (foi como este teste nasceu vermelho).
            "/ Gradle plugin" to de("info.solidsoft.gradle.pitest:gradle-pitest-plugin"),
            "CycloneDX Gradle plugin" to versaoDePlugin("org.cyclonedx.bom"),
            "google/osv-scanner-action@" to versaoDaActionDeSca(),
            "KtLint" to versaoDoKtLint(),
            "Gradle" to versaoDoWrapper(),
            "(API" to de("io.opentelemetry:opentelemetry-api"),
        ).map { (nome, vigente) -> Triple(nome, declaracaoDe(nome), vigente) }
    }

    /** Toda coordenada `grupo:artefato:versão` declarada em qualquer script Gradle do repo. */
    private fun coordenadasDoGradle(): Map<String, String> =
        scriptsGradle()
            .flatMap { COORDENADA.findAll(it.readText().semComentarios()) }
            .associate { achado ->
                val (grupo, artefato, versao) = achado.destructured
                "$grupo:$artefato" to versao
            }.also { require(it.isNotEmpty()) { "nenhuma coordenada lida dos scripts Gradle — o parser quebrou" } }

    private fun scriptsGradle(): List<File> =
        (
            listOf(File(raiz, "build.gradle.kts")) + File(raiz, "buildSrc").walkTopDown() +
                modulos().map { File(raiz, "$it/build.gradle.kts") }
        ).filter { it.isFile && it.name.endsWith(".gradle.kts") }
            .distinct()

    private fun modulos(): List<String> =
        MODULO_INCLUIDO
            .findAll(ler("settings.gradle.kts").semComentarios())
            .map { it.groupValues[1] }
            .toList()

    private fun versaoDePlugin(id: String): String =
        acharUnica(Regex("""id\("${Regex.escape(id)}"\)\s+version\s+"([^"]+)""""), "build.gradle.kts", "plugin $id")

    private fun versaoNomeada(
        caminho: String,
        nome: String,
    ): String = acharUnica(Regex("""val\s+${Regex.escape(nome)}\s*=\s*"([^"]+)""""), caminho, "val $nome")

    private fun versaoChamada(
        caminho: String,
        nome: String,
    ): String = acharUnica(Regex("""${Regex.escape(nome)}\s*\.\s*set\s*\(\s*"([^"]+)"\s*\)"""), caminho, "$nome.set")

    private fun versaoDoKtLint(): String =
        acharUnica(Regex("""ktlint\s*\{[^}]*?version\s*\.\s*set\s*\(\s*"([^"]+)"\s*\)"""), CONVENTION_PLUGIN, "bloco ktlint")

    private fun versaoDoWrapper(): String = acharUnica(Regex("""gradle-([0-9][0-9A-Za-z.\-]*)-bin\.zip"""), WRAPPER, "distributionUrl")

    private fun versaoDaActionDeSca(): String = acharUnica(ACTION_DE_SCA, CI, "uses: osv-scanner-action")

    /**
     * O stripper de comentários é de fonte **Kotlin** e só se aplica a `.gradle.kts`.
     *
     * Rodá-lo sobre YAML ou `.properties` não é inofensivo, é destrutivo: `https://` casa o comentário
     * de linha do Kotlin e leva o resto da linha embora — o que apagou tanto o `uses:` do `ci.yml`
     * quanto o `distributionUrl` do wrapper, e as duas regras nasceram vermelhas com "achei []".
     */
    private fun acharUnica(
        padrao: Regex,
        caminho: String,
        oQue: String,
    ): String {
        val bruto = ler(caminho)
        val texto = if (caminho.endsWith(".gradle.kts")) bruto.semComentarios() else bruto
        val achados =
            padrao
                .findAll(texto)
                .map { it.groupValues[1] }
                .distinct()
                .toList()
        return achados.singleOrNull()
            ?: error("esperava exatamente uma declaração de $oQue em $caminho, achei $achados")
    }

    private fun ler(caminhoRelativo: String): String {
        val arquivo = File(raiz, caminhoRelativo)
        require(arquivo.isFile) { "arquivo não encontrado: ${arquivo.absolutePath}" }
        return arquivo.readText()
    }

    private companion object {
        const val ESPELHO = ".claude/rules/stack.md"
        const val ARQUITETURA = ".claude/rules/architecture.md"
        const val CONVENTION_PLUGIN = "buildSrc/src/main/kotlin/kanban.kotlin-common.gradle.kts"
        const val WRAPPER = "gradle/wrapper/gradle-wrapper.properties"
        const val CI = ".github/workflows/ci.yml"

        // `grupo:artefato:versão` — a forma canônica, tanto no Gradle quanto no snippet de doc. O grupo
        // exige minúscula inicial e ponto para não casar `Concern | Library` de tabela markdown.
        val COORDENADA = Regex("""([a-z][a-z0-9.\-]*\.[a-z0-9.\-]+):([A-Za-z0-9.\-_]+):([0-9][0-9A-Za-z.\-]*)""")

        val ACTION_DE_SCA = Regex("""osv-scanner-action@v([0-9][0-9A-Za-z.\-]*)""")

        // Um `v1.2.3` escrito à mão no rodapé. O derivado usa `%s`, que não casa aqui.
        val VERSAO_LITERAL = Regex("""osv-scanner v[0-9]""")

        val MODULO_INCLUIDO = Regex("""["']\s*:([A-Za-z0-9_\-]+)\s*["']""")
    }
}
