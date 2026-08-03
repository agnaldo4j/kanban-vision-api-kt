package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Fitness functions da estratégia de validação de `Command`/`Query` (GAP-DX).
 *
 * Sete `validate()` usavam duas estratégias para o MESMO par de campos — quatro fail-fast
 * (`either { ensure; ensure }`, 1 erro) e três acumulando (`zipOrAccumulate`, N erros) — então o
 * cliente recebia 1 ou 2 entradas em `ValidationErrorResponse.errors` conforme a rota. A limpeza foi
 * por varredura manual; estas regras são o que impede o oitavo validador de divergir de novo.
 */
class ValidationStrategyTest {
    private companion object {
        const val ENTRADA_DA_ESTRATEGIA = "com.kanbanvision.usecases.cqs.accumulateValidation"
        const val VALIDADOR_DO_PAR = "com.kanbanvision.usecases.cqs.validateSimulationRef"
        const val ENSURE_SIMULATION_ID = "com.kanbanvision.usecases.cqs.ensureSimulationId"
        const val ENSURE_CALLER_ORG_ID = "com.kanbanvision.usecases.cqs.ensureCallerOrganizationId"

        // O construtor fail-fast do Arrow. Proibido no implementor, permitido dentro de Validation.kt —
        // é lá que `accumulateValidation` o usa para montar a ÚNICA estratégia.
        const val EITHER_DO_ARROW = "arrow.core.raise.either"
    }

    @Test
    fun `Command e Query nao constroem a propria estrategia de validacao`() {
        val implementors = implementoresDeCqs()
        Assertions.assertTrue(
            implementors.isNotEmpty(),
            "sem implementor de Command/Query a regra seria vacuamente verdadeira",
        )

        // Checa o IMPORT e o texto: o import cobre o alias (`… as failFast` não muda o FQN importado),
        // o texto cobre a chamada por FQN, que dispensa import e escaparia da primeira metade.
        implementors.assertFalse(strict = true) { clazz ->
            clazz.containingFile.imports.any { it.name == EITHER_DO_ARROW } ||
                clazz.text.semComentariosEStrings().contains(EITHER_DO_ARROW)
        }
    }

    @Test
    fun `Command e Query validam pela entrada unica de acumulacao`() {
        // Contrapartida não-vácua da regra acima: proibir o fail-fast não obriga ninguém a usar a
        // estratégia compartilhada — um validador que devolvesse `Either.Right(Unit)` cru passaria.
        implementoresDeCqs().assertTrue(strict = true) { clazz ->
            clazz.containingFile.imports.any { it.name == ENTRADA_DA_ESTRATEGIA || it.name == VALIDADOR_DO_PAR }
        }
    }

    @Test
    fun `o par simulationId e callerOrganizationId passa pelos validadores compartilhados`() {
        // A âncora é a FORMA (as duas propriedades declaradas), não o nome do arquivo nem a mensagem
        // literal: vale para o Query que ninguém escreveu ainda. As duas checagens estavam copiadas
        // verbatim em cinco arquivos, com o texto da mensagem repetido em cada um.
        val comOParCompleto = implementoresDeCqs().filter { it.declaraOParDeReferencia() }
        Assertions.assertTrue(
            comOParCompleto.isNotEmpty(),
            "sem Command/Query declarando simulationId + callerOrganizationId a regra seria vacuamente verdadeira",
        )

        // Duas formas legítimas, porque `GetDailySnapshotQuery` acumula o par JUNTO com `day` e precisa
        // dos validadores granulares: compor via `validateSimulationRef(…).bind()` colapsaria as duas
        // mensagens do par numa string só, regredindo o array `errors` da resposta.
        comOParCompleto.assertTrue(strict = true) { clazz ->
            val imports =
                clazz.containingFile.imports
                    .map { it.name }
                    .toSet()
            VALIDADOR_DO_PAR in imports ||
                (ENSURE_SIMULATION_ID in imports && ENSURE_CALLER_ORG_ID in imports)
        }
    }

    /**
     * Limite honesto: a regra é shape-based. Ela prova que o validador passa pela estratégia
     * compartilhada, não que as checagens estejam corretas — quem prova isso é
     * `SimulationInputValidationTest`, que exige as duas mensagens acumuladas em cada sítio.
     */
    private fun implementoresDeCqs(): List<KoClassDeclaration> =
        Konsist
            .scopeFromProduction()
            .classes()
            .filter { clazz -> clazz.parents().any { it.name == "Command" || it.name == "Query" } }

    /** Nome de propriedade cobre o `val` do construtor primário e o do corpo da classe. */
    private fun KoClassDeclaration.declaraOParDeReferencia(): Boolean {
        val nomes =
            properties().map { it.name }.toSet() +
                primaryConstructor?.parameters?.map { it.name }.orEmpty()
        return "simulationId" in nomes && "callerOrganizationId" in nomes
    }
}
