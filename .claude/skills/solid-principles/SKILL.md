argument-hint: "[class or module to review (optional)]"
allowed-tools: Read, Grep, Glob
---
name: solid-principles
description: >
  Aplique e verifique os cinco princípios SOLID ao escrever, revisar ou refatorar
  qualquer código neste projeto. Use este skill ao criar classes, interfaces, casos
  de uso, adaptadores ou módulos — e ao avaliar se código existente precisa ser
  reestruturado. Complementa clean-architecture e screaming-architecture.
---

# Princípios SOLID — Aplicação e Verificação

> "A melhor forma de criar uma bagunça complicada é dizer a todos para
> 'simplesmente escrever código simples' sem oferecer nenhuma orientação adicional."
> — Robert C. Martin (Uncle Bob), 2020
> Fonte: https://blog.cleancoder.com/uncle-bob/2020/10/18/Solid-Relevance.html

---

## Por Que SOLID Ainda Importa

Software não mudou fundamentalmente desde 1945. Todo código ainda é composto de
**sequência, seleção e iteração** — `if`, `while` e atribuição. Cada geração de
desenvolvedores acredita que seu contexto é radicalmente diferente e que os
princípios antigos não se aplicam. Cada geração descobre que estava errada.

Microserviços não eliminam a necessidade de SOLID. É plenamente possível construir
microserviços desorganizados que violam todos os cinco princípios. A escala do
artefato não substitui o design interno.

SOLID não é um conjunto de regras burocráticas — é **o que código simples significa
na prática**. Cada princípio resolve um problema específico de manutenção, testabilidade
ou extensibilidade que reaparece independentemente da tecnologia usada.

---

## S — Single Responsibility Principle (SRP)

### Definição

> "Reúna o que muda pelas mesmas razões. Separe o que muda por razões diferentes."

Cada módulo, classe ou função deve ter **um único ator** responsável por motivar
sua mudança. Não se trata de "fazer uma só coisa" — trata-se de **pertencer a um
único contexto de mudança**.

### O Problema que Resolve

Quando código que serve a dois atores diferentes vive no mesmo lugar, uma mudança
motivada por um ator quebra acidentalmente o comportamento esperado pelo outro.

```kotlin
// ❌ Violação: Board mistura regra de negócio com formatação de apresentação
class Board(val id: BoardId, val name: String) {
    fun validate() { ... }          // regra de negócio — muda com o domínio
    fun toDisplayString(): String { // formatação — muda com a UI
        return "Board: $name [$id]"
    }
    fun toJson(): String { ... }    // serialização — muda com o protocolo HTTP
}

// ✅ Cada responsabilidade no lugar correto
// domain/model/Board.kt — só regra de negócio
data class Board(val id: BoardId, val name: String)

// http_api/routes/BoardRoutes.kt — serialização e formatação HTTP
@Serializable
data class BoardResponse(val id: String, val name: String)
```

### Como Verificar no Código

Faça esta pergunta para cada classe:

> **"Quais pessoas/times diferentes poderiam me pedir para mudar esta classe?"**

Se a resposta tiver mais de um ator (equipe de negócio, equipe de UI, equipe de
banco de dados), a classe provavelmente viola o SRP.

### Sinais de Violação

| Sinal | Diagnóstico |
|---|---|
| Classe com métodos de negócio e métodos HTTP juntos | Mistura de responsabilidades |
| UseCase que também valida formato de entrada HTTP | Responsabilidade de adaptador no núcleo |
| Entidade com `toJson()` ou `toDisplayString()` | Serialização no domínio |
| Repositório com lógica de negócio | Persistência contendo regra |
| Rota HTTP com `if` de regra de negócio | Lógica de domínio no adaptador |

### Neste Projeto

```
domain/model/Board.kt         ← regra de negócio pura (ator: domínio)
http_api/routes/BoardRoutes.kt ← serialização/HTTP (ator: protocolo)
sql_persistence/JdbcBoardRepository.kt ← persistência (ator: banco de dados)
usecases/CreateBoardUseCase.kt ← orquestração (ator: requisito de aplicação)
```

Cada arquivo tem exatamente um ator que motiva sua mudança.

---

## O — Open-Closed Principle (OCP)

### Definição

> "Um módulo deve ser aberto para extensão, mas fechado para modificação."

Quando os requisitos mudam, você deve poder **adicionar** comportamento novo sem
**alterar** o comportamento que já funciona corretamente.

### O Problema que Resolve

