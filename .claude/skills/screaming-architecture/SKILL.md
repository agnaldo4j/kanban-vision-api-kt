---
name: screaming-architecture
description: >
  Aplique os princípios de Screaming Architecture (Uncle Bob, 2011) ao projetar
  e avaliar a estrutura de pacotes, módulos e camadas deste projeto. Use este skill
  sempre que criar um novo módulo, pacote, caso de uso, rota ou classe — e ao revisar
  se a estrutura atual ainda comunica a intenção de negócio corretamente.
argument-hint: "[package, module or class to evaluate (optional)]"
allowed-tools: Read, Grep, Glob
---

# Screaming Architecture

> "Sua arquitetura deve gritar o propósito do sistema, não os frameworks utilizados."
> — Robert C. Martin (Uncle Bob), 2011
> Fonte: https://blog.cleancoder.com/uncle-bob/2011/09/30/Screaming-Architecture.html

---

## O Princípio Central

Quando um arquiteto entrega os plantas de um prédio, você imediatamente reconhece
o que aquele prédio é: uma casa, uma biblioteca, um hospital. Os cômodos, a disposição
dos espaços e o fluxo de circulação contam a história do edifício antes de qualquer
palavra escrita.

O mesmo deve valer para código.

Ao abrir o repositório de um sistema de gestão de tarefas Kanban, a primeira coisa
que você deve ver é: **boards, colunas, cartões, casos de uso de negócio** — não
"ktor", "koin", "jdbc", "http", "spring", "rails".

Se a estrutura do projeto grita o nome do framework antes de gritar o domínio,
a arquitetura falhou.

---

## O Teste da Primeira Impressão

Ao navegar pela raiz de um projeto pela primeira vez, faça esta pergunta:

> **"O que este sistema faz?"**

Se a resposta for um framework ou tecnologia → arquitetura ruim.
Se a resposta for o domínio de negócio → arquitetura boa.

| Estrutura | O que grita | Avaliação |
|---|---|---|
| `controllers/`, `models/`, `views/` | MVC / Rails | ❌ grita framework |
| `http/`, `database/`, `config/` | Camadas técnicas | ❌ grita infraestrutura |
| `board/`, `card/`, `column/`, `usecases/` | Domínio Kanban | ✅ grita propósito |
| `domain/`, `usecases/`, `sql_persistence/`, `http_api/` | Negócio + adaptadores | ✅ grita intenção |

---

## Como Este Projeto Aplica o Princípio

### Estrutura de módulos

```
domain/          ← O QUE o sistema é (entidades e regras puras)
usecases/        ← O QUE o sistema faz (casos de uso nomeados pelo negócio)
sql_persistence/ ← COMO os dados são guardados (detalhe técnico, isolado)
http_api/        ← COMO o sistema é entregue (detalhe técnico, isolado)
```

Os dois primeiros módulos respondem "o que". Os dois últimos respondem "como".
Nunca inverta essa relação.

### Estrutura de pacotes dentro de `usecases/`

```
usecases/
├── board/
│   ├── commands/CreateBoardCommand.kt   ← ação de negócio
│   ├── queries/GetBoardQuery.kt         ← consulta de negócio
│   ├── CreateBoardUseCase.kt            ← caso de uso nomeado pelo domínio
│   └── GetBoardUseCase.kt
├── card/
│   ├── commands/CreateCardCommand.kt
│   ├── commands/MoveCardCommand.kt      ← "mover cartão" é linguagem de negócio
│   ├── queries/GetCardQuery.kt
│   ├── CreateCardUseCase.kt
│   ├── MoveCardUseCase.kt
│   └── GetCardUseCase.kt
└── repositories/
    ├── BoardRepository.kt               ← porta (interface), não implementação
    └── CardRepository.kt
```

Um novo desenvolvedor vê `MoveCardUseCase` e entende imediatamente: **este sistema
move cartões entre colunas**. Não precisa saber nada de Ktor, JDBC ou Koin.

---

## Referência Teórica: Ivar Jacobson e Casos de Uso

Uncle Bob fundamenta Screaming Architecture no trabalho de Ivar Jacobson
(*Object Oriented Software Engineering*, 1992): arquiteturas devem ser estruturadas
em torno dos **casos de uso do sistema**, não das tecnologias de suporte.

Um caso de uso é uma intenção de negócio:
- "Criar um quadro Kanban"
- "Mover um cartão para outra coluna"
- "Consultar o estado atual de um board"

Frameworks, bancos de dados e protocolos HTTP são apenas **mecanismos de entrega**
desses casos de uso. Eles não definem o sistema — eles servem o sistema.

---

## Frameworks São Ferramentas, Não Filosofias

Uncle Bob adverte sobre o entusiasmo excessivo com frameworks:

> Frameworks são uma ferramenta de que você se utiliza, não uma arquitetura
> que você adota. Se sua arquitetura é baseada em frameworks, ela não pode
> ser baseada em seus casos de uso.

### Sinais de que o framework está dominando a arquitetura

- Entidades de domínio herdam de classes do framework (`extends ActiveRecord`,
  `@Entity`, `@Document`)
- Casos de uso dependem de objetos HTTP (`HttpRequest`, `ApplicationCall`) diretamente
- Testes de lógica de negócio exigem subir um servidor ou um container de DI
- O nome do framework aparece mais no código do que nomes do domínio

### Como este projeto protege o domínio

