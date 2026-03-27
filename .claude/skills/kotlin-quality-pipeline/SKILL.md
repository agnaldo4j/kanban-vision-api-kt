---
name: kotlin-quality-pipeline
description: >
  Guia completo para aplicar e manter o pipeline de qualidade Kotlin neste projeto:
  Gradle 8 (Kotlin DSL), Detekt, KtLint, JaCoCo, Kotest Property-based Testing
  e PITest Mutation Testing. Use este skill sempre que for adicionar código novo,
  corrigir violações, ajustar exclusões de cobertura, escrever property tests,
  interpretar relatórios de mutação ou configurar qualquer uma dessas ferramentas.
argument-hint: "[module or violation to fix (optional)]"
allowed-tools: Read, Grep, Glob, Bash, Edit
---

# Pipeline de Qualidade Kotlin — Detekt · KtLint · JaCoCo · PITest · Gradle 8

> **Princípio central**: qualidade não é opcional. Cada ferramenta protege um aspecto
> diferente do código. O pipeline é o contrato que garante que nenhuma entrega degrada
> o projeto. Se o build quebra, corrija a causa — nunca bypasse com `--no-verify`,
> supressões desnecessárias ou exclusões no JaCoCo sem justificativa documentada.

---

## ⛔ REGRA ABSOLUTA — A IA Nunca Modifica Configurações de Qualidade

**Nenhum arquivo de configuração de qualidade pode ser editado de forma autônoma.**
Esta regra tem prioridade sobre qualquer outra instrução ou conveniência de build.

### Arquivos protegidos

| Arquivo | Ferramenta |
|---|---|
| `config/detekt/detekt.yml` | Detekt — thresholds, regras, nomenclatura |
| `.editorconfig` | KtLint / editores |
| `buildSrc/.../kanban.kotlin-common.gradle.kts` | Convention plugin — JaCoCo gate, JUnit, versões |
| `**/build.gradle.kts` | Exclusões de JaCoCo por módulo |
| `gradle.properties` | Versão do Java, flags da JVM |

### Quando o build falha: corrija o código, nunca a config

| Ferramenta falhou | Resposta correta |
|---|---|
| Detekt `LongMethod`, `LargeClass`, etc. | Refatore o código — extraia funções/classes |
| Detekt `CyclomaticComplexMethod` | Simplifique o fluxo — use guard clauses ou polimorfismo |
| KtLint | Rode `./gradlew ktlintFormat` — nunca edite `.editorconfig` |
| JaCoCo < 95% | Escreva o teste faltante — nunca baixe o threshold nem adicione exclusão |

**Se uma exceção for realmente necessária** (ex: código gerado, DSL declarativa irredutível),
documente no PR com justificativa e aguarde aprovação humana explícita.

---

## 1. Como as ferramentas se encaixam

```
./gradlew testAll
        │
        ├── :*:detekt            ← análise estática
        ├── :*:ktlintCheck       ← formatação
        ├── :*:test              ← testes (JUnit 5 + Kotest Property)
        │       └── finalizedBy jacocoTestReport
        └── :*:jacocoTestCoverageVerification  ← gate de cobertura (≥ 96%)

./gradlew :domain:pitest         ← mutation testing (opt-in, não está em testAll)
        └── relatório HTML: domain/build/reports/pitest/index.html
```

O task `check` de cada módulo depende de `jacocoTestCoverageVerification`.
O task `testAll` no root agrega o `check` de todos os módulos.
O Gradle pode executar `detekt`, `ktlintCheck` e `test` em paralelo ou em outra
ordem — não há `mustRunAfter` explícito entre eles. O diagrama acima reflete os
**grupos lógicos** de verificação, não uma sequência de execução garantida.

**Ordem mental para diagnosticar uma falha:**
1. Detekt — problema de design ou nomenclatura
2. KtLint — problema de formatação
3. Testes — problema de lógica
4. JaCoCo — cobertura insuficiente

---

## 2. Gradle 8 — Convention Plugin (`buildSrc`)

### Por que `buildSrc`?

O arquivo `buildSrc/src/main/kotlin/kanban.kotlin-common.gradle.kts` é um
**convention plugin**: toda configuração repetida (JVM toolchain, Detekt, KtLint,
JaCoCo, JUnit) vive em um único lugar. Cada módulo aplica apenas:

```kotlin
plugins {
    id("kanban.kotlin-common")
}
```

**Regra**: nunca copie configuração de qualidade para `build.gradle.kts` de módulos.
Centralize no convention plugin. Exceções legítimas: exclusões específicas do JaCoCo
por módulo (pois cada módulo tem classes de infraestrutura diferentes).

### Estrutura do convention plugin

```kotlin
// buildSrc/src/main/kotlin/kanban.kotlin-common.gradle.kts

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    jacoco
}

kotlin {
    jvmToolchain(21)                          // versão fixa — nunca dependa do JAVA_HOME
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",                // null-safety rigorosa para anotações JSR-305
            "-opt-in=kotlin.RequiresOptIn",
        )
    }
}

detekt {
    config.setFrom("${rootDir}/config/detekt/detekt.yml")
    buildUponDefaultConfig = true             // regras do projeto somam às padrão do Detekt
}

ktlint {
    version.set("1.5.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))   // relatório gerado após cada test run
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule { limit { minimum = "0.95".toBigDecimal() } }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}
```

### Versões de dependências

