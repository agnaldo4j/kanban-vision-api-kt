---
name: xp-kanban
description: >
  Aplique Extreme Programming (XP) e o Método Kanban como motores complementares de
  evolução de engenharia de software. Use este skill ao planejar iterações, definir
  práticas técnicas (TDD, refactoring, CI, pair programming), visualizar fluxo de trabalho,
  limitar WIP, gerenciar políticas explícitas e conduzir mudanças evolutivas e humanas
  no time e no produto. Complementa evolutionary-change, ddd e definition-of-done.
---

# Extreme Programming + Método Kanban
## Motores Complementares de Evolução em Engenharia de Software

> *"XP is the dominant agile method in the late 90s and early 00s. I particularly like its
> combination of technical and management practices."*
> — Martin Fowler, *ExtremeProgramming*, martinfowler.com

> *"The Kanban Method is both a codified management approach for professional services
> and an evolutionary approach to improvement intended to deliver institutionalized change."*
> — David J. Anderson, *Kanban: Successful Evolutionary Change for Your Technology Business*

---

## 1. Por que XP e Kanban juntos?

XP e Kanban são frequentemente vistos como alternativos. Na prática, são **complementares**:

| Dimensão | XP | Kanban |
|---|---|---|
| **Foco** | Práticas técnicas de excelência | Gestão do fluxo de trabalho |
| **Cadência** | Iterações curtas (1–2 semanas) | Fluxo contínuo, sem iterações obrigatórias |
| **Driver de mudança** | Práticas prescritas adotadas de uma vez | Mudança evolutiva a partir do estado atual |
| **Equipe** | Prescreve papéis e cerimônias | Respeita papéis existentes, adiciona visibilidade |
| **Qualidade** | TDD, refactoring, CI como disciplinas centrais | Políticas explícitas + feedback loops |
| **Planejamento** | User stories + velocity | Deferred commitment + lead time |

**A síntese**: XP fornece as **práticas técnicas** que garantem que o software seja bem feito;
Kanban fornece o **sistema de gestão** que garante que o trabalho certo seja feito no momento
certo, sem sobrecarregar o time.

> Kent Beck tomou práticas conhecidas e as levou ao extremo (*extreme*): se code review é bom,
> faça o tempo todo (pair programming); se testes são bons, escreva-os antes (TDD); se
> integração é boa, integre continuamente (CI). O mesmo princípio se aplica ao Kanban:
> se visibilidade é boa, visualize tudo; se menos WIP é melhor, limite-o explicitamente.

---

## 2. Extreme Programming — Práticas Fundamentais

### 2.1 As Quatro Variáveis

XP reconhece quatro variáveis interdependentes em qualquer projeto de software:

```
        Custo ←──────→ Tempo
           ↕              ↕
       Qualidade ←──→ Escopo
```

**Regra de ouro do XP**: Qualidade **não** é negociável. As outras três variáveis são
ajustáveis pelo cliente ou pelo negócio. Nunca sacrifique qualidade para ganhar tempo ou
reduzir custo — o custo a longo prazo sempre supera o ganho de curto prazo.

### 2.2 As Práticas do XP

O XP organiza suas práticas em dois grupos: **fine-scale feedback** (granularidade fina)
e **continuous process** (processo contínuo).

#### Fine-Scale Feedback

| Prática | Definição | Aplicação neste projeto |
|---|---|---|
| **Test-Driven Development (TDD)** | Escreva o teste antes do código. Red → Green → Refactor. | JUnit 5 + MockK. Todo use case começa por um teste falhando. |
| **Planning Game** | Negocie escopo e velocidade a cada iteração. | ADR-first: gap planejado, estimado e aprovado antes da execução. |
| **Whole Team** | Cliente, dev e QA no mesmo time. | Product owner define o gap; dev e LLM executam; CI valida. |
| **Pair Programming** | Dois desenvolvedores em um código ao mesmo tempo. | Sessões LLM + developer = par moderno. |

#### Continuous Process

