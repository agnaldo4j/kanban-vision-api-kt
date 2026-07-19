package com.kanbanvision.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Fitness function do grafo de dependências de PROJETO Gradle (ADR-0038, Fase 2). A topologia-alvo é
 * `domain-simulation → domain-kanban → domain-common`, unidirecional — nunca invertida.
 *
 * Por que não Konsist / [HexagonalArchitectureTest]: aquele verifica dependências de PACOTE/fonte
 * (imports), não as `project(":...")` deps declaradas no Gradle. O escopo `implementation` (ADR-0033)
 * só limita a exposição transitiva — não impede alguém de declarar
 * `domain-kanban implementation(project(":domain-simulation"))`. Só a asserção explícita das `project`
 * deps garante a direção do grafo. Konsist 0.17.3 não lê deps de projeto, então este teste faz parse
 * dos `build.gradle.kts` (mesmo padrão texto+regex+stripComments de [ContractPackageTest]).
 */
class ProjectDependencyGraphTest {
    /** Ordem topológica: um módulo só pode depender de outro de rank estritamente menor. */
    private val rank =
        mapOf(
            "domain-common" to 0,
            "domain-kanban" to 1,
            "domain-simulation" to 2,
        )

    @Test
    fun `o grafo de project deps dos modulos de dominio respeita simulation - kanban - common`() {
        val violations = mutableListOf<String>()
        for (module in rank.keys) {
            val myRank = rank.getValue(module)
            for (dep in domainProjectDepsOf(module)) {
                val depRank = rank.getValue(dep)
                if (depRank >= myRank) {
                    violations += "$module → $dep (aresta invertida: $module deve depender só de rank menor)"
                }
            }
        }
        assertTrue(violations.isEmpty()) {
            "Inversões no grafo de dependências de domínio (esperado simulation → kanban → common):\n" +
                violations.joinToString("\n")
        }
    }

    @Test
    fun `domain-common nao declara nenhuma dependencia de modulo de dominio`() {
        assertEquals(
            emptySet<String>(),
            domainProjectDepsOf("domain-common"),
            "domain-common é o kernel (sink do grafo) — não pode depender de nenhum outro módulo de domínio",
        )
    }

    /** `project(":X")` deps de um módulo, restritas aos módulos de domínio (rank conhecido). */
    private fun domainProjectDepsOf(module: String): Set<String> {
        val text = stripComments(buildScriptOf(module))
        return PROJECT_DEP
            .findAll(text)
            .map { it.groupValues[1] }
            .filter { rank.containsKey(it) }
            .toSet()
    }

    private fun buildScriptOf(module: String): String {
        val root = System.getProperty("rootDir")?.let(::File) ?: File("..")
        val file = File(root, "$module/build.gradle.kts")
        require(file.isFile) { "build.gradle.kts não encontrado para o módulo '$module' em ${file.absolutePath}" }
        return file.readText()
    }

    private fun stripComments(source: String): String =
        source
            .replace(BLOCK_COMMENT, "")
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")

    private companion object {
        // Casa api/implementation/compileOnly/runtimeOnly(project(":<modulo>")) — captura o alvo no grupo 1.
        private val PROJECT_DEP =
            Regex("""(?:api|implementation|compileOnly|runtimeOnly)\(project\(":([\w-]+)"\)\)""")
        private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", setOf(RegexOption.DOT_MATCHES_ALL))
    }
}
