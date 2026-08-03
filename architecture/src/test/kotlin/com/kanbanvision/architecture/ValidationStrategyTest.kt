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

        // O construtor fail-fast do Arrow. Proibido no validador, permitido dentro de Validation.kt —
        // é lá que `accumulateValidation` o usa para montar a ÚNICA estratégia.
        const val EITHER_DO_ARROW = "arrow.core.raise.either"
    }

    @Test
    fun `Command e Query nao constroem a propria estrategia de validacao`() {
        validadores().assertFalse(strict = true) { clazz ->
            clazz.corpoDoValidate().chamaQualquer(clazz.nomesLocaisDe(EITHER_DO_ARROW))
        }
    }

    @Test
    fun `Command e Query validam pela entrada unica de acumulacao`() {
        // Contrapartida não-vácua da regra acima: proibir o fail-fast não obriga ninguém a usar a
        // estratégia compartilhada — um `validate()` que devolvesse `Either.Right(Unit)` cru passaria.
        // Também é o que cobre a classe sem `validate()` algum, que na regra acima passa vacuamente.
        validadores().assertTrue(strict = true) { clazz ->
            clazz.corpoDoValidate().chamaQualquer(
                clazz.nomesLocaisDe(ENTRADA_DA_ESTRATEGIA) + clazz.nomesLocaisDe(VALIDADOR_DO_PAR),
            )
        }
    }

    @Test
    fun `o par simulationId e callerOrganizationId passa pelos validadores compartilhados`() {
        // A âncora é a FORMA (as duas propriedades declaradas), não o nome do arquivo nem a mensagem
        // literal: vale para o Query que ninguém escreveu ainda. As duas checagens estavam copiadas
        // verbatim em cinco arquivos, com o texto da mensagem repetido em cada um.
        val comOParCompleto = validadores().filter { it.declaraOParDeReferencia() }
        Assertions.assertTrue(
            comOParCompleto.isNotEmpty(),
            "sem Command/Query declarando simulationId + callerOrganizationId a regra seria vacuamente verdadeira",
        )

        // Duas formas legítimas, porque `GetDailySnapshotQuery` acumula o par JUNTO com `day` e precisa
        // dos validadores granulares: compor via `validateSimulationRef(…).bind()` colapsaria as duas
        // mensagens do par numa entrada só, regredindo o array `errors` da resposta.
        comOParCompleto.assertTrue(strict = true) { clazz ->
            val corpo = clazz.corpoDoValidate()
            corpo.chamaQualquer(clazz.nomesLocaisDe(VALIDADOR_DO_PAR)) ||
                (
                    corpo.chamaQualquer(clazz.nomesLocaisDe(ENSURE_SIMULATION_ID)) &&
                        corpo.chamaQualquer(clazz.nomesLocaisDe(ENSURE_CALLER_ORG_ID))
                )
        }
    }

    private fun validadores(): List<KoClassDeclaration> {
        val classes =
            Konsist
                .scopeFromProduction()
                .classes()
                .filter { clazz -> clazz.parents().any { it.name == "Command" || it.name == "Query" } }
        Assertions.assertTrue(classes.isNotEmpty(), "sem implementor de Command/Query as regras seriam vacuamente verdadeiras")
        return classes
    }

    /**
     * O corpo do `validate()` DESTA classe, sem comentários nem literais.
     *
     * Por classe, não por arquivo (Codex P2 no #401): `containingFile.imports` é compartilhado entre
     * todos os `Command`/`Query` do mesmo arquivo, então bastava um deles importar o helper para os
     * demais — inclusive um que fizesse fail-fast — passarem de carona.
     */
    private fun KoClassDeclaration.corpoDoValidate(): String =
        functions()
            .filter { it.name == "validate" }
            .joinToString("\n") { it.text }
            .semComentariosEStrings()

    /**
     * Os nomes pelos quais [fqn] pode ser chamado aqui: o simples, que também casa a chamada por FQN
     * (sufixo), e o alias de import, que o nome simples não casaria (Copilot no #401).
     */
    private fun KoClassDeclaration.nomesLocaisDe(fqn: String): Set<String> =
        containingFile.imports
            .filter { it.name == fqn }
            .mapNotNull { it.alias?.name }
            .toSet() + fqn.substringAfterLast('.')

    /** Chamada de função com esse nome — `f(`, `f {` ou `f<T>`. */
    private fun String.chamaQualquer(nomes: Set<String>): Boolean =
        nomes.any { Regex("""\b${Regex.escape(it)}\s*[<({]""").containsMatchIn(this) }

    /** Nome de propriedade cobre o `val` do construtor primário e o do corpo da classe. */
    private fun KoClassDeclaration.declaraOParDeReferencia(): Boolean {
        val nomes =
            properties().map { it.name }.toSet() +
                primaryConstructor?.parameters?.map { it.name }.orEmpty()
        return "simulationId" in nomes && "callerOrganizationId" in nomes
    }
}