| Prática | Definição | Aplicação neste projeto |
|---|---|---|
| **Continuous Integration (CI)** | Integre e valide várias vezes ao dia. | GitHub Actions em todo PR: `./gradlew testAll`. |
| **Refactoring** | Melhore o design sem alterar o comportamento externo. | Skill `/refactoring` + Detekt + cobertura JaCoCo ≥ 95%. |
| **Small Releases** | Entregue valor em incrementos pequenos e frequentes. | Um gap por PR; branch curta; merge rápido. |
| **Collective Code Ownership** | Qualquer pessoa pode melhorar qualquer código. | Arquitetura hexagonal com módulos claros facilita isso. |
| **Coding Standards** | Todo o time escreve no mesmo estilo. | KtLint + convention plugin centralizados em `buildSrc/`. |
| **Sustainable Pace** | Trabalhe em ritmo que possa ser mantido indefinidamente. | Protocolo 1-gap-por-sessão evita esgotamento de contexto. |
| **Simple Design** | O design mais simples que funciona para os requisitos atuais. | YAGNI: não adicione abstrações para usos hipotéticos futuros. |
| **System Metaphor** | Uma visão compartilhada de como o sistema funciona. | Screaming Architecture: pacotes gritam o domínio (`domain/`, `usecases/`). |

### 2.3 O Coração do XP: TDD

```
┌─────────────────────────────────────────────────────┐
│                    Ciclo TDD                         │
│                                                     │
│   1. RED    → Escreva um teste que falha            │
│   2. GREEN  → Escreva o mínimo de código para passar│
│   3. REFACTOR → Limpe o código sem quebrar o teste  │
│                                                     │
│   Repetir para cada comportamento novo.              │
└─────────────────────────────────────────────────────┘
```

**Anti-padrões a evitar:**
- Escrever o código antes do teste ("test after") — invalida o feedback do design
- Testar implementação, não comportamento — testes frágeis que quebram em refactoring
- Pular o refactor — acumula dívida técnica no ciclo seguinte
- Testar apenas o caminho feliz — given/when/then deve cobrir sucesso **e** erro

---

## 3. O Método Kanban

### 3.1 O que é um Sistema Kanban?

Um número fixo de kanbans (cartões ou tokens) equivalente à **capacidade acordada** do sistema
circula entre as etapas do fluxo. Um kanban representa uma unidade de trabalho.

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│ Backlog  │───▶│ Analysis │───▶│   Dev    │───▶│  Done    │
│          │    │  WIP: 2  │    │  WIP: 3  │    │          │
│ ○ ○ ○ ○  │    │  ● ●     │    │  ● ● ●   │    │  ✓ ✓ ✓   │
└──────────┘    └──────────┘    └──────────┘    └──────────┘
                     ↑                ↑
              Limite explícito  Limite explícito
              (nenhum novo item  (nenhum novo item
               quando cheio)     quando cheio)
