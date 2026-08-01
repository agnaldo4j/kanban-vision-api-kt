package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Fitness functions do guard de tenancy de `Simulation` (GAP-DW).
 *
 * Extraídas do [ConventionsTest] quando ele estourou o `LargeClass` do Detekt — e a separação vale por
 * si: é a regra de maior consequência do módulo, a única cuja falha é vazamento cross-tenant, e agora
 * o nome do arquivo diz o que ela protege.
 */
class TenancyGuardTest {
    private companion object {
        // Exige IGUALDADE e verifica os operandos (Codex P2 no #397). A presença dos tokens não
        // distinguia `==` de `!=`: um guard invertido negaria o dono e liberaria cross-tenant com os
        // quatro marcadores intactos. A semântica em si é provada por LoadOwnedSimulationTest, que
        // cobre as duas direções; esta regra impede a forma inequivocamente errada.
        val COMPARACAO_DE_TENANCY = Regex("""ensure\s*\(\s*[A-Za-z_][\w.]*\.organization\.id\s*==\s*[A-Za-z_]\w*\s*\)""")
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
        // O nome do receptor é DERIVADO DO TIPO, não fixado no texto (Codex P2 no #387). Casar a literal
        // `simulationRepository.findById` deixava passar `repo.findById`/`simulationRepo.findById` — um
        // guard de segurança driblável por renomear um campo não é guard. Aqui a regra pergunta ao tipo
        // quem são os campos `SimulationRepository` e proíbe `findById` sobre cada um deles.
        //
        // E é PROIBIÇÃO PLANA, não implicação: a versão anterior — "contém findById ⟹ contém
        // loadOwnedSimulation" — agregava em nível de CLASSE, então um use case com uma chamada guardada
        // E uma carga direta satisfazia as duas substrings e passava (Codex P2, a metade mais séria).
        // Sem substring de escape não há esse buraco: nenhum `*UseCase` pode chamar `findById` num
        // `SimulationRepository`, ponto. O único caminho legítimo é `loadOwnedSimulation`, que é função
        // top-level (não é classe `*UseCase`) e recebe o repositório por parâmetro.
        //
        // `CreateSimulationUseCase` fica de fora corretamente: o `findById` dele é de
        // `OrganizationRepository`, outro tipo. `ListSimulationsUseCase` também: usa `findAll`/`count`.
        //
        // Limite honesto: a regra é shape-based. Ela não prova que existe autorização — prova que a carga
        // direta não é usada. Quem prova a autorização é LoadOwnedSimulationTest + os 5 testes de Forbidden.
        Konsist
            .scopeFromProduction()
            .classes()
            .withNameEndingWith("UseCase")
            .assertFalse(strict = true) { clazz ->
                // GAP-EX: `hasTextContaining("$campo.findById")` caía com uma QUEBRA DE LINHA — a forma
                // idiomática que o ktlint sequer desencoraja:
                //     simulationRepository
                //         .findById(id)
                //         .bind()
                // Também driblavam `with(repo) { findById() }`, `repo.let { it.findById() }` e
                // `repo::findById`. E o `hasTextContaining` lê `psiElement.text`, que INCLUI comentários:
                // um `// nunca chame repo.findById aqui` REPROVAVA o build (falso-positivo). As três
                // regras vizinhas usam `semComentarios()`; esta era a que faltava.
                //
                // O tipo do campo também vem por `it.type?.name`, que é o TEXTO do type reference: um
                // campo declarado por FQN ou por alias de import escapava do conjunto. Daí o fallback
                // por sufixo — cobre `com.…usecases.repositories.SimulationRepository` e o alias.
                val corpo = clazz.text.semComentariosEStrings()
                // O ALIAS de import é resolvido antes de filtrar (Codex P2 no #399): com
                // `import …SimulationRepository as SimRepo`, `it.type?.name` devolve `SimRepo` e o
                // sufixo não casava — o PR anterior AFIRMAVA cobrir alias e não cobria.
                val apelidosDoRepositorio =
                    clazz.containingFile.imports
                        .filter { it.name.substringAfterLast('.') == "SimulationRepository" }
                        .mapNotNull { it.alias?.name }
                        .toSet()
                val camposDeRepositorio =
                    clazz
                        .properties()
                        .filter {
                            val tipo = it.type?.name?.substringAfterLast('.')
                            tipo?.endsWith("SimulationRepository") == true || tipo in apelidosDoRepositorio
                        }.map { it.name }
                camposDeRepositorio.any { campo ->
                    // `alcanca` percorre a cadeia com CASAMENTO DE CHAVES e exige operador de
                    // encadeamento explícito. O regex anterior usava `[^}]*`, que termina na primeira
                    // `}`: `repo.let { it.also { audit(it) } }.findById(id)` escapava (Codex P2 no
                    // #400) e `[.?]*` vazio atravessava fronteira de statement, virando falso-positivo
                    // com `repo.let { … }` seguido de um `findById(…)` solto (Copilot no #400).
                    alcanca(corpo, campo, "findById") ||
                        Regex("""with\s*\(\s*${Regex.escape(campo)}\s*\)""").find(corpo)?.let { abertura ->
                            blocoApos(corpo, abertura.range.last + 1)?.contains("findById") == true
                        } == true
                }
            }
    }

    @Test
    fun `o guard de tenancy de simulation tem exatamente um ponto de declaracao`() {
        // Par NÃO-VÁCUO da regra acima, que depois do refactor é vacuamente verdadeira.
        //
        // GAP-EX: até aqui esta regra contava `functions.filter { name == "loadOwnedSimulation" }.size == 1`
        // — a unicidade do NOME, não da POLÍTICA. Exatamente o defeito que o #395 corrigiu para o
        // `ServiceClass` e que aqui continuou vivo. Ficava verde se:
        //   (a) o `ensure(org == caller)` fosse removido de dentro do helper — o nome permanece;
        //   (b) a política fosse reduplicada com outro nome (`fetchOwnedSimulation`);
        //   (c) o helper fosse relocado para `http_api` — não havia check de path.
        //
        // A âncora agora é a FORMA DA POLÍTICA: carga por `findById` + comparação de `organization.id`
        // + `Forbidden`. Conjunção plana de marcadores, não regex frágil — medido, só uma função de
        // produção tem os quatro. E a localização é verificada, não afirmada na mensagem.
        val declaracoes =
            Konsist
                .scopeFromProduction()
                .functions(includeNested = true)
                .filter { fn ->
                    val corpo = fn.text.semComentariosEStrings()
                    corpo.contains("findById") && corpo.contains("Forbidden") && COMPARACAO_DE_TENANCY.containsMatchIn(corpo)
                }

        assertEquals(1, declaracoes.size, "a política de tenancy (findById + ensure(org) + Forbidden) deve existir uma única vez")
        Assertions.assertTrue(
            declaracoes
                .single()
                .containingFile.path
                .normalizado()
                .contains("/usecases/"),
            "a política de tenancy é regra de aplicação e deve morar em usecases",
        )
    }

    /** Corpo do bloco `{ … }` que começa após [de], com casamento de chaves. */
    private fun blocoApos(
        corpo: String,
        de: Int,
    ): String? {
        val abre = corpo.indexOf('{', de).takeIf { it >= 0 } ?: return null
        var nivel = 0
        for (i in abre until corpo.length) {
            when (corpo[i]) {
                '{' -> nivel++
                '}' -> {
                    nivel--
                    if (nivel == 0) return corpo.substring(abre, i)
                }
            }
        }
        return null
    }

    private fun String.normalizado(): String = replace('\\', '/')
}