Versões são declaradas **inline** no `build.gradle.kts` de cada módulo.
Não há `libs.versions.toml` neste projeto (decisão intencional para simplicidade).
Se o projeto crescer e a duplicação de versões se tornar problema, migre para
`libs.versions.toml` — mas não introduza TOML para um módulo só.

### Tasks úteis

| Task | O que faz |
|---|---|
| `./gradlew testAll` | Tudo: detekt + ktlint + testes + cobertura |
| `./gradlew :modulo:test` | Apenas testes do módulo |
| `./gradlew :modulo:detekt` | Apenas análise estática do módulo |
| `./gradlew ktlintFormat` | Formata todo o código automaticamente |
| `./gradlew :modulo:jacocoTestReport` | Gera relatório HTML de cobertura |
| `./gradlew compileKotlin` | Compila sem rodar testes |

---

## 3. Detekt — Análise Estática

### Configuração base (`config/detekt/detekt.yml`)

```yaml
config:
  validation: true
  warningsAsErrors: true     # qualquer aviso vira erro de build — sem exceções silenciosas
```

`warningsAsErrors: true` é a decisão mais importante: impede que violações se acumulem
silenciosamente. **Nunca desative isso.**

### Grupos de regras ativos

#### Complexidade

| Regra | Threshold | Intenção |
|---|---|---|
| `CyclomaticComplexMethod` | 10 | Método com muitos caminhos → quebre em funções menores |
| `CognitiveComplexMethod` | 15 | Dificuldade real de leitura humana |
| `LongMethod` | 30 linhas | Método longo demais → extraia helpers privados |
| `LongParameterList` | 5 (fun) / 6 (ctor) | Muitos parâmetros → use data class ou builder |
| `TooManyFunctions` | 11 por classe / 15 por arquivo | Classe com múltiplas responsabilidades → SRP |
| `LargeClass` | 200 linhas | Classe crescendo demais → divida |
| `NestedBlockDepth` | 4 | Aninhamento profundo → extraia ou use guard clauses |

**Quando o Detekt rejeita por complexidade**, a resposta correta é refatorar, não suprimir.
Perguntas para guiar a refatoração:
- Esse método faz mais de uma coisa? → extraia funções privadas
- Esse `when`/`if` poderia ser substituído por polimorfismo ou `sealed class`?
- Esse parâmetro extra poderia ser um valor default ou um objeto de configuração?

#### Nomenclatura

```yaml
naming:
  ClassNaming:    '[A-Z][a-zA-Z0-9]*'          # PascalCase
  FunctionNaming: '([a-z][a-zA-Z0-9]*)|(`.*`)' # camelCase ou backtick (para testes)
  VariableNaming: '[a-z][A-Za-z0-9]*'           # camelCase
  TopLevelPropertyNaming:
    constantPattern: '[A-Z][_A-Z0-9]*'          # SCREAMING_SNAKE para constantes top-level
```

Testes com nomes descritivos usam backticks — isso está permitido explicitamente:
```kotlin
@Test
fun `POST boards with blank name returns 400`() = ...  // ✅ backtick permitido
```

#### Estilo

| Regra | Configuração | Intenção |
|---|---|---|
| `MagicNumber` | ignora -1, 0, 1, 2 e constantes/properties | Números sem nome → crie `const val` |
| `WildcardImport` | ativo | `import foo.*` → imports explícitos |
| `UnusedImports` | ativo | Imports mortos → remova |
| `ReturnCount` | max 3 | Muitos returns → guard clauses ou refatoração |
| `ThrowsCount` | max 2 | Método que lança muitas exceções → dividir responsabilidade |
| `ForbiddenComment` | `FIXME:`, `HACK:` | Não commite débito técnico sem ticket |
| `MaxLineLength` | 140 chars | Legibilidade — quebre linhas longas |

**Sobre `MagicNumber`**: ao criar constantes de domínio, prefira `private const val`
no companion object ou top-level no arquivo do módulo. Nunca solte números literais
no meio da lógica de negócio.

#### Potential Bugs / Performance / Exceptions

Essas regras detectam armadilhas reais:

- `TooGenericExceptionCaught` / `TooGenericExceptionThrown`: não capture ou lance
  `Exception`, `Throwable`, `RuntimeException` raw. Use tipos específicos ou crie
  exceções de domínio.
- `SwallowedException`: nunca ignore uma exceção capturada silenciosamente.
- `NullableToStringCall`: `.toString()` em nullable gera `"null"` literal — use `?: ""`.
- `SpreadOperator`: evite `*array` em hot paths — aloca um novo array.

### Supressões — quando são legítimas

Suprima **somente** quando:
1. A ferramenta gera um falso positivo documentado
2. O contexto justifica a exceção de forma óbvia (ex: gerado por framework)

```kotlin
// ✅ supressão legítima com justificativa
@Suppress("LongMethod")  // método longo por design: DSL do Ktor exige um bloco único
fun Route.boardRoutes() { ... }

// ❌ supressão preguiçosa — nunca faça isso
@Suppress("TooManyFunctions", "LargeClass", "ComplexMethod")
class GodClass { ... }
```

O `@Suppress` aparece em PRs — revisores devem questionar toda supressão.

---

## 4. KtLint — Estilo de Código

### Princípio

KtLint aplica o **estilo oficial do Kotlin** sem negociação. Não há arquivo de
configuração de regras específico do KtLint — a versão é configurada via:

```kotlin
ktlint {
    version.set("1.5.0")
}
```