```

É um **pull system**: novo trabalho só entra quando há capacidade. Não é possível sobrecarregar
o sistema se os limites de WIP forem definidos adequadamente.

### 3.2 As Três Agendas do Kanban

Organizações adotam Kanban por uma de três motivações:

| Agenda | Motivação | Foco |
|---|---|---|
| **Sustentabilidade** | Evitar sobrecarga de pessoas e do sistema | Interno: burnout, turnover, qualidade, engajamento |
| **Orientação a Serviço** | Satisfazer clientes insatisfeitos | Externo: fit-for-purpose, previsibilidade, confiança |
| **Sobrevivência** | Criar uma organização inovadora e adaptável | Institucional: mudança evolutiva, cultura, longevidade |

### 3.3 As Seis Práticas Gerais

#### Práticas Operacionais (executadas pelo time)

| Prática | O que significa na prática |
|---|---|
| **1. Visualizar** | Torne visível todo o trabalho e seu fluxo. Kanban board com colunas por estado. |
| **2. Limitar o WIP** | Defina limites por coluna. Nenhum novo item começa sem capacidade disponível. |
| **3. Gerenciar o Fluxo** | Monitore o fluxo diariamente. Identifique bloqueios e gargalos. |

#### Práticas de Gestão (orientam como líderes atuam)

| Prática | O que significa na prática |
|---|---|
| **4. Tornar Políticas Explícitas** | Regras visíveis, entendidas por todos. "Pronto" tem critério claro. |
| **5. Implementar Feedback** | Kanban Cadences: standups diários, revisão de serviço, retrospectivas. |
| **6. Melhorar Colaborativamente, Evoluir Experimentalmente** | Use modelos e método científico. Hipótese → experimento → evidência → decisão. |

> **Por que as práticas de gestão importam**: Implementações que aplicam apenas as três práticas
> operacionais tendem a ser rasas. A cultura de melhoria contínua (kaizen) emerge apenas quando
> as práticas de gestão também estão presentes.

### 3.4 Os Princípios do Kanban

#### Princípios de Fluxo
1. Negócios de bens intangíveis (serviços profissionais) podem ser geridos de forma similar a negócios de bens físicos.
2. Represente bens intangíveis com artefatos tangíveis: torne o trabalho invisível visível.
3. Controle e limite o "estoque" de bens intangíveis.

#### Princípios de Entrega de Serviço
1. Entenda e foque nas necessidades e expectativas dos seus clientes.
2. Gerencie o trabalho; deixe as pessoas se auto-organizarem em torno dele.
3. Evolua suas políticas de gestão para melhorar os resultados do cliente e do negócio.

#### Princípios de Gestão da Mudança
1. **Comece com o que você faz agora**: entenda os processos atuais como praticados; respeite papéis, responsabilidades e títulos existentes.
2. **Concorde em perseguir melhorias por mudança evolutiva.**
3. **Incentive atos de liderança em todos os níveis.**

### 3.5 O Modelo de Mudança Evolutiva

```
          ┌─────────┐
          │Stressor │ ← Limite de WIP cria tensão positiva
          └────┬────┘
               │
          ┌────▼────────┐
          │  Feedback   │ ← Cadências Kanban (standups, revisões, retrospectivas)
          │ Mechanism   │   Métricas: CFD, lead time histogram
          └────┬────────┘
               │
          ┌────▼──────────────┐
          │  Act of           │ ← "Let's do something about it!"
          │  Leadership       │   Pequena mudança de política → resultado → nova evidência
          └───────────────────┘
```

**"People do not resist change, they resist being changed."** — Peter Senge

Os quatro estados individuais em relação à mudança:

| Estado | Condição | Ação Kanban |
|---|---|---|
| **Estabilidade** | Feliz, sem motivação para mudar | Manter o sistema funcionando |
| **Inércia** | Infeliz, mas com medo de mudar | Criar tensão segura com limites de WIP |
| **Incremental** | Aceita mudanças normativas que não afetam identidade | Executar um gap por vez |
| **Dramático** | Mudança de identidade, papel, status | Evitar — requer ADR + aprovação prévia |

### 3.6 Os Valores do Kanban

Identificados por Mike Burrows (2013) e ampliados com o Kanban Maturity Model:

> Resultados seguem práticas.
> Práticas seguem cultura.
> Cultura segue valores.
> **Portanto, lidere pelos valores.**

Valores centrais: **Transparência**, **Equilíbrio**, **Colaboração**, **Foco no Cliente**,
**Fluxo**, **Liderança**, **Compreensão**, **Acordo**, **Respeito**.

---

## 4. XP + Kanban na Prática de Engenharia de Software

### 4.1 A Relação entre Card Walls (XP) e Kanban Boards

Historicamente, o Kanban board para software evoluiu **dos card walls do XP**. Os "extreme
programmers" escreviam user stories em cartões e os fixavam numa parede — a "card wall".
A diferença fundamental:

| Card Wall (XP) | Kanban Board |
|---|---|
| Estados simples: Backlog → In Progress → Done | Múltiplos estados refletindo o fluxo real de descoberta/entrega |
| Foco em iteração | Foco em fluxo contínuo |
| Sem limites de WIP explícitos | WIP limits por coluna são centrais |
| Visualização de stories | Visualização do workflow como sistema |

### 4.2 Quando Usar Cada Abordagem neste Projeto

| Situação | Use XP | Use Kanban |
|---|---|---|
| Escrevendo um novo use case | TDD (Red → Green → Refactor) | — |
| Priorizando o próximo gap | — | Deferred commitment: só inicie quando houver capacidade |
| Revisando qualidade do código | Collective ownership + refactoring | Políticas explícitas (Detekt, JaCoCo) |
| Integrando na branch | CI: `./gradlew testAll` antes do merge | Limite de WIP: max 1 PR aberto por vez |
| Diagnosticando lentidão no ciclo | Simple design + small releases | Gerenciar fluxo: identificar gargalo |
| Decidindo o que implementar primeiro | Planning Game (valor × esforço) | Kanban board com prioridade explícita |
| Adaptando práticas ao contexto | XP: adapte o extremo ao necessário | Kanban: respeite o que existe, evolua gradualmente |

### 4.3 Protocolo de Iteração XP-Kanban

```
PLANEJAR (Planning Game + Deferred Commitment)
  ├── Identifique o próximo gap/story de maior valor
  ├── Confirme que há capacidade (WIP limit não excedido)
  ├── Classifique: [N] normativo, [M] médio, [E] estrutural
  └── Se [E]: ADR aprovada antes de continuar

