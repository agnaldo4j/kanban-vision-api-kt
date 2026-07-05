package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fitness functions de convenções (review de gates 2026-07-05): rotas sem acesso
 * direto à persistência, contrato Either nos use cases, nomenclatura CQS e testes
 * com nomes descritivos (convenção de testing.md).
 */
class ConventionsTest {
    @Test
    fun `rotas nao importam a camada de persistencia`() {
        // Complementa o ForbiddenImport do Detekt (que cobre Jdbc*) com a camada
        // inteira: rotas falam com use cases, nunca com persistence.* — a única
        // exceção de wiring é o AppModule (pacote di, fora de routes).
        Konsist
            .scopeFromProduction("http_api")
            .files
            .filter { it.packagee?.name == "com.kanbanvision.httpapi.routes" }
            .assertFalse { file ->
                file.imports.any { it.name.startsWith("com.kanbanvision.persistence") }
            }
    }

    @Test
    fun `use cases expoem execute retornando Either`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withNameEndingWith("UseCase")
            .assertTrue { clazz ->
                val executes = clazz.functions().filter { it.name == "execute" }
                executes.isNotEmpty() &&
                    executes.all { it.returnType?.text?.startsWith("Either<") == true }
            }
    }

    @Test
    fun `classes de commands terminam em Command e de queries em Query`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .filter { it.resideInPackage("..commands") }
            .assertTrue { it.name.endsWith("Command") }

        Konsist
            .scopeFromProduction()
            .classes()
            .filter { it.resideInPackage("..queries") }
            .assertTrue { it.name.endsWith("Query") }
    }

    @Test
    fun `funcoes de teste tem nomes descritivos com backtick`() {
        // Convenção de testing.md: nomes descritivos (`execute saves entity...`).
        // Nome com espaço só é válido em backtick — a regra cobre as duas coisas.
        // O projeto usa kotlin.test.Test (74 arquivos) E org.junit.jupiter.api.Test (8);
        // @TestTemplate (Pact) fica fora de propósito: é método de infraestrutura.
        val testAnnotations = setOf("org.junit.jupiter.api.Test", "kotlin.test.Test")
        Konsist
            .scopeFromTest()
            .functions()
            .filter { fn -> fn.annotations.any { it.fullyQualifiedName in testAnnotations } }
            .assertTrue { it.name.contains(" ") }
    }
}