```kotlin
// ✅ Entidade pura — zero dependências de framework
data class Board(val id: BoardId, val name: String)

// ✅ Caso de uso recebe uma Command tipada, não um objeto HTTP
class CreateBoardUseCase(private val repository: BoardRepository) {
    fun execute(command: CreateBoardCommand): BoardId { ... }
}

// ✅ A rota HTTP é um adaptador — chama o caso de uso, não contém lógica
post("/boards") {
    val request = call.receive<CreateBoardRequest>()
    val boardId = createBoard.execute(CreateBoardCommand(name = request.name))
    call.respond(HttpStatusCode.Created, BoardResponse(...))
}
```

A lógica de negócio não sabe que existe HTTP. O Ktor não sabe como funciona
o domínio. Cada camada faz exatamente uma coisa.

---

## A Web É um Detalhe

Este é um dos pontos mais contraintuitivos do artigo. Uncle Bob é enfático:

> A web é um mecanismo de entrega, não uma arquitetura.

Isso significa que a decisão de expor o sistema via HTTP, CLI, fila de mensagens
ou interface gráfica deve ser **postergável e substituível** sem tocar no domínio
ou nos casos de uso.

Na prática neste projeto:
- `domain/` e `usecases/` não têm dependência nenhuma de HTTP
- `http_api/` é o único módulo que conhece Ktor — e ele só faz wiring
- Os testes de `domain/` e `usecases/` rodam sem servidor, sem banco, sem framework

---

## Postergação de Decisões Técnicas

Uma arquitetura que grita o domínio permite adiar escolhas técnicas:

| Decisão | Quando pode ser postergada |
|---|---|
| Qual banco de dados usar | Até os requisitos de performance serem conhecidos |
| SQL vs NoSQL | Até o modelo de dados estabilizar |
| REST vs GraphQL vs gRPC | Até os consumidores da API serem definidos |
| Qual framework HTTP usar | Até os requisitos de throughput serem claros |
| Qual container de DI usar | Sempre — é detalhe de wiring |

Se o domínio e os casos de uso estiverem isolados, trocar qualquer um desses
elementos é uma tarefa localizada — não uma reescrita.

---

## Testabilidade Como Medida de Pureza

Uncle Bob usa testabilidade como critério objetivo:

> Se você precisa subir um servidor web ou conectar a um banco de dados para
> testar sua lógica de negócio, sua arquitetura falhou em isolar o domínio.

### Pirâmide de testes neste projeto

```
         ┌─────────────────────┐
         │  Testes de rota     │  ← testApplication (Ktor test engine)
         │  (http_api)         │    sem banco real, sem servidor real
         └──────────┬──────────┘
                    │
         ┌──────────▼──────────┐
         │  Testes de caso de  │  ← MockK para repositórios
         │  uso (usecases)     │    zero framework, zero banco
         └──────────┬──────────┘
                    │
         ┌──────────▼──────────┐
         │  Testes de domínio  │  ← JUnit puro, sem nenhuma dependência
         │  (domain)           │    entidades e regras isoladas
         └─────────────────────┘
```

Cada camada é testável de forma completamente isolada. Isso só é possível porque
o domínio não conhece nenhuma camada externa.

---

## Checklist ao Criar Estrutura Nova

Use estas perguntas antes de criar qualquer pacote, módulo ou classe:

### Para novos pacotes/módulos

- [ ] O nome reflete o domínio de negócio, não uma tecnologia?
- [ ] Um novo desenvolvedor entenderia o propósito lendo apenas o nome?
- [ ] O módulo tem dependências somente para módulos "mais internos" (domain ← usecases)?

### Para novas classes

- [ ] A classe vive no módulo correto (lógica de negócio em `domain`/`usecases`, não em `http_api`)?
- [ ] A classe de domínio é um Plain Object, sem herança ou anotações de framework?
- [ ] A classe pode ser testada sem subir nenhum servidor ou banco de dados?
- [ ] O nome da classe descreve uma intenção de negócio (`MoveCardUseCase`, não `CardHandler`)?

### Para novas dependências

- [ ] A dependência é adicionada no módulo correto (framework em `http_api`, nunca em `domain`)?
- [ ] `domain` continua com zero dependências de framework após a mudança?
- [ ] A dependência é realmente necessária ou é possível resolver com código puro?

---

## Sinais de Alerta — Arquitetura Deixando de Gritar

Fique atento quando:

| Sinal | Problema | Ação |
|---|---|---|
| Entidade de domínio importa classe de framework | Inversão de dependência | Mova para adaptador |
| Caso de uso recebe `ApplicationCall` ou `HttpRequest` | Acoplamento a entrega | Crie um Command/Query object |
| Teste de domínio precisa de `@SpringBootTest` ou similar | Isolamento quebrado | Extraia a lógica para camada pura |
| Pacote chamado `controllers/`, `services/`, `repositories/` (genérico) | Grita padrão técnico | Renomeie pelo domínio |
| Lógica de negócio dentro de uma rota HTTP | Responsabilidade errada | Mova para UseCase |
| Framework referenciado no README antes do domínio | Comunicação invertida | Reescreva — domínio primeiro |

---

## Princípios Resumidos

1. **A estrutura de pastas é a primeira documentação** — ela deve contar a história do negócio
2. **Frameworks são plugins** — servem ao domínio, não o definem
3. **A web é um detalhe** — casos de uso existem independente do protocolo de entrega
4. **Domínio puro = testável de forma isolada** — se não consegue testar sem infraestrutura, o design está errado
5. **Nomes de negócio prevalecem** — `CreateBoardUseCase` ganha de `BoardController` sempre
6. **Decida tarde, mude facilmente** — boa arquitetura preserva a liberdade de escolha técnica