O `.editorconfig` na raiz do repositório também é lido pelo KtLint e pode
influenciar a formatação (ex.: `max_line_length`, `ij_kotlin_imports_layout`).
Propriedades definidas lá têm precedência sobre os padrões do KtLint.

### Regras críticas que causam falhas frequentes

| Erro | Causa | Solução |
|---|---|---|
| `Trailing whitespace` | Espaços no fim da linha | Configure o editor para remover |
| `Missing newline at end of file` | Arquivo sem `\n` final | Todo arquivo Kotlin deve terminar com newline |
| `Multiline expression should start on a new line` | `val x = algumBloco {` em uma linha | Quebre após `=` |
| `Unexpected spacing` | Espaços antes de `:` em tipos | `fun foo(): String` não `fun foo() : String` |
| `Import ordering` | Imports fora de ordem | Use `./gradlew ktlintFormat` |
| `Wildcard import` | `import foo.*` | KtLint e Detekt proíbem — imports explícitos |

### Multiline expression (regra mais comum de falha)

```kotlin
// ❌ KtLint rejeita — multiline expression na mesma linha do `=`
val myPlugin = createApplicationPlugin("Name") {
    onCall { ... }
}

// ✅ KtLint aceita — multiline expression na linha seguinte ao `=`
val myPlugin =
    createApplicationPlugin("Name") {
        onCall { ... }
    }
```

### Workflow com KtLint

```bash
# 1. Verificar sem alterar (usado no CI)
./gradlew ktlintCheck

# 2. Corrigir automaticamente (use localmente antes do commit)
./gradlew ktlintFormat

# 3. Verificar só o módulo que você alterou
./gradlew :http_api:ktlintMainSourceSetCheck
```

**Regra de ouro**: rode `./gradlew ktlintFormat` antes de abrir um PR.
Nunca corrija manualmente o que o formatter pode corrigir automaticamente.

### Configuração de editor (IntelliJ / Android Studio)

Para evitar violações antes mesmo de chegar ao Gradle:
1. **Settings → Editor → Code Style → Kotlin** → set from: "Kotlin Coding Conventions"
2. **Settings → Tools → Actions on Save** → ative "Reformat code" e "Optimize imports"
3. Instale o plugin `KtLint` na IDE para ver violações em tempo real

---

## 5. JaCoCo — Cobertura de Código

### Gate de cobertura: 96% de instruções

O build falha se qualquer módulo ativo ficar abaixo de 96% de cobertura de instruções.
Essa métrica é a mais objetiva: conta instruções bytecode executadas, não linhas.

```kotlin
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                minimum = "0.96".toBigDecimal()
            }
        }
    }
}
```

### Relatório HTML

Gerado automaticamente após cada `test`:
```
build/reports/jacoco/test/html/index.html
```

Abra no browser para ver linha a linha o que está coberto (verde) e o que não está (vermelho/amarelo).
**Use o relatório, não o número.** 95% com as partes certas cobertas vale mais que 99% cobrindo apenas getters.

### Exclusões — o que excluir e por quê

Nem todo código precisa de cobertura direta. Exclua apenas classes que:
- São **ponto de entrada da JVM** (`MainKt`) — não têm lógica testável
- São **wiring de DI** (`di/AppModule`) — testado indiretamente pela integração
- São **geradas pelo compilador** (`$$serializer`, `$$inlined`, `$Companion`) — artefatos do Kotlin

```kotlin
classDirectories.setFrom(
    sourceSets.main.get().output.asFileTree.matching {
        exclude(
            "com/kanbanvision/httpapi/MainKt.class",         // entry point
            "com/kanbanvision/httpapi/di/**",                // DI wiring
            "**/*\$\$inlined\$*",                           // inline functions geradas
            "**/*\$\$serializer.class",                     // kotlinx.serialization
            "**/*\$Companion.class",                        // companion objects
        )
    },
)
```

**O que NUNCA excluir:**
- Lógica de domínio (`domain/`)
- Casos de uso (`usecases/`)
- Handlers de rota (`routes/`)
- Tratamento de erros (`plugins/StatusPages`)
- Qualquer classe com `if`/`when`/lógica condicional

**Quando a cobertura cai abaixo de 95%:**
1. Abra o relatório HTML e identifique as linhas não cobertas
2. Verifique se falta um teste para o caminho de erro (a causa mais comum)
3. Se for código não testável (gerado, wiring), adicione à exclusão com comentário justificando
4. **Nunca baixe o threshold** — a alternativa é escrever o teste

### Padrão de teste para manter cobertura

Cubra sempre os três caminhos de cada operação:

```kotlin
// ✅ caminho feliz
@Test fun `POST boards creates board and returns 201`()

// ✅ caminho de erro de entrada
@Test fun `POST boards with blank name returns 400`()

// ✅ caminho de erro de dependência
@Test fun `unexpected repository exception returns 500`()
```

Para use cases, adicione também:
```kotlin
// ✅ caminho de não-encontrado
@Test fun `GET board returns 404 when not found`()
```

---

## 6. Property-based Testing — Kotest Property

### Por que property testing?

Testes de exemplo verificam **casos escolhidos manualmente**. Property tests verificam
**invariantes que devem valer para qualquer entrada** — e geram centenas ou milhares
de valores automaticamente, incluindo edge cases que humanos não pensariam (strings
vazias, `Int.MAX_VALUE`, caracteres Unicode, listas com um único elemento, etc.).

