package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * Fitness function da pureza do domain (ADR-0026): src/main de domain/ não pode
 * importar frameworks nem infraestrutura — hoje o módulo usa apenas stdlib,
 * java.time e java.util (arrow-kt é permitido por ser tipo funcional puro).
 */
class DomainPurityTest {
    /**
     * ALLOW-LIST, não deny-list (GAP-EX). A lista anterior tinha 11 prefixos proibidos e deixava passar
     * tudo o que ninguém tinha pensado em proibir: `com.fasterxml.jackson`, `org.apache.commons`,
     * `redis.clients.jedis`, `java.io.File`, `java.net.http`. Deny-list de framework é uma corrida que o
     * autor da regra sempre perde para o autor do import.
     *
     * Medido antes de inverter — o domínio importa hoje exatamente 5 prefixos, todos legítimos:
     * `com.kanbanvision` (66), `java.util` (15), `arrow.core` (9), `java.time` (5), `kotlin.random` (2).
     */
    private val allowedImportPrefixes =
        listOf(
            "com.kanbanvision.domain.",
            "kotlin.",
            "kotlinx.coroutines.",
            "java.time.",
            "java.util.",
            "java.math.",
            "arrow.core.",
        )

    @Test
    fun `domain so importa kotlin, coroutines, tipos de valor da JDK e arrow`() {
        // scopeFromProduction() + filtro por prefixo de pacote (não por nome de módulo): pós-extração
        // faseada (ADR-0038) o domínio se espalha por :domain-common/:domain-kanban/:domain-simulation.
        // Só esses usam o prefixo com.kanbanvision.domain, então o filtro cobre exatamente os módulos
        // de domínio.
        Konsist
            .scopeFromProduction()
            .files
            .filter { file -> file.packagee?.name?.startsWith("com.kanbanvision.domain") == true }
            .assertFalse(strict = true) { file ->
                file.imports.any { import ->
                    allowedImportPrefixes.none { prefixo -> import.name.startsWith(prefixo) }
                }
            }
    }

    @Test
    fun `domain nao alcanca framework por nome totalmente qualificado`() {
        // A metade que a regra acima NÃO cobre, com ou sem allow-list: `org.slf4j.LoggerFactory.getLogger(…)`
        // escrito inline não gera `import` nenhum e é invisível a qualquer inspeção de imports. Mesmo vetor
        // que `ContextBoundaryTest` e `ContractPackageTest` já cobrem de propósito — este era o inconsistente.
        val raizesDeFramework =
            listOf(
                """io\.ktor""",
                """org\.koin""",
                """org\.jetbrains\.exposed""",
                """org\.slf4j""",
                """io\.micrometer""",
                """com\.zaxxer""",
                """org\.flywaydb""",
                """kotlinx\.serialization""",
                """jakarta""",
                """javax""",
                """java\.sql""",
            )
        // SUBPACOTES entre a raiz e a classe (Codex P2 + Copilot no #399). O padrão anterior exigia
        // `[A-Z]` logo após a raiz, então só pegava classe imediatamente abaixo dela —
        // `io.ktor.server.application.Application`, `javax.crypto.Cipher` e `jakarta.persistence.Entity`,
        // que são a forma NORMAL de escrever um FQN, passavam verdes. Era a maioria dos casos.
        val fqnDeFramework = Regex("""\b(${raizesDeFramework.joinToString("|")})(?:\.[a-z]\w*)*\.[A-Z]""")

        Konsist
            .scopeFromProduction()
            .files
            .filter { file -> file.packagee?.name?.startsWith("com.kanbanvision.domain") == true }
            .assertFalse(strict = true) { file -> fqnDeFramework.containsMatchIn(file.text.semComentariosEStrings()) }
    }
}