Se toda nova funcionalidade exige modificar código existente e correto, o risco de
regressão é contínuo. OCP não é sobre não modificar nunca — é sobre isolar o que
está correto do que precisa mudar.

```kotlin
// ❌ Violação: adicionar novo tipo de notificação exige modificar NotificationService
class NotificationService {
    fun notify(event: BoardEvent, type: String) {
        when (type) {
            "email" -> sendEmail(event)
            "slack" -> sendSlack(event)
            // toda nova integração exige modificar este when — risco de regressão
        }
    }
}

// ✅ Extensão sem modificação via abstração
interface BoardEventListener {
    fun onEvent(event: BoardEvent)
}

class EmailNotifier : BoardEventListener {
    override fun onEvent(event: BoardEvent) { ... }
}

class SlackNotifier : BoardEventListener {
    override fun onEvent(event: BoardEvent) { ... }
}

// NotificationService nunca muda — apenas novos Listeners são adicionados
class NotificationService(private val listeners: List<BoardEventListener>) {
    fun notify(event: BoardEvent) = listeners.forEach { it.onEvent(event) }
}
```

### Como Verificar no Código

> **"Para adicionar esta nova funcionalidade, precisei modificar código que já
> estava funcionando?"**

Se sim: avalie se uma abstração poderia ter isolado o ponto de variação.

### Sinais de Violação

| Sinal | Diagnóstico |
|---|---|
| `when (type)` ou `if (type == "x")` que cresce a cada feature | Ponto de variação sem abstração |
| Modificar UseCase existente para adicionar comportamento novo | Falta de extensão por composição |
| Testes antigos quebrando para adicionar feature nova | Código correto sendo alterado |
| Classe com mais de uma responsabilidade variando | SRP + OCP violados juntos |

### Neste Projeto

A interface `BoardRepository` é o OCP em ação: o UseCase `CreateBoardUseCase`
nunca precisará ser modificado para suportar um novo banco de dados. Basta criar
uma nova implementação de `BoardRepository`.

---

## L — Liskov Substitution Principle (LSP)

### Definição

> "Um programa que usa uma interface não deve ser surpreendido por uma implementação
> dessa interface."

**Correção importante de Uncle Bob**: LSP não é apenas sobre herança de classes.
É sobre **subtipagem** em qualquer forma — toda implementação de interface é um
subtipo dessa interface. Duck typing também cria subtipos implícitos.

### O Problema que Resolve

Quando uma implementação concreta se comporta de forma diferente do contrato definido
pela interface, o código que depende da interface quebra de formas inesperadas —
geralmente em produção, não nos testes.

```kotlin
// Interface define o contrato
interface CardRepository {
    // contrato implícito: retorna null se não encontrado, nunca lança exceção
    fun findById(id: CardId): Card?
}

// ✅ Implementação honra o contrato
class JdbcCardRepository : CardRepository {
    override fun findById(id: CardId): Card? {
        return try {
            // busca no banco, retorna null se não encontrar
            queryDatabase(id)
        } catch (e: SQLException) {
            null // converte exceção técnica para o contrato da interface
        }
    }
}

// ❌ Violação de LSP — lança exceção que o contrato não prevê
class BrokenCardRepository : CardRepository {
    override fun findById(id: CardId): Card? {
        throw DatabaseException("Sempre falha") // surpreende quem usa a interface
    }
}
```

### Contrato Além da Assinatura

O contrato de uma interface inclui:

| Aspecto | Exemplos |
|---|---|
| **Pré-condições** | Não pode exigir mais do que a interface promete aceitar |
| **Pós-condições** | Não pode entregar menos do que a interface promete retornar |
| **Invariantes** | Não pode violar comportamento que o chamador assume como constante |
| **Exceções** | Não pode lançar exceções que a interface não declara |

### Como Verificar no Código

> **"Se eu trocar esta implementação por outra que respeita a mesma interface,
> todos os testes continuam passando sem nenhuma modificação?"**

Se precisar modificar testes para trocar uma implementação, há uma violação de LSP.

### Sinais de Violação

| Sinal | Diagnóstico |
|---|---|
| `if (repository is JdbcCardRepository)` no UseCase | Código dependendo da implementação concreta |
| Implementação lança exceção não declarada na interface | Surpresa no contrato |
| Subclasse que lança `UnsupportedOperationException` | Violação clássica |
| Testes diferentes para cada implementação da mesma interface | Contratos divergentes |
| Mock que se comporta diferente da implementação real | LSP não verificado nos testes |

