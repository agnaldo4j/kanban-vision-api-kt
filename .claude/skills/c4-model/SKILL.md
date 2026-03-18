---
name: c4-model
description: >
  Gera e mantém atualizados os diagramas C4 Model (Context, Container, Component),
  diagramas de sequência e diagramas de classe do projeto no README.md.
  Use sempre que uma nova feature, rota, caso de uso, entidade ou módulo for adicionado.
  Referência: https://c4model.com
argument-hint: "[feature, route or component changed]"
allowed-tools: Read, Grep, Glob, Edit
---

# C4 Model — Diagramas de Arquitetura

Você é um arquiteto de software especialista em C4 Model (Simon Brown).
Quando esta skill for invocada, você deve **explorar o estado atual do código**
e **atualizar a seção `## Arquitetura — Diagramas C4` do `README.md`** com
diagramas Mermaid precisos e fiéis ao código real.

---

## Referência C4 Model

- Site oficial: https://c4model.com
- Os 4 níveis de abstração:

| Nível | Nome | Audiência | Pergunta respondida |
|---|---|---|---|
| 1 | System Context | Qualquer pessoa | O que o sistema faz e quem usa? |
| 2 | Container | Desenvolvedores e arquitetos | Quais processos/tecnologias compõem o sistema? |
| 3 | Component | Desenvolvedores | Quais são os blocos internos de um container? |
| 4 | Code | Raramente necessário | Como uma estrutura específica é implementada? |

---

## O que fazer ao invocar esta skill

### Passo 1 — Explore o estado atual do código

Leia os seguintes arquivos para entender o que mudou:

```
# Módulos e dependências
build.gradle.kts (root e cada módulo)
settings.gradle.kts

# Domínio
domain/src/main/kotlin/com/kanbanvision/domain/model/
domain/src/main/kotlin/com/kanbanvision/domain/simulation/

# Casos de uso e ports
usecases/src/main/kotlin/com/kanbanvision/usecases/
usecases/src/main/kotlin/com/kanbanvision/usecases/repositories/

# Rotas HTTP
http_api/src/main/kotlin/com/kanbanvision/httpapi/routes/
http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/
http_api/src/main/kotlin/com/kanbanvision/httpapi/di/

# Persistência
sql_persistence/src/main/kotlin/com/kanbanvision/persistence/repositories/
```

### Passo 2 — Atualize a seção no README

Localize `## Arquitetura — Diagramas C4` no `README.md` e substitua todo o conteúdo
com os diagramas atualizados. **Nunca remova a seção — apenas atualize seu conteúdo.**

### Passo 3 — Valide visualmente

Após editar o README, confirme que os diagramas Mermaid são sintaticamente válidos
e refletem com precisão:
- Todos os módulos/containers existentes
- Todos os componentes (rotas, use cases, repositórios) existentes
- Fluxos de sequência dos happy paths principais
- Classes do domínio com relações corretas

---

## Estrutura da seção no README

A seção deve sempre conter, nesta ordem, dentro de `## Arquitetura — Diagramas C4`:

```markdown
## Arquitetura — Diagramas C4

### Nível 1 — Contexto do Sistema
[diagrama C4Context Mermaid]

### Nível 2 — Containers
[diagrama C4Container Mermaid]

### Nível 3 — Componentes: http_api
[diagrama C4Component Mermaid]

### Nível 3 — Componentes: domain
[diagrama C4Component Mermaid]

### Sequência — Board Management
[diagrama sequenceDiagram Mermaid]

### Sequência — Simulation Engine
[diagrama sequenceDiagram Mermaid]

### Classes — Domínio
[diagrama classDiagram Mermaid]
```

---

## Guia de sintaxe Mermaid para C4

### Nível 1 — C4Context

```mermaid
C4Context
  title Nível 1 — Contexto do Sistema

  Person(alias, "Nome", "Descrição")
  Person_Ext(alias, "Nome externo", "Descrição")
  System(alias, "Nome", "Descrição")
  System_Ext(alias, "Nome externo", "Descrição")
  SystemDb(alias, "Banco", "Descrição")
  SystemDb_Ext(alias, "Banco externo", "Descrição")

  Rel(de, para, "rótulo")
  Rel(de, para, "rótulo", "tecnologia")
```

