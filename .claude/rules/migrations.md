---
paths:
  - "**/db/migration/*.sql"
  - "**/migration/**"
  - "**/internal/serializers/**"
---

# Database Migrations — Flyway + PostgreSQL

## Conventions

- **Naming**: `V{N}__{description}.sql` (double underscore). Monotonically increasing `N`. Never reuse a number.
- **Immutability**: never edit an existing migration — Flyway will fail on checksum mismatch. Create a new migration instead.
- **Rollback**: Flyway Community does not support rollback scripts. Design migrations to be forward-only (prefer `ADD COLUMN` over `DROP COLUMN`; prefer nullable additions).
- **Idempotency**: use `IF NOT EXISTS` / `IF EXISTS` guards where possible.

## Current Schema

| Migration | Purpose |
|---|---|
| `V1__initial_schema.sql` | All tables consolidated: `boards`, `steps`, `cards`, `organizations`, `simulations`, `simulation_states`, `daily_snapshots` (post-unification PRs #87–#91) |
| `V2__jsonb_simulation_blobs.sql` | Migrate `simulation_states.state_json` and `daily_snapshots.snapshot_json` from TEXT to JSONB (ADR-0013) |

## Rules

- New migration file = new PR — never bundle schema changes with application code in the same PR without explicit justification.
- Integration tests automatically apply all migrations via `DatabaseFactory` + Embedded PostgreSQL.
- JSON columns (`simulation_states.state_json`, `daily_snapshots.snapshot_json`) use `JSONB` (migrated in V2 — ADR-0013).
- Next available migration number: **V4**. (`V3__organizations_name_not_blank.sql` — CHECK anti-branco em `organizations.name`, GAP-DH #357.)

## Refinar o tipo de um campo já persistido — decode tolerante a legado

Os blobs JSON (`state_json`, `snapshot_json`) são **registro imutável de leitura**: contêm dados gravados por
releases anteriores, quando a borda podia aceitar valores que hoje seriam inválidos. Ao **refinar o tipo de um
campo serializado** — trocar um `String` cru por um value class / smart constructor com invariante (ex.:
`NonBlankTitle`, GAP-DH #355) — o `require`/`init` do novo tipo passa a rodar **também sobre o histórico**.

- **Ponto cego:** a borda de *entrada* nova (DTO/domínio) guarda o valor; o **decode** (`toDomain`/`surrogate`)
  não. Um único valor legado inválido (ex.: um `ADD_ITEM` de título em branco gravado antes do guard) faz o
  decode **lançar** → `Either.catch` → `PersistenceError` → a **linha/agregado inteiro** fica não-carregável
  (`findById`/`findAll` viram 500), não só o campo.
- **Regra:** o decode deve ser **tolerante a legado**. Se a exigência é manter o agregado **carregável**
  (o caso normal para histórico), **coaja** o valor inválido a um **sentinel** (ex.: `decodeTitle()` →
  `"(untitled)"`) — nunca deixe o `require` do value class lançar cru. (Trocar o `require` por um **erro tipado**
  só muda a *forma* da falha: o agregado **continua não-carregável**; use isso apenas se falhar o load daquele
  registro for aceitável, o que raramente é para dados históricos.) Cubra com um teste dedicado ("legacy blank …
  decodes to a sentinel instead of crashing the load").
- **Alternativa (quando cabe uma migração):** um data-fix Flyway forward-only que sanitize o histórico
  (`UPDATE … SET … WHERE …` sobre o JSONB) — mas só depois de **auditar** que tais registros existem; se o
  campo *sempre* teve guard (ex.: `Card.init` nunca deixou blank), não há legado a tolerar e nada a migrar.

### Coagir a sentinel NÃO basta — dois modos de falha do próprio decode tolerante

A prescrição acima ("coaja o valor inválido a um sentinel") é **por campo**, e é aí que ela falha: o decode não
entrega campos, entrega um **agregado construído** que alguém vai **regravar**. Os dois furos abaixo foram P1 do
Codex no **#383 (GAP-DV)** — o autor tinha aplicado a regra à risca e produziu, num caso, algo **pior que o 500
que a tolerância evita**.

- **Nem todo campo degradado é equivalente: se o campo é a CHAVE DE ESCRITA, degradá-lo troca um erro de
  leitura por uma corrupção silenciosa.** O decode degradava o `id` de topo do blob para `"(unknown)"`, mas
  `JdbcSimulationRepository.save` faz **upsert por `simulation.id`** — então a simulação carregada por
  `findById(idReal)` seria, no `runDay` seguinte, gravada numa **linha nova** sob o sentinel, deixando a
  original órfã e os snapshots seguindo o id errado. O 500 é barulhento e reversível; isto é silencioso e
  permanente. **Pergunte de cada campo que você degrada: alguém escreve POR ele?** (chave primária, chave de
  upsert, chave de correlação/tenant).
  **A saída não é deixar de tolerar — é reconhecer que aquele campo tem fonte autoritativa FORA do blob**: a
  **linha relacional que a query já usou** para chegar até ali. O serializer continua sem lançar (é o ponto do
  gap) e a autoridade é reposta na camada que a tem.
  ⚠️ **Enumere TODOS os campos assim, não só o primeiro que aparecer.** A primeira redação desta regra dizia
  "para todos os outros campos o blob é a única fonte" e reconciliava só o `simulation.id` — mas
  `organization.id` também vem da mesma linha, também é regravado pelo `save`, é coluna com **FK**
  (`REFERENCES organizations(id)`) **e** é a chave de **tenancy** (`ensure(simulation.organization.id ==
  callerOrganizationId) { Forbidden }` em 5 use cases). Degradá-la dava FK violation no save e **403 para o
  dono legítimo** — o registro "tolerado" ilegível na prática. Codex P2 no #384, sobre a emenda escrita a
  partir do P1 do #383: corrigir *um* caso de uma classe não fecha a classe.
  ```kotlin
  // JdbcSimulationRepository.rowToSimulation
  val decoded = SimulationSerializer.decode(stateJson)
  decoded.copy(
      id = SimulationId(row[SimulationsTable.id]),
      organization = decoded.organization.copy(id = row[SimulationsTable.organizationId]),
  )
  ```
  Fixe em teste a **regravação**, não só a carga: salvar o agregado carregado e assertar que continua havendo
  **uma única linha** é a prova direta do fork (`SimulationIdentityReconciliationTest`). Para o campo com FK a
  prova é ainda mais direta — sem a reconciliação o re-save falha com **violação de constraint**, não com
  asserção.

- **Um caminho de REPARO novo tem de satisfazer os invariantes CROSS-FIELD do agregado que vai construir.**
  Raciocinar campo-a-campo cobre `require`s de um campo só; não cobre `init`s que **relacionam** campos. Aqui,
  `Step.init` exige `workers.all { hasAbility(requiredAbility) }`: uma reparação que completava worker sem
  abilities com o fallback **global** (`DEVELOPER`) era **cega ao contexto** — num step que exigisse `TESTER`,
  `DEPLOYER` ou `PRODUCT_MANAGER` o `Step.init` continuava lançando, e o agregado seguia não-carregável
  **exatamente no caso que a tolerância existe para cobrir**. Correção: decodificar a ability do step
  **primeiro** e repassá-la ao decode de cada worker (`WorkerSurrogate.toDomain(requiredAbility)`).
  Três condições que a reparação contextual tem de respeitar, e que valem para qualquer uma:
  1. **A ordem entre reparos importa** — a ability herdada do step entra **antes** da regra `TESTER → DEPLOYER`
     (`Worker.init`), senão o reparo de um invariante quebra o outro. Cubra o encadeamento em teste.
  2. **Só acrescenta, nunca remove** — podar o worker incompatível também faria `Step.init` passar, mas é
     **descarte**, proibido pela seção seguinte: o próximo save o tornaria permanente.
  3. **Não é só o caso "vazio"** — repare todo worker do step, não apenas o de lista vazia, senão o `init`
     ainda pode lançar por outra instância. Em blob consistente é no-op.

  > **Por que o *fallback consistente* funciona onde o reparo cego falhou.** Coagir só um lado de um invariante
  > cross-field o quebra; coagir os **dois lados ao mesmo valor** (o `requiredAbility` do step **e** as
  > `abilities` dos workers) é um **renomeio consistente** e o preserva — que é o cenário real (release novo
  > cria uma ability, rollback, ambos os lados carregam o mesmo valor ilegível e degradam juntos). Corolário
  > para escolher a constante: **o fallback não pode participar de nenhum outro cross-field** — `ABILITY_FALLBACK`
  > é `DEVELOPER` e não `TESTER` justamente porque `Worker.init` exige `!hasTester || hasDeployer`.

- **Escolha a sentinel pelo COMPORTAMENTO que ela terá no motor, não pela sua neutralidade sintática.** Um
  estado degradado não fica parado: ele é lido pelo engine no próximo dia. `CARD_STATE_FALLBACK` é `BLOCKED`
  porque, depois do GAP-DU, BLOCKED é genuinamente **inerte** (`autoAdvance` só toca `TODO`, `applyMove`
  recusa BLOCKED, a execução só toca `IN_PROGRESS`) — é quarentena visível em `blockedCount`, e sai dela por
  um `UnblockItem` explícito. `TODO` parece o "neutro" mas **ressuscitaria o trabalho em silêncio**: o card
  voltaria à fila e seria iniciado no dia seguinte por causa de um dado ilegível.

> **Cobertura em 3 casas — e o gatilho mais fraco.** Esta regra é o *ângulo dado-persistido* (evolução do
> histórico + a alternativa data-fix Flyway), mas o bug **nasce ao editar um value class / serializer `.kt`**,
> não uma migração SQL — então mesmo com o glob `**/internal/serializers/**` acima, `migrations.md` é o
> gatilho **mais fraco** dos três para o modo de falha real. Quem cobre o **autor** de `.kt` é o `/fp-oo-kotlin`
> (callout "value class em campo serializado"); quem cobre o **revisor** é o `pr-harness` §2.5 (checar o decode
> de dados legados ao refinar o tipo de um campo persistido). A cobertura conjunta é adequada — só não conte com
> esta regra sozinha para disparar no ponto onde o defeito é introduzido.

## Variante (tag) desconhecida no blob — preserve, nunca descarte

A seção acima trata de um **valor inválido** em dado **antigo** (compatibilidade *para trás*). Este é o eixo
oposto e igualmente real: uma **variante que este release não conhece**, gravada por um release **mais novo**
(compatibilidade *para frente*).

- **O gatilho realista não é corrupção manual — é rollback.** Um release novo passa a gravar uma 5ª tag
  (`Decision`/`MovementType`), você reverte, e os pods antigos deixam de ler o histórico **que eles mesmos
  acabaram de gravar**. Sob rolling update/HPA as duas versões convivem por minutos, então o blob misto é o
  caso normal, não a exceção. Decode que faz `error("Unknown …")` ou `enum.valueOf(raw)` transforma isso em
  `PersistenceError` → 500 em `findById` **e na página inteira** de `findAll(organizationId, page, size)`.
- **Regra: preserve o registro, não pule.** Mapeie o irreconhecível para uma variante portadora
  (`Decision.Unknown(type, payload)` / `MovementType.Unknown(raw)`).
  **Descartar é lossy, não é degradação de leitura:** `JdbcSimulationRepository` re-serializa o **agregado
  inteiro** a cada save (`it[stateJson] = SimulationSerializer.encode(simulation)`), então um `skip`/`filter`
  no decode vira **deleção permanente** no próximo `runDay` — o dado some do banco por um caminho que ninguém
  leu como escrita. Vale para qualquer blob read-modify-write, não só este.
- **A variante portadora só preserva de fato se carregar o JSON CRU — o tipo do campo é o limite real.**
  Não afirme "volta idêntica ao wire" a partir de `type` + um mapa tipado: a garantia vale só para o formato
  que o mapa já aceita. Medido no `DecisionSurrogate` atual (`payload: Map<String, String>`,
  `Json { ignoreUnknownKeys = true }`):

  | Payload gravado por um release mais novo | Resultado hoje |
  |---|---|
  | `{"count":3}` · `{"flag":true}` · `{"tags":["a"]}` · objeto aninhado | **`JsonDecodingException`** — lança **antes** de `Decision.Unknown` existir ⇒ o 500 que a tolerância deveria evitar |
  | campo extra no **topo** da decisão (fora de `payload`) | decodifica, mas é **descartado em silêncio** por `ignoreUnknownKeys` ⇒ some no próximo save |

  Ou seja: preservar de verdade exige reter o **`JsonElement`/`JsonObject` cru**, não um `Map<String,String>`.
  Se o carrier tipado for suficiente para o caso conhecido, **diga o escopo** ("payload plano de strings") em
  vez de prometer identidade de wire — e cubra com teste os dois casos da tabela, que são justamente os que
  passam despercebidos por serem *futuros*. (Codex P2 no #369; medido, não inferido. O carrier de `Decision`
  ainda é `Map<String,String>` — fechar isso é o **GAP-EN**.)
- **Tolerância custa a falha barulhenta — reponha a garantia com teste.** O `else` que antes explodia era o
  que pegava um decoder desatualizado; com um catch-all legítimo, **uma variante nova passa a decodificar
  silenciosamente como `Unknown`**. Como a tag é `String`, o compilador não consegue exaustividade no decode:
  fixe um **round-trip exhaustiveness test** cujo `when` é exaustivo sobre o sealed (`DecisionRoundTripExhaustivenessTest`),
  de modo que adicionar uma variante **pare de compilar** até alguém passá-la pelo `toSurrogate().toDomain()`.
- **Mantenha o wire byte-idêntico ao converter enum → sum type.** `data object` com os mesmos nomes + uma `tag`
  que reproduz o que `enum.name` emitia mantém os blobs e o JSON de resposta inalterados — a conversão deixa de
  ser uma migração de dados. (Cuidado de cobertura ao fazer isso: ver o bullet do `when` em `kotlin-quality.md`.)
- **Assimetria deliberada — tolerância é só do lado persistido.** Dado já gravado degrada para manter o agregado
  carregável; **input de cliente continua fail-closed** com erro tipado (400). Não propague a tolerância deste
  arquivo para a borda HTTP/DTO: lá, tag desconhecida é rejeição, e isso deve estar fixado em teste
  (`DecisionRequestExhaustivenessTest`).

(GAP-DS/#366 — mesma classe do #355, relocada de *valor de campo* para *tag de variante*.)