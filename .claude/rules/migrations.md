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
  `PersistenceError` → 500 em `findById` **e na página inteira** de `findAllByOrganization`.
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