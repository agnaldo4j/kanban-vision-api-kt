package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Fitness functions de convenções (review de gates 2026-07-05): rotas sem acesso
 * direto à persistência, contrato Either nos use cases, nomenclatura CQS e testes
 * com nomes descritivos (convenção de testing.md).
 */
class ConventionsTest {
    // A regra "repositórios concretos (Jdbc*/Exposed*) só no AppModule" (ADR-0028) foi
    // subsumida pela `ContractPackageTest` (GAP-BS/ADR-0033): os Jdbc* vivem em
    // `persistence.internal.repositories`, e nenhum pacote `*.internal` pode ser importado
    // cross-module exceto pelo AppModule (seam de DI) — cobertura mais ampla e self-service.

    @Test
    fun `rotas nao importam a camada de persistencia`() {
        // Complementa a regra acima com a camada inteira: rotas falam com use
        // cases, nunca com persistence.* — a única exceção de wiring é o
        // AppModule (pacote di, fora de routes).
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
    fun `use case que carrega simulation por id passa pelo guard unico de tenancy`() {
        // GAP-DW: `findById(id).bind()` + `ensure(org == caller) { Forbidden }` estava copiado verbatim em
        // 5 use cases. Um 6º que esquecesse o `ensure` vazaria cross-tenant SEM QUEBRAR TESTE ALGUM — não
        // havia nada, nem em usecases/src/test nem aqui, que exigisse o guard. Extrair o helper torna fácil
        // acertar; esta regra é o que torna difícil errar.
        //
        // A âncora é a CARGA (`findById`), não a injeção do repositório: `ListSimulationsUseCase` injeta o
        // mesmo `SimulationRepository` mas faz tenancy por FILTRO (`findAll(orgId, …)`), e `CreateSimulation`
        // não lê Simulation — os dois devem ficar de fora, e ficam.
        //
        // Forma de IMPLICAÇÃO, não filtro: `assertTrue` sobre lista vazia lança
        // KoPreconditionFailedException, e depois do refactor NENHUM use case casa o antecedente.
        //
        // Limite honesto: a regra é shape-based. Ela não prova que existe autorização — prova que a carga
        // direta não é usada. Quem prova a autorização é LoadOwnedSimulationTest + os 5 testes de Forbidden.
        Konsist
            .scopeFromProduction()
            .classes()
            .withNameEndingWith("UseCase")
            .assertTrue { clazz ->
                !clazz.hasTextContaining("simulationRepository.findById") ||
                    clazz.hasTextContaining("loadOwnedSimulation")
            }
    }

    @Test
    fun `o guard de tenancy de simulation tem exatamente um ponto de declaracao`() {
        // Par NÃO-VÁCUO da regra acima, que depois do refactor é vacuamente verdadeira. Aquela proíbe a
        // forma antiga; esta garante que a forma nova não sumiu nem foi reduplicada — o modo mais provável
        // de o tripwire virar decoração. Este é inerentemente vermelho se o helper for apagado ou copiado.
        val declaracoes =
            Konsist
                .scopeFromProduction()
                .functions()
                .filter { it.name == "loadOwnedSimulation" }

        assertEquals(1, declaracoes.size, "loadOwnedSimulation deve ser declarado uma única vez")
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