---

## I — Interface Segregation Principle (ISP)

### Definição

> "Mantenha interfaces pequenas para que os usuários não dependam de coisas
> que não precisam."

Nenhum módulo deve ser forçado a depender de métodos que não usa. Especialmente
em linguagens estaticamente tipadas (Kotlin, Java, Go, Swift), dependências de
compilação desnecessárias forçam recompilação e aumentam acoplamento invisível.

### O Problema que Resolve

Quando uma interface é grande demais, todo cliente que depende dela precisa ser
recompilado quando qualquer parte dela muda — mesmo a parte que o cliente nunca usa.

```kotlin
// ❌ Interface gorda — UseCase de leitura é forçado a depender de save/delete
interface BoardRepository {
    fun save(board: Board)
    fun delete(id: BoardId)
    fun findById(id: BoardId): Board?
    fun findAll(): List<Board>
    fun findByName(name: String): List<Board>
    fun countAll(): Int
    fun existsById(id: BoardId): Boolean
}

// ✅ Interfaces segregadas por uso
interface BoardWriter {
    fun save(board: Board)
    fun delete(id: BoardId)
}

interface BoardReader {
    fun findById(id: BoardId): Board?
    fun findAll(): List<Board>
}

// CreateBoardUseCase depende apenas do que usa
class CreateBoardUseCase(private val writer: BoardWriter) { ... }

// GetBoardUseCase depende apenas do que usa
class GetBoardUseCase(private val reader: BoardReader) { ... }
```

### Nível de Módulo — ISP no Gradle

ISP não se aplica só a interfaces — aplica-se a módulos e dependências:

```kotlin
// ❌ http_api depende de sql_persistence inteiro só para usar uma classe
implementation(project(":sql_persistence"))

// ✅ Exponha apenas o necessário via api() — oculte o resto com implementation()
// Em sql_persistence/build.gradle.kts:
api(project(":domain"))          // quem depende de sql_persistence vê domain
implementation("com.zaxxer:HikariCP:6.3.0")  // HikariCP NÃO vaza para fora
```

### Como Verificar no Código

> **"Esta classe/módulo importa algo que nunca usa?"**

Se sim: ou a interface pode ser segregada, ou a dependência pode ser removida.

### Sinais de Violação

| Sinal | Diagnóstico |
|---|---|
| Interface com mais de 5-6 métodos | Candidata à segregação |
| UseCase importando interface que tem métodos que nunca chama | Interface gorda |
| Módulo com `implementation(project(":modulo"))` só para uma classe | Dependência excessiva |
| Teste que precisa mockar 10 métodos para testar 1 | Interface muito grande |
| Classe que implementa interface mas deixa metade com `TODO()` | LSP + ISP violados |

---

## D — Dependency Inversion Principle (DIP)

### Definição

> "Dependa na direção da abstração. Módulos de alto nível não devem depender
> de detalhes de baixo nível."

Tanto os módulos de alto nível (regras de negócio) quanto os de baixo nível
(banco de dados, HTTP) devem depender de **abstrações**. As abstrações não devem
depender dos detalhes — os detalhes devem depender das abstrações.

### O Problema que Resolve

Quando regras de negócio conhecem detalhes técnicos (SQL, HTTP, nomes de tabelas,
formatos de arquivo), qualquer mudança técnica contamina o núcleo do sistema.

```kotlin
// ❌ UseCase depende de implementação concreta — DIP violado
class CreateBoardUseCase {
    private val repository = JdbcBoardRepository() // depende do detalhe concreto

    fun execute(command: CreateBoardCommand): BoardId {
        // se o banco mudar, este UseCase precisa ser alterado
    }
}

// ✅ UseCase depende de abstração — DIP respeitado
class CreateBoardUseCase(
    private val repository: BoardRepository, // depende da interface (abstração)
) {
    fun execute(command: CreateBoardCommand): BoardId {
        // nenhuma referência a JDBC, SQL ou banco específico
    }
}

// A abstração (interface) pertence ao UseCase, não ao adaptador
// usecases/repositories/BoardRepository.kt
interface BoardRepository {
    fun save(board: Board)
    fun findById(id: BoardId): Board?
}

// O detalhe concreto implementa a abstração e depende dela
// sql_persistence/repositories/JdbcBoardRepository.kt
class JdbcBoardRepository : BoardRepository { ... }
```

### A Direção das Interfaces Importa

**A interface deve ser definida no módulo de quem a usa (alto nível), não no módulo
de quem a implementa (baixo nível).**

