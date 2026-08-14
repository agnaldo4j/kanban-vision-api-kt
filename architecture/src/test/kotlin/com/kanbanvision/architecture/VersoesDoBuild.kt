package com.kanbanvision.architecture

import java.io.File

/*
 * A VERDADE das versões, lida das fontes que mandam nelas — os scripts Gradle, o
 * `gradle-wrapper.properties` e o `uses:` do `ci.yml`.
 *
 * Separado do [StackVersionMirrorTest] porque são duas responsabilidades que crescem em ritmos
 * diferentes: aqui entra uma linha por componente novo, lá entra uma regra nova. Juntos estouraram o
 * `LargeClass` do Detekt no primeiro lote de componentes, o que é o mesmo sinal por outro caminho.
 */

/**
 * Um componente rastreado: como o espelho o nomeia, a versão vigente e — quando existe — o slug do
 * badge que o anuncia.
 *
 * [noEspelho] marca o que só é rastreado por badge: `kotest-property` e a instrumentação OTel não estão
 * na tabela do espelho mas estão no README, e ficar fora do espelho não é motivo para ficar fora do
 * guard.
 */
internal data class Componente(
    val nome: String,
    val vigente: String,
    val slugDeBadge: String? = null,
    val noEspelho: Boolean = true,
)

internal class VersoesDoBuild(
    private val raiz: File,
) {
    fun componentes(): List<Componente> = deCoordenada() + deDeclaracaoPropria()

    private fun deCoordenada(): List<Componente> {
        val coord = coordenadas()

        fun de(coordenada: String): String =
            requireNotNull(coord[coordenada]) { "coordenada não encontrada nos build.gradle.kts: $coordenada" }
        return listOf(
            Componente("Ktor", de("io.ktor:ktor-server-core-jvm"), slugDeBadge = "ktor"),
            Componente("Koin", de("io.insert-koin:koin-core"), slugDeBadge = "koin"),
            Componente("HikariCP", de("com.zaxxer:HikariCP")),
            Componente("Flyway", de("org.flywaydb:flyway-core")),
            Componente("org.postgresql:postgresql", de("org.postgresql:postgresql")),
            Componente("Arrow-kt", de("io.arrow-kt:arrow-core"), slugDeBadge = "arrow-kt"),
            Componente("JUnit Jupiter", de("org.junit.jupiter:junit-jupiter-api")),
            Componente("MockK", de("io.mockk:mockk")),
            Componente("Konsist", de("com.lemonappdev:konsist")),
            Componente("Detekt", de("dev.detekt:dev.detekt.gradle.plugin"), slugDeBadge = "detekt"),
            Componente("ktor-openapi", de("io.github.smiley4:ktor-openapi")),
            Componente("ktor-swagger-ui", de("io.github.smiley4:ktor-swagger-ui")),
            Componente("Kotlin", de("org.jetbrains.kotlin:kotlin-gradle-plugin"), slugDeBadge = "kotlin"),
            // A barra separa do "CycloneDX Gradle plugin": sem ela o padrão casa os DOIS, e a exigência
            // de casamento único reprova (foi como o guard nasceu vermelho).
            Componente("/ Gradle plugin", de("info.solidsoft.gradle.pitest:gradle-pitest-plugin")),
            Componente("(API", de("io.opentelemetry:opentelemetry-api")),
            Componente("kotest-property", de("io.kotest:kotest-property"), "kotest-property", noEspelho = false),
            Componente(
                "opentelemetry-instrumentation",
                de("io.opentelemetry.instrumentation:opentelemetry-ktor-3.0"),
                slugDeBadge = "opentelemetry",
                noEspelho = false,
            ),
        )
    }

    /** Os que não são coordenada: plugin com `version`, `val`/`set(...)`, o wrapper e a action. */
    private fun deDeclaracaoPropria(): List<Componente> =
        listOf(
            Componente("PITest core", versaoChamada("http_api/build.gradle.kts", "pitestVersion")),
            Componente("CycloneDX Gradle plugin", versaoDePlugin("org.cyclonedx.bom")),
            Componente("google/osv-scanner-action@", versaoDaActionDeSca()),
            Componente("KtLint", versaoDoKtLint()),
            Componente("Gradle", versaoDoWrapper(), slugDeBadge = "gradle"),
        )

    /**
     * Toda coordenada `grupo:artefato:versão` declarada em qualquer script Gradle do repo.
     *
     * Agrupa antes de escolher, e reprova em conflito. Um `associate` guardaria só a ÚLTIMA ocorrência:
     * `junit-jupiter-api` aparece em todos os módulos, então bastaria um módulo ser bumpado sozinho para
     * o mapa reportar a versão do outro e o espelho seguir stale com o gate verde — o gate mentindo
     * exatamente na forma que ele existe para pegar. Hoje as 64 coordenadas do repo não têm conflito
     * nenhum, então a regra não custa nada e vigia o dia em que passarem a ter. (Codex P2 no #412.)
     */
    fun coordenadas(): Map<String, String> {
        val porCoordenada =
            scriptsGradle()
                .flatMap { COORDENADA.findAll(it.readText().semComentarios()) }
                .groupBy({ "${it.groupValues[1]}:${it.groupValues[2]}" }, { it.groupValues[3] })
        require(porCoordenada.isNotEmpty()) { "nenhuma coordenada lida dos scripts Gradle — o parser quebrou" }
        val conflitantes = porCoordenada.filterValues { it.distinct().size > 1 }
        require(conflitantes.isEmpty()) {
            "coordenada declarada com versões diferentes entre módulos — alinhe o build antes do espelho:\n" +
                conflitantes.entries.joinToString("\n") { (coord, versoes) -> "$coord → ${versoes.distinct()}" }
        }
        return porCoordenada.mapValues { (_, versoes) -> versoes.first() }
    }

    fun versaoDaActionDeSca(): String = acharUnica(ACTION_DE_SCA, CI, "uses: osv-scanner-action")

    fun versaoNomeada(
        caminho: String,
        nome: String,
    ): String = acharUnica(Regex("""val\s+${Regex.escape(nome)}\s*=\s*"([^"]+)""""), caminho, "val $nome")

    fun ler(caminhoRelativo: String): String {
        val arquivo = File(raiz, caminhoRelativo)
        require(arquivo.isFile) { "arquivo não encontrado: ${arquivo.absolutePath}" }
        return arquivo.readText()
    }

    private fun versaoDePlugin(id: String): String =
        acharUnica(Regex("""id\("${Regex.escape(id)}"\)\s+version\s+"([^"]+)""""), "build.gradle.kts", "plugin $id")

    private fun versaoChamada(
        caminho: String,
        nome: String,
    ): String = acharUnica(Regex("""${Regex.escape(nome)}\s*\.\s*set\s*\(\s*"([^"]+)"\s*\)"""), caminho, "$nome.set")

    private fun versaoDoKtLint(): String =
        acharUnica(Regex("""ktlint\s*\{[^}]*?version\s*\.\s*set\s*\(\s*"([^"]+)"\s*\)"""), CONVENTION_PLUGIN, "bloco ktlint")

    private fun versaoDoWrapper(): String = acharUnica(Regex("""gradle-([0-9][0-9A-Za-z.\-]*)-bin\.zip"""), WRAPPER, "distributionUrl")

    /**
     * O stripper de comentários é de fonte **Kotlin** e só se aplica a `.gradle.kts`.
     *
     * Rodá-lo sobre YAML ou `.properties` não é inofensivo, é destrutivo: `https://` casa o comentário de
     * linha do Kotlin e leva o resto da linha embora — o que apagou tanto o `uses:` do `ci.yml` quanto o
     * `distributionUrl` do wrapper, e as duas regras nasceram vermelhas com "achei []".
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

    internal companion object {
        const val CONVENTION_PLUGIN = "buildSrc/src/main/kotlin/kanban.kotlin-common.gradle.kts"
        const val WRAPPER = "gradle/wrapper/gradle-wrapper.properties"
        const val CI = ".github/workflows/ci.yml"

        // `grupo:artefato:versão` — a forma canônica, tanto no Gradle quanto no snippet de doc. O grupo
        // exige minúscula inicial e ponto para não casar `Concern | Library` de tabela markdown.
        val COORDENADA = Regex("""([a-z][a-z0-9.\-]*\.[a-z0-9.\-]+):([A-Za-z0-9.\-_]+):([0-9][0-9A-Za-z.\-]*)""")

        val ACTION_DE_SCA = Regex("""osv-scanner-action@v([0-9][0-9A-Za-z.\-]*)""")

        val MODULO_INCLUIDO = Regex("""["']\s*:([A-Za-z0-9_\-]+)\s*["']""")
    }
}
