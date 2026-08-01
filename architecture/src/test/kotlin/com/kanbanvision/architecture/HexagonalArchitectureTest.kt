package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Fitness function da Dependency Rule (ADR-0026, arquitetura hexagonal):
 * http_api -> usecases -> domain; sql_persistence -> domain + usecases;
 * http_api -> sql_persistence somente para wiring de DI (o ContractPackageTest
 * restringe os repositórios concretos ao AppModule). A direção única das
 * dependências também garante ausência de ciclos entre camadas.
 *
 * GAP-EX: até aqui este arquivo era, em boa parte, decorativo.
 *
 *  - `domain.dependsOnNothing()` era VERDE POR CONSTRUÇÃO. Ele faz `layers.add(this)`
 *    (`LayerDependenciesCore.kt:168`), então o conjunto de layers do bloco continha SÓ `Domain`; e
 *    `getDependentOnAnyLayerFiles` (`KoArchitectureAssert.kt:283-299`) remove os imports que residem
 *    em `Domain` e em seguida só aceita imports que residam em alguma layer do conjunto. Interseção
 *    vazia para qualquer código. Medido com o mesmo probe (`import com.kanbanvision.usecases…` dentro
 *    de `Board.kt`): forma antiga VERDE, forma nova VERMELHA.
 *  - `dependsOn(...)` sem `strict` NÃO PODE FALHAR — `getFailedDependsOnLayers` filtra por
 *    `strict == true`. Só `doesNotDependOn` e o `strict = true` produzem falha.
 */
class HexagonalArchitectureTest {
    private val domain = Layer("Domain", "com.kanbanvision.domain..")
    private val useCases = Layer("UseCases", "com.kanbanvision.usecases..")
    private val persistence = Layer("Persistence", "com.kanbanvision.persistence..")
    private val httpApi = Layer("HttpApi", "com.kanbanvision.httpapi..")

    @Test
    fun `domain nao depende de nenhuma outra camada`() {
        Konsist.scopeFromProduction().assertArchitecture {
            domain.doesNotDependOn(useCases, persistence, httpApi)
        }
    }

    @Test
    fun `usecases depende de domain e de mais nenhuma camada`() {
        // Blocos SEPARADOS de propósito: o Konsist grava `strict=false` nas arestas de
        // `doesNotDependOn` e recusa o override quando a layer já foi declarada `strict=true` no mesmo
        // bloco ("is already defined with a strict=true value"). Um bloco por asserção contorna isso
        // sem enfraquecer nenhuma das duas.
        Konsist.scopeFromProduction().assertArchitecture { useCases.dependsOn(domain, strict = true) }
        Konsist.scopeFromProduction().assertArchitecture { useCases.doesNotDependOn(persistence, httpApi) }
    }

    @Test
    fun `persistence depende de domain e usecases e de mais nenhuma camada`() {
        Konsist.scopeFromProduction().assertArchitecture { persistence.dependsOn(domain, useCases, strict = true) }
        Konsist.scopeFromProduction().assertArchitecture { persistence.doesNotDependOn(httpApi) }
    }

    @Test
    fun `httpApi e a camada mais externa - depende das internas e ninguem depende dela`() {
        // persistence permitida apenas para wiring de DI (AppModule) —
        // granularidade de classe é garantida pelo ContractPackageTest.
        Konsist.scopeFromProduction().assertArchitecture { httpApi.dependsOn(domain, useCases, persistence, strict = true) }
        Konsist.scopeFromProduction().assertArchitecture {
            domain.doesNotDependOn(httpApi)
            useCases.doesNotDependOn(httpApi)
            persistence.doesNotDependOn(httpApi)
        }
    }

    @Test
    fun `as quatro camadas cobrem toda a producao`() {
        // É ESTE teste que dá sentido ao "e de mais nenhuma camada" dos nomes acima. Os
        // `doesNotDependOn` proíbem nominalmente as camadas conhecidas; um 5º pacote de produção
        // (`com.kanbanvision.messaging`, digamos) não seria proibido por ninguém, e o "somente"
        // viraria mentira em silêncio. Aqui a completude é asserida, não presumida.
        val camadas =
            listOf(
                "com.kanbanvision.domain",
                "com.kanbanvision.usecases",
                "com.kanbanvision.persistence",
                "com.kanbanvision.httpapi",
            )
        // `mapNotNull` SUMIRIA com arquivo sem `package` (Codex P2 no #396) — e esse arquivo não reside
        // em layer nenhuma, então pode importar cross-layer sem nenhuma asserção acima vê-lo, enquanto
        // este teste ainda declarava "tudo coberto". Era a classe de defeito deste card dentro do
        // conserto dela. Agora arquivo sem pacote É violação.
        val forasteiros =
            Konsist
                .scopeFromProduction()
                .files
                .mapNotNull { arquivo ->
                    val pacote = arquivo.packagee?.name ?: return@mapNotNull "(sem package) ${arquivo.path}"
                    pacote.takeUnless { p -> camadas.any { p == it || p.startsWith("$it.") } }
                }.distinct()
                .sorted()

        assertEquals(
            emptyList<String>(),
            forasteiros,
            "arquivo de produção sem pacote, ou em pacote fora das 4 camadas — nenhum `doesNotDependOn` acima o cobre",
        )
    }
}
