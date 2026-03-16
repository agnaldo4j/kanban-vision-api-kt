---
name: kotlin-quality-pipeline
description: >
  Guia completo para aplicar e manter o pipeline de qualidade Kotlin neste projeto:
  Gradle 8 (Kotlin DSL), Detekt, KtLint e JaCoCo. Use este skill sempre que for
  adicionar código novo, corrigir violações, ajustar exclusões de cobertura ou
  configurar qualquer uma dessas ferramentas.
---

# Pipeline de Qualidade Kotlin — Detekt · KtLint · JaCoCo · Gradle 8

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
| JaCoCo < 90% | Escreva o teste faltante — nunca baixe o threshold nem adicione exclusão |

**Se uma exceção for realmente necessária** (ex: código gerado, DSL declarativa irredutível),
documente no PR com justificativa e aguarde aprovação humana explícita.

---

## 1. Como as ferramentas se encaixam

```
./gradlew testAll
        │
        ├── :*:detekt            ← análise estática
        ├── :*:ktlintCheck       ← formatação
        ├── :*:test              ← testes (JUnit 5)
        │       └── finalizedBy jacocoTestReport
        └── :*:jacocoTestCoverageVerification  ← gate de cobertura (≥ 90%)
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
        rule { limit { minimum = "0.90".toBigDecimal() } }
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

### Gate de cobertura: 90% de instruções

O build falha se qualquer módulo ativo ficar abaixo de 90% de cobertura de instruções.
Essa métrica é a mais objetiva: conta instruções bytecode executadas, não linhas.

```kotlin
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                minimum = "0.90".toBigDecimal()
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
**Use o relatório, não o número.** 90% com as partes certas cobertas vale mais que 95% cobrindo getters.

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

**Quando a cobertura cai abaixo de 90%:**
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

## 6. Workflow Diário — Onde Cada Ferramenta Entra

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

### Ao mexer em configuração de qualidade

| Mudança | Aprovação necessária |
|---|---|
| Aumentar threshold de complexidade | Requer revisão — sinal de dívida técnica |
| Adicionar exclusão no JaCoCo | Justificativa obrigatória no PR |
| Baixar o gate de cobertura | Proibido — trate o problema real |
| Adicionar `@Suppress` | Comentário obrigatório com motivo |
| Versão do KtLint/Detekt | Pode quebrar regras existentes — faça em PR isolado |

---

## 7. Diagnóstico de Falhas Comuns

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

## 8. Referência Rápida

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
```

---

## 9. Princípios Não Negociáveis

1. **`warningsAsErrors = true` nunca é desativado** — aviso não visto é bug em produção
2. **Gate de cobertura 90% nunca desce** — escreva o teste, não baixe o número
3. **`@Suppress` exige justificativa** — sem comentário, o PR não passa
4. **`ktlintFormat` antes do commit** — não perca tempo revisando formatação em PR
5. **Exclusões do JaCoCo são documentadas** — só para código não testável por natureza
6. **Convention plugin é a única fonte de verdade** — não duplique config em módulos