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
 * A verdade mora no [VersoesDoBuild]; aqui ficam só as regras.
 *
 * **Limite honesto, e ele é real:** as formas vigiadas são as ESTRUTURADAS — a tabela do espelho, a
 * coordenada `grupo:artefato:versão` e o badge do shields.io. Prosa solta continua sem guard, e de
 * propósito: medido sobre as docs vivas, um casamento genérico de `<Nome> <versão>` produziu 23
 * achados dos quais **2** eram drift — `KtLint 0` e `Detekt 0` são contagem de violação,
 * `compileKotlin 2>&1` é redirecionamento de shell, `Koin 4.x`/`Detekt 2.x` são séries e
 * `Detekt 1.23.8` é histórico legítimo. Um guard com 21/23 de falso-positivo seria desligado na
 * primeira semana. Para prosa a saída é apontar para o espelho em vez de repetir o número, como o
 * `testing-and-observability` passou a fazer. (Codex P2 no #412 pediu cobertura fora de coordenada; o
 * que dava para cobrir sem ruído foi o badge.)
 */
class StackVersionMirrorTest {
    /** `workingDir` do teste é o projectDir do módulo; a raiz vem por systemProperty (ver build.gradle.kts). */
    private val raiz = System.getProperty("rootDir")?.let(::File) ?: File("..")
    private val build = VersoesDoBuild(raiz)