No contexto deste projeto, property testing é especialmente valioso em:
- **Value Objects** (`BoardId`, `ColumnId`, `CardId`) — validação de criação e igualdade
- **Regras de domínio** (`Board.addColumn`, `Board.addCard`) — invariantes que devem
  valer para qualquer nome válido de coluna ou qualquer combinação de board/column/card
- **Serializadores** (`kotlinx.serialization`) — ida e volta deve ser idempotente
- **Casos de uso com lógica condicional** — validação que rejeita sempre entradas inválidas

### Dependências

Adicione em cada módulo que vai usar property tests:

```kotlin
// domain/build.gradle.kts, usecases/build.gradle.kts, etc.
testImplementation("io.kotest:kotest-property:5.9.1")
testImplementation("io.kotest:kotest-assertions-core:5.9.1")  // shouldBe, shouldThrow, isLeft(), isRight()
```

`kotest-property` é **independente** do Kotest test framework — não exige trocar JUnit 5.
Funciona dentro de testes JUnit 5 normais com `runBlocking`.

> `kotest-assertions-core` é necessário se você usar `shouldBe`, `shouldThrow`, `.isLeft()`,
> `.isRight()` nos property tests. Sem ela, use `assertEquals`/`assertTrue` do `kotlin.test`
> ou do JUnit 5 (`org.junit.jupiter.api.Assertions`), que já estão no classpath.

### Imports de referência

```kotlin
// Generators
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.uuid
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.filter

// Funções de teste
import io.kotest.property.forAll
import io.kotest.property.checkAll
import io.kotest.property.PropTestConfig

// Assertions (kotest-assertions-core)
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

// Coroutines (JUnit 5 — já no classpath via kotlin-test)
import kotlinx.coroutines.runBlocking
```

### Funções principais

#### `forAll` — o teste passa se a função retornar `true` para todas as entradas

```kotlin
import io.kotest.property.forAll
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import kotlinx.coroutines.runBlocking

@Test
fun `column name concatenation preserves total length`() = runBlocking {
    forAll(Arb.string(1..50), Arb.string(1..50)) { a, b ->
        (a + b).length == a.length + b.length
    }
}
```

#### `checkAll` — o teste passa se nenhuma exceção for lançada (usa assertions)

```kotlin
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe

@Test
fun `BoardId created from valid UUID always has correct string representation`() = runBlocking {
    checkAll(Arb.uuid()) { uuid ->
        val id = BoardId(uuid)
        id.value shouldBe uuid
    }
}
```

**Diferença prática:**
- `forAll` → retorna `Boolean` — bom para invariantes matemáticas puras
- `checkAll` → usa `shouldBe`, `shouldThrow`, etc. — bom para assertions com kotest matchers

#### Configurar número de iterações

```kotlin
// padrão: 1000 iterações
checkAll<String>(PropTestConfig(iterations = 5_000)) { input ->
    // teste mais exaustivo para validação crítica
}
```

### Generators (Arb) mais usados

| Generator | Exemplo | O que gera |
|---|---|---|
| `Arb.string()` | `Arb.string(1..100)` | Strings Unicode, tamanho no range |
| `Arb.string(Codepoint.alphanumeric())` | — | Strings alfanuméricas |
| `Arb.int()` | `Arb.int(1..Int.MAX_VALUE)` | Inteiros com edge cases |
| `Arb.long()` | `Arb.long()` | Longs com edge cases |
| `Arb.uuid()` | `Arb.uuid()` | UUIDs aleatórios |
| `Arb.boolean()` | `Arb.boolean()` | `true` / `false` |
| `Arb.list(arb)` | `Arb.list(Arb.string(), 0..10)` | Listas de 0–10 elementos |
| `Arb.nonEmptyList(arb)` | — | Listas com ao menos 1 elemento |
| `Arb.email()` | `Arb.email()` | E-mails válidos |
| `Arb.enum<MyEnum>()` | `Arb.enum<Status>()` | Todos os valores de um enum |

### Custom Generators — domínio do projeto

Crie generators para os seus Value Objects e entidades usando `Arb.bind` e `.map`:

```kotlin
// Em um arquivo arbDomain.kt no diretório de test helpers
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

// Generator para BoardId válido
val arbBoardId: Arb<BoardId> = Arb.uuid().map { BoardId(it) }

// Generator para nome de coluna válido (1–50 chars, alfanumérico)
val arbColumnName: Arb<String> = Arb.string(1..50, Codepoint.alphanumeric())

// Generator para um Board usando Arb.bind
val arbBoard: Arb<Board> = Arb.bind(
    Arb.uuid(),
    Arb.string(1..100, Codepoint.alphanumeric()),
) { id, name ->
    Board(id = BoardId(id), name = name, columns = emptyList())
}
```

#### Filtrando valores com `.filter`

```kotlin
// Gera nomes de coluna inválidos (blank ou > 50 chars)
val arbInvalidColumnName: Arb<String> = Arb.string().filter { it.isBlank() || it.length > 50 }
```

> **Cuidado com `.filter`**: se o filtro for muito restritivo, o gerador tentará muitas
> vezes e vai lançar `GeneratorException`. Prefira generators específicos ao invés de
> filtrar grandes espaços.

### Padrões de property test para este projeto

#### Invariante de Value Object (domain)