EXECUTAR (TDD + Small Releases)
  ├── RED: escreva o teste que especifica o comportamento
  ├── GREEN: implemente o mínimo para passar
  ├── REFACTOR: limpe o código (Detekt + KtLint)
  └── CI: ./gradlew testAll verde a cada commit

ENTREGAR (Continuous Integration + Kanban Pull)
  ├── Abra PR com escopo mínimo (≤ 400 linhas)
  ├── CI passa → revisão → merge
  └── Atualize o board: mova o item para Done

APRENDER (Feedback + Evolve Experimentally)
  ├── O que o lead time desta entrega nos diz?
  ├── Onde o fluxo travou? (gargalo, bloqueio, tamanho de lote)
  └── Uma melhoria de política para a próxima iteração
```

---

## 5. Aplicação ao Ciclo de Gaps deste Projeto

O ciclo de execução de gaps do ADR-0004 **é uma implementação concreta** dos princípios
XP + Kanban:

| Conceito | XP | Kanban | Como aparece nos gaps |
|---|---|---|---|
| Small releases | Uma story por iteração | Um gap por sessão LLM | `feat/gap-X-slug` — um PR por gap |
| WIP limit | Máximo de tasks em progresso | WIP limit explícito por coluna | 1 gap ativo por vez |
| Deferred commitment | Não planeje longe demais | Last responsible moment | Gaps P3 só começam quando P2 está concluído |
| Continuous integration | Build verde antes do merge | Política explícita de qualidade | `./gradlew testAll` obrigatório |
| Refactoring | Red → Green → Refactor | Kaizen: melhoria contínua | Skill `/refactoring` + Detekt zero violations |
| Collective ownership | Qualquer dev pode tocar o código | Políticas explícitas e compartilhadas | Convention plugin em `buildSrc/` — regras visíveis |
| Simple design | YAGNI + 4 regras do design simples | Evite over-engineering | ADR requerida antes de mudanças estruturais |
| Planning game | Prioridade por valor × esforço | Kanban board de prioridade | Tabela de prioridades no ADR-0004 |

---

## 6. Board Protocol — GitHub Project #6

O board https://github.com/users/agnaldo4j/projects/6 é a **fonte de verdade** sobre o que
está sendo feito agora. Este protocolo define como Claude interage com ele.

### 6.1 Início de Sessão — Pull do Topo do Todo

```bash
# 1. Verificar estado atual do board
gh project item-list 6 --owner agnaldo4j --format json | \
  jq '.items[] | {title: .title, status: .status}'

# 2. Se há item em Doing → continuar aquele item (não iniciar novo)
# 3. Se Doing vazio → puxar o PRIMEIRO item do topo de Todo