    @Test
    fun `o espelho em stack md declara a versao vigente de cada componente rastreado`() {
        val espelho = build.ler(ESPELHO)
        val divergentes =
            build.componentes().filter { it.noEspelho }.mapNotNull { (nome, vigente) ->
                val declaradas = declaracaoDe(nome).findAll(espelho).map { it.groupValues[1] }.toList()
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
        val vigentes = build.coordenadas()
        val divergentes =
            porLinhaDeDocViva { arquivo, i, linha ->
                VersoesDoBuild.COORDENADA.findAll(linha).mapNotNull { achado ->
                    val (grupo, artefato, versao) = achado.destructured
                    val vigente = vigentes["$grupo:$artefato"] ?: return@mapNotNull null
                    "$arquivo:$i — $grupo:$artefato declara $versao, o build usa $vigente"
                        .takeIf { versao != vigente }
                }
            }
        assertTrue(divergentes.isEmpty()) {
            "doc viva declarando coordenada Gradle com versão que o build não usa:\n" +
                divergentes.joinToString("\n")
        }
    }

    @Test
    fun `nenhum badge de doc viva anuncia versao diferente da que o build usa`() {
        // O `shields.io/badge/<slug>-<rótulo>-<cor>` é a OUTRA forma estruturada de declarar versão, e a
        // regra de coordenada não a via: o README anunciava Ktor 3.5.1, Gradle 9.6.1, kotest 6.2.2 e
        // opentelemetry 2.29.0 com o build em 3.5.2/9.7.0/6.2.3/2.30.0-alpha. (Codex P2 no #412 apontou
        // os três primeiros; o quarto apareceu ao varrer os badges em vez de conferir a lista dele.)
        val porSlug = build.componentes().mapNotNull { c -> c.slugDeBadge?.let { it to c.vigente } }.toMap()
        val divergentes =
            porLinhaDeDocViva { arquivo, i, linha ->
                BADGE.findAll(linha).mapNotNull { achado ->
                    // O shields.io escapa `-` como `--` nos DOIS campos: `arrow--kt`, `2.0.0--alpha.5`.
                    val slug = achado.groupValues[1].replace("--", "-")
                    val vigente = porSlug[slug] ?: return@mapNotNull null
                    val anunciada = achado.groupValues[2].replace("--", "-")
                    "$arquivo:$i — badge `$slug` anuncia $anunciada, o build usa $vigente"
                        .takeIf { anunciada != vigente }
                }
            }
        assertTrue(divergentes.isEmpty()) {
            "badge anunciando versão que o build não usa:\n" + divergentes.joinToString("\n")
        }
    }

    @Test
    fun `nenhuma doc viva cita a action de SCA sem nomear a versao que o ci roda`() {
        // Regra deliberadamente mais frouxa que a de coordenada: aqui a menção HISTÓRICA é legítima e
        // precisa sobreviver — o pitfall do `github-ci-health` registra um comportamento medido no
        // v2.3.8, e apagar a versão medida destruiria a evidência. O que a regra exige é que a linha não
        // fique falando SÓ da versão velha: se cita a action, a vigente tem de aparecer junto.
        val vigente = build.versaoDaActionDeSca()
        val obsoletas =
            porLinhaDeDocViva { arquivo, i, linha ->
                val citadas =
                    VersoesDoBuild.ACTION_DE_SCA
                        .findAll(linha)
                        .map { it.groupValues[1] }
                        .toList()
                listOfNotNull(
                    "$arquivo:$i — cita $citadas e o ci roda v$vigente"
                        .takeIf { citadas.isNotEmpty() && vigente !in citadas },
                ).asSequence()
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
            build
                .ler(VersoesDoBuild.CI)
                .lines()
                .singleOrNull { it.contains("SBOM: artifact") }
                ?: error("${VersoesDoBuild.CI} deve ter exatamente uma linha de rodapé do Supply Chain Report")
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
            build.versaoNomeada("sql_persistence/build.gradle.kts", "exposedVersion"),
            build.versaoNomeada(ARQUITETURA, "exposedVersion"),
            "o exemplo de Exposed em $ARQUITETURA divergiu do sql_persistence",
        )
    }

    @Test
    fun `o parser reconhece as formas em que uma versao e declarada, e so elas`() {
        // Anti-vácuo das regras acima: elas comparam textos, e comparação entre dois "não achei" passa.
        assertEquals(listOf("3.5.2"), versoesDe("Ktor", "| HTTP | Ktor 3.5.2 (Netty engine) |"))
        assertEquals(listOf("2.4.10"), versoesDe("Kotlin", "| Kotlin | 2.4.10 |"))
        assertEquals(listOf("2.0.0-alpha.5"), versoesDe("Detekt", "| Static analysis | Detekt 2.0.0-alpha.5 |"))
        assertEquals(
            listOf("42.7.13"),
            versoesDe("org.postgresql:postgresql", "driver `org.postgresql:postgresql` 42.7.13) |"),
        )
        assertEquals(listOf("2.5.0"), versoesDe("google/osv-scanner-action@", "(`google/osv-scanner-action@v2.5.0`)"))

        // Dois "Gradle plugin" no espelho — o do PITest e o do CycloneDX. A barra desambigua; sem ela o
        // padrão casa os dois e a regra reprova por ambiguidade em vez de comparar a versão errada.
        val linhaPitest = "| Mutation testing | PITest core 1.25.3 / Gradle plugin 1.19.0 |"
        val linhaSbom = "| SBOM | CycloneDX Gradle plugin 3.4.1 (`org.cyclonedx.bom`) |"
        assertEquals(listOf("1.19.0", "3.4.1"), versoesDe("Gradle plugin", "$linhaPitest\n$linhaSbom"))
        assertEquals(listOf("1.19.0"), versoesDe("/ Gradle plugin", "$linhaPitest\n$linhaSbom"))
        assertEquals(listOf("3.4.1"), versoesDe("CycloneDX Gradle plugin", "$linhaPitest\n$linhaSbom"))
        assertEquals(listOf("9.7.0"), versoesDe("Gradle", "| Java | Java 25 LTS (Gradle 9.7.0 wrapper) |"))
    }

    @Test
    fun `o parser de badge le slug e versao escapados, e ignora badge sem versao`() {
        fun badges(linha: String) =
            BADGE
                .findAll(linha)
                .map { "${it.groupValues[1].replace("--", "-")}=${it.groupValues[2].replace("--", "-")}" }
                .toList()

        assertEquals(listOf("ktor=3.5.2"), badges("[![K](https://img.shields.io/badge/ktor-3.5.2-087CFA?logo=ktor)](x)"))
        // `--` é o escape de `-` do shields.io, nos dois campos.
        assertEquals(listOf("arrow-kt=2.2.3"), badges("https://img.shields.io/badge/arrow--kt-2.2.3-E91E63"))
        assertEquals(listOf("detekt=2.0.0-alpha.5"), badges("https://img.shields.io/badge/detekt-2.0.0--alpha.5-9146FF"))
        assertEquals(
            listOf("opentelemetry=2.30.0-alpha"),
            badges("https://img.shields.io/badge/opentelemetry-2.30.0--alpha-425CC7?logo=opentelemetry"),
        )

        // Badge que não declara versão de artefato — o rótulo não começa com dígito, ou nem é versão.
        assertEquals(emptyList<String>(), badges("https://img.shields.io/badge/java-25%20LTS-ED8B00"))
        assertEquals(emptyList<String>(), badges("https://img.shields.io/badge/pitest-mutation%20testing-CC0000"))
        assertEquals(emptyList<String>(), badges("https://img.shields.io/badge/license-MIT-blue.svg"))
        assertEquals(emptyList<String>(), badges("https://img.shields.io/badge/P1-red?style=flat"))
    }

    private fun porLinhaDeDocViva(regra: (String, Int, String) -> Sequence<String>): List<String> =
        docsVivas(raiz).flatMap { arquivo ->
            val caminho = arquivo.relativeTo(raiz).path
            arquivo
                .readText()
                .lines()
                .withIndex()
                .flatMap { (i, linha) -> regra(caminho, i + 1, linha).toList() }
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

    private companion object {
        const val ESPELHO = ".claude/rules/stack.md"
        const val ARQUITETURA = ".claude/rules/architecture.md"

        // Um `v1.2.3` escrito à mão no rodapé. O derivado usa `%s`, que não casa aqui.
        val VERSAO_LITERAL = Regex("""osv-scanner v[0-9]""")

        // `shields.io/badge/<slug>-<rótulo>-<cor>`, com o `-` do nome escapado como `--`. O rótulo tem de
        // COMEÇAR com dígito: é o que separa "declara versão" de `java-25%20LTS` ou
        // `pitest-mutation%20testing`, que não declaram versão de artefato nenhuma.
        val BADGE = Regex("""shields\.io/badge/([A-Za-z0-9]+(?:--[A-Za-z0-9]+)*)-([0-9][0-9A-Za-z.\-]*)-[0-9A-Fa-f]{3,8}""")
    }
}