```kotlin
@Test
fun `BoardId equality holds for same UUID`() = runBlocking {
    checkAll(Arb.uuid()) { uuid ->
        BoardId(uuid) shouldBe BoardId(uuid)
    }
}

@Test
fun `BoardId created from distinct UUIDs are never equal`() = runBlocking {
    forAll(Arb.uuid(), Arb.uuid()) { a, b ->
        a == b || BoardId(a) != BoardId(b)
    }
}
```

#### Invariante de regra de domínio (aggregate)

```kotlin
@Test
fun `Board addColumn always rejects blank names regardless of board state`() = runBlocking {
    checkAll(arbBoard, Arb.string().filter { it.isBlank() }) { board, blankName ->
        val result = board.addColumn(blankName)
        result.isLeft() shouldBe true  // sempre retorna erro para nomes blank
    }
}

@Test
fun `Board addColumn succeeds for any valid non-blank name not already in board`() = runBlocking {
    checkAll(arbBoard, arbColumnName) { board, name ->
        val freshBoard = board.copy(columns = emptyList())
        val result = freshBoard.addColumn(name)
        result.isRight() shouldBe true
    }
}
```

#### Round-trip de serialização (sql_persistence)

```kotlin
@Test
fun `serialization round-trip preserves Board data`() = runBlocking {
    checkAll(arbBoard) { board ->
        val json = Json.encodeToString(BoardSurrogate.serializer(), board.toSurrogate())
        val decoded = Json.decodeFromString(BoardSurrogate.serializer(), json).toDomain()
        decoded shouldBe board
    }
}
```

### Integração com JUnit 5

Nenhuma configuração extra é necessária. `forAll` e `checkAll` são `suspend fun` —
envolva com `runBlocking` em testes JUnit 5:

```kotlin
import kotlinx.coroutines.runBlocking

class BoardPropertyTest {
    @Test
    fun `any property test`() = runBlocking {
        forAll(arbBoard) { board ->
            board.id.value != null
        }
    }
}
```

> A task `useJUnitPlatform()` já está no convention plugin. Só adicione a dependência
> `kotest-property` — nenhuma outra configuração é necessária.

### Shrinking — como o Kotest apresenta falhas

Quando um property test falha, o Kotest **shrinks** automaticamente o valor para o
menor exemplo que ainda provoca a falha:

```
Property failed after 42 attempts
Cause: ...
Shrinks: 5
Shrunk value: Board(id=BoardId(value=00000000-...), name="a", columns=[])
```

Os generators padrão (`Arb.string`, `Arb.int`, etc.) já têm shrinkers embutidos.
Generators criados com `Arb.bind` herdam o shrinking dos generators internos.

### Como property tests afetam o JaCoCo

- Property tests **contam como cobertura normal** — as instruções executadas durante
  as 1000 iterações são registradas pelo JaCoCo normalmente.
- O volume de execuções tende a cobrir **branches** que testes manuais perderam,
  incluindo `else`/`null` paths raramente exercitados.
- Se um generator não conseguir cobrir um branch específico, adicione um teste de
  exemplo convencional para esse caso.

### Quando NÃO usar property testing

| Situação | Use em vez disso |
|---|---|
| Lógica que depende de estado externo (banco, HTTP) | Testes de integração com estado real |
| Comportamento que muda com fixtures específicas | Testes de exemplo com fixtures explícitas |
| Validação de mensagens de erro exatas | Testes de exemplo — a mensagem é um contrato |
| Performance de operações IO-bound | Benchmarks separados |

---

## 7. PITest — Mutation Testing

> **Referência:** https://pitest.org/quickstart/
>
> Reinertsen (*The Principles of Product Development Flow*): alta cobertura de linhas não
> garante qualidade de asserções. Mutantes que sobrevivem revelam testes que executam o
> código mas não verificam seu comportamento — o diagnóstico mais valioso que JaCoCo
> **não consegue dar**.

### Por que mutation testing?

JaCoCo mede **o que foi executado**. PITest mede **o que foi verificado**.
Um teste pode executar uma linha 1000 vezes sem nunca fazer uma asserção sobre ela —
o JaCoCo reporta 100% de cobertura, o PITest reporta 0% de mutação morta.

```
// Este "teste" passa no JaCoCo mas mata zero mutantes:
@Test
fun `run day does not throw`() {
    engine.runDay(command)  // executa, mas sem asserção alguma
}
```

O PITest aplica operadores de mutação ao bytecode e verifica se os testes existentes
detectam cada alteração. Se um teste não falha quando o código é corrompido, ele não
está realmente testando aquele comportamento.

### Conceitos fundamentais

#### Mutante

Uma cópia do bytecode com uma única alteração intencional aplicada por um operador
de mutação. Ex.: `if (count > 0)` → `if (count >= 0)`.

#### Estados de um mutante

| Estado | Significado | O que fazer |
|---|---|---|
| **Killed** | Um teste falhou — mutante detectado | ✅ Bom — o teste verifica o comportamento |
| **Survived** | Nenhum teste falhou | ❌ Fraqueza — adicione ou fortaleça asserções |
| **No coverage** | Nenhum teste executou aquela linha | ❌ Gap — escreva o teste faltante |
| **Timed Out** | Mutação causou loop infinito | ℹ️ Considerado killed automaticamente |
| **Non viable** | Bytecode inválido — JVM recusou carregar | ℹ️ Raro, ignorar |
| **Memory error** | Mutação causou OOM | ℹ️ Considerado killed automaticamente |

**Meta: maximizar Killed + Timed Out. Zero tolerância para Survived em lógica crítica.**

#### Mutações equivalentes