```
usecases/ define BoardRepository (interface)
             ↑
sql_persistence/ implementa BoardRepository (detalhe)
```

Se a interface fosse definida em `sql_persistence/`, o UseCase passaria a depender
do módulo de banco de dados — invertendo a Dependency Rule.

### Como Verificar no Código

> **"Se eu remover completamente o módulo de banco de dados, o módulo de casos
> de uso ainda compila?"**

Deve compilar. Se não compilar, há uma dependência errada.

### Sinais de Violação

| Sinal | Diagnóstico |
|---|---|
| `import com.kanbanvision.persistence.*` em `usecases/` | UseCase conhece implementação |
| Interface definida no módulo do adaptador | Abstração no lugar errado |
| `new JdbcBoardRepository()` dentro de um UseCase | Instância concreta no núcleo |
| SQL literal em qualquer arquivo fora de `sql_persistence/` | Detalhe técnico no domínio |
| UseCase importando classe de framework (Ktor, Koin, HikariCP) | Alto nível dependendo de detalhe |

---

## SOLID como Sistema — Os Princípios se Reforçam

Os cinco princípios não são independentes — violações tendem a aparecer juntas:

```
SRP violado
    └── classe grande com múltiplas responsabilidades
            └── OCP violado (when/if que cresce)
                    └── ISP violado (interface gorda para servir múltiplos clientes)
                            └── DIP violado (dependências concretas espalhadas)
                                    └── LSP violado (contratos implícitos divergentes)
```

Corrija o SRP e frequentemente os outros se resolvem também.

---

## Checklist de Verificação SOLID por Tipo de Entrega

### Ao criar uma nova classe

- [ ] **SRP**: Existe apenas um ator que pode motivar a mudança desta classe?
- [ ] **OCP**: A classe permite extensão de comportamento sem modificação do seu corpo?
- [ ] **LSP**: Se esta classe implementa uma interface, honra completamente o contrato?
- [ ] **ISP**: A classe depende apenas dos métodos que realmente usa?
- [ ] **DIP**: A classe depende de abstrações (interfaces), não de implementações concretas?

### Ao criar uma nova interface

- [ ] **ISP**: A interface tem apenas os métodos que todos os seus clientes precisam?
- [ ] **LSP**: O contrato está completo — pré-condições, pós-condições, exceções?
- [ ] **DIP**: A interface está definida no módulo de quem a usa (alto nível)?

### Ao criar um novo módulo/pacote

- [ ] **SRP**: O módulo tem uma única razão para existir (um único ator responsável)?
- [ ] **DIP**: As dependências entre módulos apontam da periferia para o núcleo?
- [ ] **ISP**: O módulo expõe apenas o necessário (sem vazar implementações internas)?

### Ao fazer uma revisão de código (PR review)

- [ ] `when`/`if` com type-checking → possível OCP e SRP violados
- [ ] Interface com muitos métodos → possível ISP violado
- [ ] `import` de módulo externo em `domain/` ou `usecases/` → DIP violado
- [ ] Implementação com comportamento inesperado → LSP violado
- [ ] Classe modificada para adicionar feature nova → OCP violado

---

## Relação com Clean Architecture e Screaming Architecture

| Skill | Foco | Como se relaciona com SOLID |
|---|---|---|
| **Screaming Architecture** | Estrutura comunica o domínio | SRP: cada módulo tem um único propósito de negócio |
| **Clean Architecture** | Dependency Rule (dependências apontam para dentro) | DIP: abstrações no núcleo, detalhes na periferia |
| **SOLID** | Design interno de classes e interfaces | Garante que cada peça individual do sistema seja bem formada |

Clean Architecture define **onde** as coisas ficam.
Screaming Architecture define **como** a estrutura comunica propósito.
SOLID define **como** cada classe e interface é construída internamente.

Os três juntos são o tripé de qualidade deste projeto.

---

## Referência Rápida — Perguntas de Diagnóstico

| Princípio | Pergunta-chave |
|---|---|
| **SRP** | Quem pode me pedir para mudar esta classe? Se mais de um ator → violar |
| **OCP** | Precisei modificar código correto para adicionar esta feature? Se sim → violar |
| **LSP** | Trocar a implementação concreta quebra algum teste? Se sim → violar |
| **ISP** | Esta classe depende de métodos que nunca usa? Se sim → violar |
| **DIP** | Módulos de alto nível importam módulos de baixo nível? Se sim → violar |