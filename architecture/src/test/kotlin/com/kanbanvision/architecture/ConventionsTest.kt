package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Assertions
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
            .assertFalse { clazz ->
                val simulationRepositories =
                    clazz
                        .properties()
                        .filter { it.type?.name == "SimulationRepository" }
                        .map { it.name }
                simulationRepositories.any { clazz.hasTextContaining("$it.findById") }
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
                    val corpo = fn.text.semComentarios()
                    corpo.contains("findById") &&
                        corpo.contains("ensure(") &&
                        corpo.contains("organization.id") &&
                        corpo.contains("Forbidden")
                }

        assertEquals(1, declaracoes.size, "a política de tenancy (findById + ensure(org) + Forbidden) deve existir uma única vez")
        Assertions.assertTrue(
            declaracoes
                .single()
                .containingFile.path
                .contains("/usecases/"),
            "a política de tenancy é regra de aplicação e deve morar em usecases",
        )
    }

    @Test
    fun `nenhum codigo de producao acessa board steps por receptor explicito`() {
        // GAP-DQ/#388 tirou da produção os dois sítios que reimplementavam "primeiro step"
        // (`board.steps.minByOrNull { it.position }`) em vez de perguntar ao agregado. A limpeza foi
        // por varredura manual e nada impedia a regressão — esta regra é o que faltava.
        //
        // Regex e não `hasTextContaining`: `board.stepsInExecutionOrder()` contém `.steps` como
        // prefixo, e um substring check reprovaria justamente a forma correta.
        //
        // O RECEPTOR VEM DO TIPO, não de `.steps` solto (Codex P2 no #392). Proibir `.steps` em
        // qualquer receptor pega propriedade homônima de outro tipo: `BoardSurrogate.steps` existe
        // (`SimulationStateSerializer.kt:79`), e mover o serializer da extension atual para
        // `surrogate.steps` reprovaria o build sem burlar agregado nenhum. Aqui a regra pergunta ao
        // tipo quais nomes são `Board` e proíbe `.steps` só sobre esses — o que cobre tanto
        // `board.steps` quanto `scenario.board.steps`, e deixa `surrogate.steps` passar.
        //
        // LIMITES HONESTOS, os dois deliberados:
        //  (1) acesso SEM receptor dentro de uma extension de Board não é coberto — é a forma do
        //      único acesso cru legítimo do repo (`private fun Board.toSurrogate() = …(steps =
        //      steps.map { … })`), onde serialização precisa da ordem ARMAZENADA e trocar por
        //      `stepsInExecutionOrder()` seria bug de fidelidade de wire;
        //  (2) `with(board) { steps }` também escapa, mesma família.
        val nomesDeBoard =
            Konsist
                .scopeFromProduction()
                .let { escopo ->
                    escopo.properties().filter { it.type?.name == "Board" }.map { it.name } +
                        escopo
                            .functions()
                            .flatMap { it.parameters }
                            .filter { it.type.name == "Board" }
                            .map { it.name }
                }.toSet()

        // Qualificado: `assertTrue` sem pacote resolveria para a extension de List do Konsist.
        Assertions.assertTrue(nomesDeBoard.isNotEmpty(), "sem nome de tipo Board a regra abaixo seria vacuamente verdadeira")

        val acessoCru = Regex("""\b(${nomesDeBoard.joinToString("|") { Regex.escape(it) }})\.steps(?![A-Za-z0-9_])""")
        Konsist
            .scopeFromProduction()
            .files
            .filterNot { it.path.endsWith("/domain/model/kanban/Board.kt") }
            .assertFalse { acessoCru.containsMatchIn(it.text.semComentarios()) }
    }

    // Comentário citando `board.steps` (ex.: "não use board.steps") não é acesso — a regra acima
    // olha código. Contrapartida honesta: um literal de string com `//` engole o resto da linha.
    private fun String.semComentarios(): String = replace(Regex("""/\*[\s\S]*?\*/"""), " ").replace(Regex("""//[^\n]*"""), " ")

    @Test
    fun `Board declara uma unica vez o acesso ordenado a steps`() {
        // Par NÃO-VÁCUO da regra acima, que é vacuamente verdadeira hoje (produção tem zero sítios).
        // Sem este, apagar `firstStep()` deixaria as duas regras verdes e o encapsulamento morto.
        val board =
            Konsist
                .scopeFromProduction()
                .classes()
                .first { it.name == "Board" }

        assertEquals(
            1,
            board.functions().count { it.name == "stepsInExecutionOrder" },
            "Board.stepsInExecutionOrder sumiu ou foi duplicado",
        )
        assertEquals(1, board.functions().count { it.name == "firstStep" }, "Board.firstStep sumiu ou foi duplicado")

        // GAP-EX: contar o NOME não era o que o nome deste teste promete. `fun stepsInExecutionOrder():
        // List<Step> = steps` (sem ordenar) deixava ESTA regra e a de cima verdes, com a ordenação morta.
        // Agora a ordenação é asserida na forma, e o `firstStep` tem de DELEGAR — senão
        // `firstStep(): Step? = steps.firstOrNull()` devolveria a ordem de inserção em silêncio.
        val ordenado = board.functions().single { it.name == "stepsInExecutionOrder" }
        Assertions.assertTrue(
            Regex("""sortedBy\s*\{[^}]*position""").containsMatchIn(ordenado.text.semComentarios()),
            "Board.stepsInExecutionOrder parou de ordenar por position — o nome virou mentira",
        )

        val primeiro = board.functions().single { it.name == "firstStep" }
        Assertions.assertTrue(
            primeiro.text.semComentarios().contains("stepsInExecutionOrder()"),
            "Board.firstStep tem de delegar a stepsInExecutionOrder(), senão devolve ordem de inserção",
        )
    }

    @Test
    fun `codigo fora do dominio nao escolhe um default que o dominio ja declara`() {
        // GAP-DT/EW: o adapter escolhia `SimulationDay(1)`, `DRAFT`, `"Default Simulation Scenario"` — e o
        // use case repetia os mesmos. As regras de `architecture/` governavam direção de dependência e
        // localização, nunca AUTORIA do default, então a classe inteira era invisível para elas.
        //
        // A âncora é DERIVADA do domínio: o conjunto proibido são os defaults que os próprios tipos de
        // domínio declaram. Nada de lista fixa aqui — acrescentar um default novo no domínio estende a
        // regra sozinho.
        //
        // E a proibição é pela FORMA DA ESCOLHA (`?:` / `getOrDefault` / `getOrElse`), não pela mera
        // menção. Medido antes de escrever: proibir a menção reprovaria três usos legítimos —
        // `decodeEnum(status, DRAFT)` nos serializers (fallback de decode, `migrations.md`), o sentinel
        // nomeado `QUARANTINE_CARD_STATE`, e os rótulos de `when` em `RunDayUseCase`. Nenhum deles
        // ESCOLHE um default; eles despacham ou toleram legado.
        //
        // LIMITE HONESTO: shape-based. Quem escrever `val x: ServiceClass; if (...) x = STANDARD` escapa.
        // A regra torna difícil o descuido, não impossível a intenção.
        val (dominio, fora) =
            Konsist
                .scopeFromProduction()
                .files
                .partition { it.path.contains("/domain-") }

        // A DECLARAÇÃO de tipo antes do `=` é o que separa default de atribuição comum. Sem ela o
        // conjunto raspava o corpo do `SimulationEngine`/`Card`/`Worker` e trazia `MovementType.MOVED`,
        // `CardState.DONE`, `AbilityName.TESTER` — 13 entradas para 4 defaults reais. Não dava falso
        // verde (era mais estrito), mas o nome desta regra prometia precisão que ela não tinha, e o
        // primeiro alarme pareceria bug do guard em vez de problema do código.
        val defaultsDoDominio =
            Regex(""":\s*[A-Za-z_][A-Za-z0-9_]*\??\s*=\s*([A-Z][A-Za-z0-9_]*\.[A-Z][A-Z0-9_]{2,})\b""")
                .let { padrao -> dominio.flatMap { padrao.findAll(it.text).map { m -> m.groupValues[1] } } }
                .toSet()

        Assertions.assertTrue(
            defaultsDoDominio.isNotEmpty(),
            "sem default derivado do domínio a regra abaixo seria vacuamente verdadeira",
        )

        // O prefixo de pacote opcional NÃO é decoração: o primeiro probe usou o nome totalmente
        // qualificado e passou impune, porque o regex só casava o nome simples. Mesmo cuidado que a
        // `ContractPackageTest` já toma (cobre import E FQN).
        val escolhaDeDefault =
            Regex(
                """(\?:|getOrDefault\(|getOrElse\s*\{)\s*(?:[a-z][A-Za-z0-9_]*\.)*""" +
                    """(${defaultsDoDominio.joinToString("|") { Regex.escape(it) }})\b""",
            )
        fora.assertFalse { escolhaDeDefault.containsMatchIn(it.text.semComentarios()) }
    }

    @Test
    fun `a politica de parse tolerante de ServiceClass tem exatamente um ponto de declaracao`() {
        // Par NÃO-VÁCUO da regra acima (GAP-DY). A primeira versão desta regra media a unicidade do
        // NOME (`fromNameOrDefault` declarado uma vez) e não a da POLÍTICA — ficou verde enquanto
        // `SimulationSerializerScenarioMappings` reimplementava o mesmo "nome desconhecido → STANDARD"
        // como `decodeEnum(serviceClass, ServiceClass.STANDARD)`. Nome de fitness function é lido como
        // a garantia; esta passou a assertar o que promete.
        //
        // A âncora é a CONSTANTE DE FALLBACK: quem escreve `ServiceClass.STANDARD` fora do domínio está
        // reimplementando a política, seja com `?:`, `decodeEnum` ou qualquer outra forma. Dentro do
        // domínio ela é legítima (é o default declarado de `Card` e de `Decision.AddItem`).
        Konsist
            .scopeFromProduction()
            .files
            .filterNot { it.path.contains("/domain-") }
            .assertFalse { it.text.semComentarios().contains("ServiceClass.STANDARD") }

        // Contagem SEPARADA por política, e não do conjunto somado (Codex P2 no #395): com um total de 2,
        // remover `fromNameOrDefault` enquanto se acrescenta um segundo `fromNameOrNull` mantinha a soma
        // e o guard verde — uma política mascarava o sumiço da outra. E a mensagem afirmava "moram no
        // domínio" sem que nada verificasse onde estavam declaradas; agora verifica.
        val funcoes = Konsist.scopeFromProduction().functions(includeNested = true)
        listOf("fromNameOrDefault", "fromNameOrNull").forEach { politica ->
            val declaracoes = funcoes.filter { it.name == politica }
            assertEquals(1, declaracoes.size, "$politica deve ser declarada exatamente uma vez")
            Assertions.assertTrue(
                declaracoes
                    .single()
                    .containingFile.path
                    .contains("/domain-kanban/"),
                "$politica é regra de domínio e deve morar em domain-kanban",
            )
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