Algumas mutações não alteram o comportamento observável do programa — são
**mutações equivalentes**. Ex.: `>=1` → `>1` quando a variável nunca vale 1.
São inevitáveis; não causam falha de build — o PITest simplesmente não consegue
matar esses mutantes mesmo com testes perfeitos. Aceite-as como parte do score.

### Grupos de mutadores

O PITest organiza mutadores em grupos cumulativos:

| Grupo | Conteúdo | Uso |
|---|---|---|
| `DEFAULTS` | Mutadores padrão ativos por default | Baseline razoável |
| `STRONGER` | DEFAULTS + mutadores opcionais mais agressivos | **Usado neste projeto** |
| `ALL` | STRONGER + experimentais | Muito lento, gera ruído |

### Mutadores DEFAULTS (sempre ativos)

| Mutador | O que faz | Exemplo |
|---|---|---|
| `CONDITIONALS_BOUNDARY` | Substitui operadores relacionais pelos limites adjacentes | `a < b` → `a <= b` |
| `INCREMENTS` | Inverte `++` e `--` em variáveis locais | `i++` → `i--` |
| `INVERT_NEGS` | Inverte negação numérica | `return -i` → `return i` |
| `MATH` | Substitui operadores aritméticos e bitwise | `a + b` → `a - b`, `a & b` → `a \| b` |
| `NEGATE_CONDITIONALS` | Nega condicionais | `==` → `!=`, `<=` → `>` |
| `VOID_METHOD_CALLS` | Remove chamadas a métodos void | `logger.info(...)` removido |
| `EMPTY_RETURNS` | Substitui retornos por valores vazios | `String` → `""`, `List` → `emptyList()` |
| `FALSE_RETURNS` | Substitui `Boolean` return por `false` | `return isValid()` → `return false` |
| `TRUE_RETURNS` | Substitui `Boolean` return por `true` | `return isValid()` → `return true` |
| `NULL_RETURNS` | Substitui retornos de objeto por `null` | `return board` → `return null` |
| `PRIMITIVE_RETURNS` | Substitui primitivos por `0` | `return count` → `return 0` |

### Mutadores adicionais no grupo STRONGER

| Mutador | O que faz | Por que é mais agressivo |
|---|---|---|
| `CONSTRUCTOR_CALLS` | Substitui `new Foo()` por `null` | Pode causar NPE em cadeia |
| `INLINE_CONSTS` | Muta constantes literais: `true→false`, `5→-1→0` | Testa que constantes têm valor correto |
| `NON_VOID_METHOD_CALLS` | Remove chamadas a métodos não-void, substitui retorno por default | `int i = foo()` → `int i = 0` |
| `REMOVE_CONDITIONALS` | Força `if` a sempre executar ou nunca executar | `if (cond)` → sempre true ou sempre false |
| `REMOVE_INCREMENTS` | Remove `i++` sem substituir | Detecta que o incremento é necessário |

### Configuração neste projeto

```kotlin
// domain/build.gradle.kts
pitest {
    junit5PluginVersion.set("1.2.1")

    // Foco no SimulationEngine — lógica mais crítica de fila e WIP
    targetClasses.set(setOf("com.kanbanvision.domain.simulation.*"))
    targetTests.set(setOf("com.kanbanvision.domain.simulation.*"))

    mutators.set(setOf("STRONGER"))   // DEFAULT + mutadores opcionais agressivos

    // Baseline: 38% (70/182 mutantes mortos). Elevar progressivamente.
    // Reinertsen: 97% de line coverage ≠ qualidade de asserção no SimulationEngine.
    mutationThreshold.set(35)

    outputFormats.set(setOf("XML", "HTML"))
    timestampedReports.set(false)     // relatório em path fixo — facilita diff
    failWhenNoMutations.set(true)     // garante que o foco não ficou sem código
    threads.set(minOf(4, Runtime.getRuntime().availableProcessors()))
}
```

**Por que o threshold é 35% e não 80%?**
O baseline medido foi 38%. Definir threshold acima do baseline atual quebraria o CI
imediatamente. A estratégia é elevar progressivamente à medida que as asserções
melhoram — o PITest como guia de melhoria, não punição.

### Como interpretar o relatório HTML

```bash
# Gerar relatório
./gradlew :domain:pitest

# Abrir
open domain/build/reports/pitest/index.html
```

No relatório:
- **Verde** = mutante killed (✅ teste verificou o comportamento)
- **Vermelho** = mutante survived (❌ asserção fraca ou ausente)
- **Laranja** = no coverage (❌ código não testado)

Clique em um mutante survived para ver **exatamente qual linha foi mutada e qual
mutação específica sobreviveu** — isso aponta diretamente onde fortalecer o teste.

### Como matar um mutante survived

**Passo 1**: identifique a mutação no relatório. Ex.:
```
SimulationEngine.kt line 42: NEGATE_CONDITIONALS — survived
Original:  if (wipLimit > 0)
Mutated:   if (wipLimit <= 0)
```

**Passo 2**: veja qual teste cobre aquela linha (`No coverage` → escreva um teste;
`Survived` → o teste existe mas não verifica o comportamento afetado).

**Passo 3**: fortaleça ou adicione a asserção:

