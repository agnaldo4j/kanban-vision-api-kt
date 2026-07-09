---
name: c4-model
description: >
  Gera e mantém atualizados os diagramas de arquitetura (contexto, container, componente,
  sequência, classe, ERD) nas páginas do wiki (Architecture*, Operations, Observability, GraalVM).
  Use sempre que uma nova feature, rota, caso de uso, entidade ou módulo for adicionado.
  Referência: https://c4model.com
argument-hint: "[feature, route or component changed]"
allowed-tools: Read, Grep, Glob, Edit
---

# C4 Model — Diagramas de Arquitetura

Você é um arquiteto de software especialista em C4 Model (Simon Brown).
Quando esta skill for invocada, você deve **explorar o estado atual do código** e
**atualizar os diagramas nas páginas do wiki** com diagramas Mermaid precisos e fiéis ao código.

> **Localização (atual):** os diagramas vivem no **wiki** (não mais no README — o README apenas
> linka). O wiki é um clone git separado em `../kanban-vision-api-kt.wiki` (remote `.wiki.git`,
> mantido manualmente): edite os arquivos `Architecture*.md`, `Operations.md`, `Observability.md`,
> `GraalVM.md`, e o hub `Diagrams.md`; o mantenedor faz o push. A página `Diagrams` indexa tudo por
> senioridade.
>
> **House style (obrigatório):** Mermaid **`flowchart`/`graph`** para estrutura, `sequenceDiagram`
> para fluxos, `classDiagram` para o domínio, `erDiagram` para o schema, `stateDiagram-v2` para
> máquinas de estado. **Não** usar a sintaxe nativa `C4Context/C4Container/C4Component` (o suporte
> no GitHub é experimental); os templates C4 mais abaixo ficam só como referência conceitual dos
> níveis. Valide cada diagrama (Mermaid Live ou o MCP Mermaid) antes de commitar.

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

### Passo 2 — Atualize a página do wiki correspondente

Edite a página do wiki mais próxima da mudança (no clone `../kanban-vision-api-kt.wiki`):
`Architecture.md` (contexto/container/módulos), `Architecture-Domain.md` (classe/estado),
`Architecture-Usecases.md` / `Architecture-SQL-Persistence.md` (componente/sequência/ERD),
`Operations.md` (CI/CD, k8s), `Observability.md`, `GraalVM.md`. Mantenha o hub `Diagrams.md`
apontando para o diagrama. Commit local com mensagem `docs(wiki): …`; o mantenedor faz o push.

### Passo 3 — Valide visualmente

Após editar a página do wiki, confirme que os diagramas Mermaid são sintaticamente válidos
(valide no Mermaid Live ou pelo MCP Mermaid) e refletem com precisão:
- Todos os módulos/containers existentes
- Todos os componentes (rotas, use cases, repositórios) existentes
- Fluxos de sequência dos happy paths principais
- Classes do domínio com relações corretas

---

## Onde cada diagrama vive no wiki

Mapa canônico (todos em Mermaid **flowchart** house-style, exceto onde indicado):

| Diagrama | Página do wiki |
|---|---|
| Contexto do Sistema (nível 1) + Containers (nível 2) + mapa de módulos | `Architecture.md` |
| Fluxo de request (sequenceDiagram) | `Architecture.md` |
| Componentes `http_api` | `Architecture-HTTP-API.md` |
| Classe do domínio (classDiagram) + máquina de estado do Card (stateDiagram-v2) | `Architecture-Domain.md` |
| Sequência CQS (RunDay) + componentes usecases | `Architecture-Usecases.md` |
| Componentes de persistência + **ERD** (erDiagram) | `Architecture-SQL-Persistence.md` |
| Fluxo de Domain Events → métricas + topologia de observabilidade | `Observability.md` |
| Pipeline CI/CD + topologia k8s | `Operations.md` |
| Pipeline de build GraalVM | `GraalVM.md` |
| Índice por senioridade (Júnior/Pleno/Sênior) | `Diagrams.md` (hub) |

> **Board Management não está wired** (pós-GAP-BF): não há sequência de Board Management — os fluxos
> reais são de Simulation (RunDay). Não desenhe componentes/repos que não existem no código.

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
| Sintaxe C4 nativa (`C4Context`…) | Mermaid `flowchart` house-style (render confiável no GitHub) |
| Diagrama desatualizado após nova feature | Atualizar a página do wiki junto com cada PR |

---

## Quando atualizar os diagramas

Atualizar a seção sempre que:
- Nova rota HTTP for adicionada (`*Routes.kt`)
- Novo caso de uso for criado em `usecases/`
- Nova entidade de domínio for criada em `domain/model/`
- Novo módulo Gradle for adicionado
- Novo sistema externo (fila, cache, serviço) for integrado
- Um container/componente existente for removido ou renomeado

**Regra:** o PR que adiciona a feature deve incluir a atualização da página do wiki correspondente
(via o clone `../kanban-vision-api-kt.wiki`) com o diagrama atualizado. Sem diagrama atualizado, o
PR está incompleto (o mantenedor faz o push do wiki).

---

## Referências

- C4 Model: https://c4model.com
- Mermaid C4 Diagrams: https://mermaid.js.org/syntax/c4.html
- Mermaid Sequence: https://mermaid.js.org/syntax/sequenceDiagram.html
- Mermaid Class: https://mermaid.js.org/syntax/classDiagram.html
- Simon Brown: https://simonbrown.je