### Nível 2 — C4Container

```mermaid
C4Container
  title Nível 2 — Containers

  Person(user, "Usuário")

  System_Boundary(sys, "Nome do Sistema") {
    Container(alias, "Nome", "Tecnologia", "Responsabilidade")
    ContainerDb(alias, "Banco", "Tecnologia", "Responsabilidade")
  }

  Rel(de, para, "rótulo", "tecnologia")
```

### Nível 3 — C4Component

```mermaid
C4Component
  title Nível 3 — Componentes: NomeContainer

  Container_Boundary(container, "NomeContainer") {
    Component(alias, "Nome", "Tipo", "Responsabilidade")
  }

  Container_Ext(dep, "Dependência externa")
  Rel(de, para, "rótulo")
```

---

## Regras de qualidade dos diagramas

### C4 Context (Nível 1)
- [ ] Mostra apenas: sistema, atores externos, sistemas externos
- [ ] Sem detalhes de tecnologia ou implementação
- [ ] Relações têm rótulo e tecnologia de comunicação

### C4 Container (Nível 2)
- [ ] Um container = um processo deployável ou store de dados
- [ ] Tecnologia visível em cada container
- [ ] Dependência entre containers reflete o fluxo de dependência do `build.gradle.kts`
- [ ] Banco de dados representado como `ContainerDb` ou `SystemDb`

### C4 Component (Nível 3)
- [ ] Um component = um conjunto de código com responsabilidade bem definida
- [ ] Componentes refletem os arquivos/classes reais no módulo
- [ ] Relações refletem chamadas/dependências reais no código
- [ ] Não inventar components que não existem no código

### Diagramas de Sequência
- [ ] Mostra o happy path do fluxo principal
- [ ] Participantes correspondem a classes/objetos reais
- [ ] Mensagens correspondem a chamadas de método reais
- [ ] Inclui retorno de dados relevantes

### Diagramas de Classe
- [ ] Apenas classes do domínio (módulo `domain`)
- [ ] Atributos com tipo correto (como no código real)
- [ ] Relações: composição (`*--`), agregação (`o--`), associação (`-->`)
- [ ] Não incluir classes de infraestrutura (Ktor, JDBC, Koin)

---

## Antipadrões a evitar

| Antipadrão | Correto |
|---|---|
| Diagramas que não refletem o código | Sempre ler o código antes de desenhar |
| Nível 3 com detalhes de código (Nível 4) | Parar no componente, não detalhar implementação |
| Nível 2 com detalhes de componentes | Separar em diagramas distintos por nível |
| Relações sem rótulo | Sempre descrever a natureza da relação |
| Diagrama em imagem binária | Sempre Mermaid (renderiza no GitHub) |
| Seção separada por tipo de diagrama | Todos os diagramas na mesma seção `## Arquitetura — Diagramas C4` |
| Diagrama desatualizado após nova feature | Atualizar o README junto com cada PR |

---

## Quando atualizar os diagramas

Atualizar a seção sempre que:
- Nova rota HTTP for adicionada (`*Routes.kt`)
- Novo caso de uso for criado em `usecases/`
- Nova entidade de domínio for criada em `domain/model/`
- Novo módulo Gradle for adicionado
- Novo sistema externo (fila, cache, serviço) for integrado
- Um container/componente existente for removido ou renomeado

**Regra:** o PR que adiciona a feature deve incluir a atualização do README com
o diagrama C4 correspondente. Sem diagrama atualizado, o PR está incompleto.

---

## Referências

- C4 Model: https://c4model.com
- Mermaid C4 Diagrams: https://mermaid.js.org/syntax/c4.html
- Mermaid Sequence: https://mermaid.js.org/syntax/sequenceDiagram.html
- Mermaid Class: https://mermaid.js.org/syntax/classDiagram.html
- Simon Brown: https://simonbrown.je