```kotlin
// ❌ teste que cobre a linha mas não mata o mutante
@Test
fun `run day with wip limit does not throw`() {
    engine.runDay(command)  // sem asserção sobre o efeito do WIP limit
}

// ✅ teste que mata o mutante — verifica o comportamento afetado
@Test
fun `run day respects WIP limit — cards beyond limit stay in queue`() {
    val result = engine.runDay(command)
    // asserção direta sobre o que muda quando wipLimit > 0 vs <= 0
    result.queuedCards shouldHaveSize (totalCards - wipLimit)
}
```

**Regra**: a asserção deve ser **sobre o valor que a mutação altera**. Se a mutação
inverte `>`, o teste deve verificar que o comportamento é diferente em `> 0` vs `<= 0`.

### Padrões de asserção que matam mutadores específicos

| Mutador que sobrevive | Asserção fraca | Asserção que mata |
|---|---|---|
| `CONDITIONALS_BOUNDARY` (`> → >=`) | `result != null` | `result.size == exactExpectedCount` |
| `FALSE_RETURNS` / `TRUE_RETURNS` | Não verifica o retorno | `result.isSuccess shouldBe true` |
| `NULL_RETURNS` | Não verifica conteúdo | `result shouldNotBe null` + `result.id shouldBe expectedId` |
| `EMPTY_RETURNS` | Não verifica tamanho | `result shouldHaveSize n` |
| `MATH` (`+ → -`) | Não verifica valor calculado | `result.totalFlow shouldBe expectedFlow` |
| `VOID_METHOD_CALLS` | Não verifica efeito colateral | Verificar que o estado mudou |
| `REMOVE_CONDITIONALS` | Executa sem verificar caminho | Teste para when-true E when-false |

### Relação entre JaCoCo e PITest

| Dimensão | JaCoCo | PITest |
|---|---|---|
| O que mede | Instruções executadas | Comportamento verificado |
| 100% = | Toda linha foi executada | Todo mutante foi morto |
| Fraqueza | Não detecta asserções ausentes | Lento; mutações equivalentes |
| Complementaridade | Gate obrigatório em todo PR | Guia de melhoria de testes |
| Gate neste projeto | ≥ 96% por módulo | ≥ 35% em `domain/` (baseline progressivo) |

**Regra**: JaCoCo é o floor (mínimo aceitável). PITest é o espelho (qualidade real).
Um score PITest alto com JaCoCo alto é o objetivo. JaCoCo alto com PITest baixo é
um sinal de testes que executam sem verificar — dívida técnica silenciosa.

### Incremental analysis — para codebase grande

Quando o projeto crescer e `./gradlew :domain:pitest` demorar demais:

```kotlin
// domain/build.gradle.kts — habilitar análise incremental
pitest {
    // ... config existente ...
    withHistory.set(true)  // armazena em java.io.tmpdir automaticamente
    // ou explicitamente:
    // historyInputLocation.set(file("$buildDir/pitest-history.bin"))
    // historyOutputLocation.set(file("$buildDir/pitest-history.bin"))
}
```

Com `withHistory`, o PITest evita re-testar mutantes cujo código e testes não mudaram —
dramaticamente mais rápido para PRs que tocam apenas uma parte do código.

### Tasks PITest disponíveis

| Task | O que faz |
|---|---|
| `./gradlew :domain:pitest` | Mutation testing no `domain/` (foco no SimulationEngine) |
| `./gradlew pitestAll` | Mutation testing em todos os módulos (mais lento) |

**PITest NÃO está em `check` nem em `testAll`** — é opt-in por ser lento.
O CI executa `:domain:pitest` em step separado e faz upload do relatório HTML como artefato.

### Elevando o threshold progressivamente

O threshold atual (35%) é o baseline medido. O objetivo de longo prazo:

| Marco | Score alvo | Ação necessária |
|---|---|---|
| Baseline | 35% | Estado atual — sem ação |
| Próximo ciclo | 50% | Fortalecer asserções nas top-10 survived mutations |
| Ciclo intermediário | 65% | Adicionar testes de boundary para condicionais |
| Meta de maturidade | 80% | Asserções precisas em toda lógica de fila e WIP |

**Nunca eleve o threshold sem primeiro verificar que o score atual supera o novo valor.**

---

## 8. Workflow Diário — Onde Cada Ferramenta Entra

### Ao escrever código novo

1. Escreva o código e os testes juntos (TDD ou test-after — nunca sem testes)
2. Rode `./gradlew :modulo:test` para validar lógica rapidamente
3. Rode `./gradlew ktlintFormat` para corrigir formatação automaticamente
4. Rode `./gradlew testAll` uma vez antes de commitar

### Ao corrigir uma violação do Detekt

Siga esta ordem de decisão:
```
Violação Detekt
    │
    ├── É um falso positivo documentado?
    │       └── Sim → @Suppress com comentário explicativo
    │
    ├── É código gerado pelo framework/compilador?
    │       └── Sim → @Suppress ou exclusão de arquivo no detekt.yml
    │
    └── É um problema real de design?
            └── Sim → REFATORE. Não suprima.
```

### Ao adicionar uma nova classe

Checklist antes de commitar:
- [ ] A classe tem testes cobrindo sucesso e falha?
- [ ] Funções têm ≤ 30 linhas e complexidade ≤ 10?
- [ ] Nenhum número mágico solto — constantes nomeadas?
- [ ] A classe tem ≤ 11 funções (ou foi dividida)?
- [ ] Sem imports wildcard?
- [ ] `./gradlew testAll` verde?

### Ao corrigir comentários de PR