# 4. Mover o item de Todo → Doing
gh api graphql -f query='
mutation {
  updateProjectV2ItemFieldValue(input: {
    projectId: "PVT_kwHNWUfOAUhH_w"
    itemId: "<ITEM_ID>"
    fieldId: "PVTSSF_lAHNWUfOAUhH_84P7ZSQ"
    value: { singleSelectOptionId: "75426285" }  # Doing
  }) { projectV2Item { id } }
}'

# 5. Criar branch a partir de main atualizado
git checkout main && git pull origin main
git checkout -b feat/gap-X-slug
```

**Regra de pull**: sempre o item no **topo do Todo** — nunca escolha por conveniência.

### 6.2 Encerramento — Após Merge do PR

```bash
# 1. Atualizar main local
git checkout main && git pull origin main

# 2. Deletar branch local e remota
git branch -d feat/gap-X-slug
git push origin --delete feat/gap-X-slug  # se necessário

# 3. Mover card: Doing → Done
gh api graphql -f query='
mutation {
  updateProjectV2ItemFieldValue(input: {
    projectId: "PVT_kwHNWUfOAUhH_w"
    itemId: "<ITEM_ID>"
    fieldId: "PVTSSF_lAHNWUfOAUhH_84P7ZSQ"
    value: { singleSelectOptionId: "ca259842" }  # Done
  }) { projectV2Item { id } }
}'

# 4. Marcar gap [x] no ADR-0004
# 5. Atualizar memory/project_adr_progress.md
# 6. Encerrar a sessão
```

### 6.3 IDs de Referência — GitHub Project

| Recurso | ID |
|---|---|
| Project ID | `PVT_kwHNWUfOAUhH_w` |
| Status Field ID | `PVTSSF_lAHNWUfOAUhH_84P7ZSQ` |
| Status: Backlog | `8dfbb2d5` |
| Status: Todo | `0fab6fb9` |
| Status: Doing | `75426285` |
| Status: Done | `ca259842` |

### 6.4 WIP Limit — Regra de Ouro

> **WIP limit: 1**. Nunca mais de um item em Doing.
> Se Doing já tem um item → termine-o antes de puxar o próximo.
> "Pare de iniciar. Comece a terminar." — David J. Anderson

---

## 7. Sinais de Alerta

### No código (XP)
- Classe com > 200 linhas → SRP violado → extraia
- Método com > 10 de complexidade ciclomática → refatore
- Testes escritos depois do código → risco de design pobre
- CI vermelho por mais de 1h → bloqueio do fluxo inteiro

### No fluxo (Kanban)
- WIP limit excedido → parar de iniciar, começar a terminar
- Item parado > 2 dias → identificar bloqueio, criar política
- PR aberto > 48h sem revisão → escopo provavelmente grande demais
- Múltiplos gaps em progresso → esgotamento de contexto iminente

---

## 8. Referências

- Beck, Kent. *Extreme Programming Explained: Embrace Change* (White Book). Addison-Wesley, 1999.
- Anderson, David J. *Kanban: Successful Evolutionary Change for Your Technology Business* (Blue Book). Blue Hole Press, 2010.
- Fowler, Martin. *ExtremeProgramming*. https://martinfowler.com/bliki/ExtremeProgramming.html
- Fowler, Martin. *Refactoring: Improving the Design of Existing Code*. Addison-Wesley, 2018.
- Shore, James. *The Art of Agile Development*. O'Reilly, 2007.
- Burrows, Mike. *Kanban from the Inside*. Blue Hole Press, 2014.
- Ohno, Taiichi. *Toyota Production System: Beyond Large-Scale Production*. Productivity Press, 1988.
- Site XP: http://www.extremeprogramming.org
- Skill: [evolutionary-change](../evolutionary-change/SKILL.md)
- Skill: [definition-of-done](../definition-of-done/SKILL.md)
- Skill: [refactoring](../refactoring/SKILL.md)
- Skill: [testing-and-observability](../testing-and-observability/SKILL.md)
- ADR-0004: `adr/ADR-0004-avaliacao-qualidade-gaps-priorizados.md`