Sempre que um comentário de revisão (humano ou Copilot) for endereçado, **responda
ao comentário no GitHub imediatamente após o push** — nunca deixe a resposta para depois.
Isso evita desalinhamento entre o que foi corrigido e o que o revisor vê na interface.

**Protocolo obrigatório — execute nesta ordem:**

```bash
# 1. Faça a correção no código
# 2. Commit e push
git add <arquivo> && git commit -m "fix: ..." && git push

# 3. Responda cada comentário — referenciando o commit hash
gh api repos/<owner>/<repo>/pulls/comments/<comment-id>/replies \
  -X POST -f body="Corrigido no commit <hash> — <descrição objetiva do que mudou>."
```

**Como obter os IDs dos comentários pendentes:**

```bash
gh api repos/<owner>/<repo>/pulls/<pr-number>/comments \
  --jq '.[] | {id: .id, line: .line, body: .body}'
```

**Regras da resposta:**
- Mencione o **commit hash** (curto) onde a correção foi aplicada
- Descreva **o que mudou** em uma frase (ex: "threshold atualizado de 90% → 95%")
- Não deixe comentários sem resposta após o push — o GitHub os marca como "outdated"
  no diff mas continua exibindo na aba de comentários, causando confusão

### Ao mexer em configuração de qualidade

| Mudança | Aprovação necessária |
|---|---|
| Aumentar threshold de complexidade | Requer revisão — sinal de dívida técnica |
| Adicionar exclusão no JaCoCo | Justificativa obrigatória no PR |
| Baixar o gate de cobertura | Proibido — trate o problema real |
| Adicionar `@Suppress` | Comentário obrigatório com motivo |
| Versão do KtLint/Detekt | Pode quebrar regras existentes — faça em PR isolado |

---

## 9. Diagnóstico de Falhas Comuns

### Detekt: `TooGenericExceptionCaught`

```kotlin
// ❌ captura genérica
try { ... } catch (e: Exception) { ... }

// ✅ específico
try { ... } catch (e: IllegalArgumentException) { ... }
// ou crie uma exceção de domínio:
try { ... } catch (e: DatabaseException) { ... }
```

### Detekt: `MagicNumber`

```kotlin
// ❌ número mágico
if (poolSize > 10) error("Pool too large")

// ✅ constante nomeada
private const val MAX_POOL_SIZE = 10
if (poolSize > MAX_POOL_SIZE) error("Pool too large")
```

### Detekt: `ForbiddenComment`

```kotlin
// ❌ vai quebrar o build
// FIXME: isso está errado mas funciona por ora

// ✅ crie um ticket e referencie
// TODO: ticket #42 — substituir por implementação assíncrona
```

### KtLint: `NewLineAtEndOfFile`

Todo arquivo `.kt` deve terminar com uma quebra de linha (`\n`) no final do arquivo.
Configure o editor ou use `./gradlew ktlintFormat`.

### JaCoCo: cobertura caiu após adicionar código

```bash
# 1. Gere o relatório
./gradlew :modulo:jacocoTestReport

# 2. Abra
open modulo/build/reports/jacoco/test/html/index.html

# 3. Encontre as linhas vermelhas/amarelas e escreva o teste faltante
```

Amarelo = branch não coberto (ex: o `else` de um `if` nunca foi testado).
Vermelho = instrução nunca executada nos testes.

### Gradle: task de um módulo específico

```bash
# Formato: ./gradlew :nome-do-modulo:nome-da-task
./gradlew :domain:detekt
./gradlew :usecases:test
./gradlew :http_api:jacocoTestReport
./gradlew :sql_persistence:ktlintCheck
```

---

## 10. Referência Rápida

```bash
# Pipeline completo (use antes de todo PR)
./gradlew testAll

# Formatar automaticamente
./gradlew ktlintFormat

# Teste único
./gradlew :modulo:test --tests "com.kanbanvision.pacote.ClasseTest"

# Ver relatório de cobertura
open modulo/build/reports/jacoco/test/html/index.html

# Ver relatório de Detekt
open modulo/build/reports/detekt/detekt.html

# Compilar sem testar
./gradlew compileKotlin

# Rodar só property tests de um módulo (são JUnit 5 normais)
./gradlew :domain:test --tests "*PropertyTest"

# PITest — mutation testing (opt-in)
./gradlew :domain:pitest                     # SimulationEngine — foco principal
./gradlew pitestAll                          # todos os módulos (lento)
open domain/build/reports/pitest/index.html  # relatório HTML
```

---

## 11. Princípios Não Negociáveis

1. **`warningsAsErrors = true` nunca é desativado** — aviso não visto é bug em produção
2. **Gate de cobertura 96% nunca desce** — escreva o teste, não baixe o número
3. **`@Suppress` exige justificativa** — sem comentário, o PR não passa
4. **`ktlintFormat` antes do commit** — não perca tempo revisando formatação em PR
5. **Exclusões do JaCoCo são documentadas** — só para código não testável por natureza (lambdas de DSL, serializadores gerados)
6. **Convention plugin é a única fonte de verdade** — não duplique config em módulos
7. **Property tests verificam invariantes, não casos fixos** — se você está hardcoding o input, use um teste de exemplo
8. **Threshold PITest só sobe, nunca desce** — cada elevação exige que o score atual já supere o novo valor
9. **JaCoCo alto + PITest baixo = dívida silenciosa** — testes que executam sem verificar são piores que ausentes porque dão falsa segurança
10. **Mutante survived é um TODO** — registre ou resolva; nunca ignore sem entender o que o mutante revela