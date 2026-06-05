# ADR-0016 — Upgrade Kotlin de 2.3.0 para 2.4.0

## Cabeçalho

| Campo     | Valor                          |
|-----------|--------------------------------|
| Status    | Aceita                         |
| Data      | 2026-06-05                     |
| Autores   | @agnaldo4j                     |
| Branch    | chore/kotlin-2.4-upgrade       |
| PR        | —                              |
| Supersede | —                              |

---

## Contexto e Motivação

O projeto usa Kotlin **2.3.0** desde o PR #118 (março 2026). Kotlin **2.4.0** foi
lançado em **3 de junho de 2026** como release de linguagem estável. O K2 compiler é
o padrão desde 2.0.0; este upgrade não muda o frontend do compilador.

Dois motivadores concretos tornam o upgrade oportuno agora:

1. **`kotlinx-serialization-json:1.8.1` não compila com Kotlin 2.4.0** — foi
   publicada contra Kotlin 2.1.20. Versão compatível com 2.4: `1.11.0`.
2. **Arrow-kt 2.2.3 inclui suporte explícito a Kotlin 2.4.0** — `arrow-core:2.0.1`
   acumula 4 minor bumps de drift com o compilador K2; `either {}` e `Raise` foram
   otimizados nas versões intermediárias.

Adicionalmente, a flag de compilador `-opt-in=kotlin.RequiresOptIn` (presente na
convention plugin) tornou-se deprecated em 2.2 e produz warning em 2.4 — deve ser
removida.

---

## Forças (Decision Drivers)

- Kotlin 2.4.0 estável e production-ready desde 03/06/2026
- Gradle 8.13 suportado (faixa 7.6.3–9.5.0)
- Java 21 `jvmToolchain` suportado
- Zero breaking changes do Kotlin 2.4 aplicáveis ao codebase atual
- `kotlinx-serialization-json` bloqueada em 1.8.1 sem este upgrade
- CI mantém `./gradlew testAll` como safety gate antes de merge

---

## Opções Consideradas

### Opção A — Atualizar para Kotlin 2.4.0 (com co-upgrades obrigatórios)

- Kotlin KGP + kotlin-test: `2.3.0` → `2.4.0`
- `kotlinx-serialization-json`: `1.8.1` → `1.11.0` (obrigatório)
- `arrow-core`: `2.0.1` → `2.2.3` (recomendado — suporte K2/2.4 explícito)
- Remover `-opt-in=kotlin.RequiresOptIn` da convention plugin
- Manter Detekt 1.23.8 e ktlint-gradle 12.1.1

### Opção B — Permanecer em Kotlin 2.3.0

Aguardar Detekt 2.0.0 estável com suporte a Kotlin 2.4 antes de migrar.

**Contras**: `kotlinx-serialization-json` permanece travada em 1.8.1; o ecossistema
(Arrow, Ktor) migra progressivamente para 2.4 aumentando drift.

### Opção C — Atualizar para Kotlin 2.3.21 (bug fix da série 2.3.x)

Usar a última patch release 2.3.x. Adia, mas não resolve o bloqueio de
`kotlinx-serialization` — a versão 1.11.0 exige Kotlin 2.3.20+ via alinhamento de ABI.
O próximo ciclo de release ainda exigiria o salto para 2.4, com o mesmo custo.

---

## Decisão

**Opção A** — upgrade para Kotlin 2.4.0 com os co-upgrades listados.

O bloqueio de `kotlinx-serialization-json` é o trigger primário: sem o upgrade do
Kotlin, o módulo `sql_persistence` não compila com versões recentes de serialization.
A Opção C apenas adia a mesma decisão por um ciclo.

O Detekt 1.23.8 é mantido; como o PR não introduz syntax Kotlin 2.4 nova (apenas
build scripts mudam), o compilador embutido no Detekt continuará analisando o source
sem incompatibilidade de parsing. Se o Detekt falhar em análise (NPE/OOM), o fallback
documentado é `detekt-gradle-plugin:2.0.0-alpha.3`, aceito como risco com plano de
upgrade para stable quando disponível.

---

## Consequências

**Positivas:**
- `kotlinx-serialization-json 1.11.0`: nova API de exceções JSON, correções de bug,
  melhor desempenho em decodificação
- `arrow-core 2.2.3`: suporte K2/2.4 explícito; melhorias de performance em `either {}`
  e `Raise`; API de `zipOrAccumulate` estabilizada
- Convention plugin sem flag deprecated: build mais limpo, zero warnings de compilador
- Alinha ao ciclo de release Kotlin; próxima revisão prevista: Kotlin 2.5.0 (dez/2026)

**Riscos:**
- Detekt 1.23.8 pode falhar ao analisar source compilado com KGP 2.4.0 se diferenças
  internas de bytecode afetarem o parser embutido. Mitigação: fallback para
  `detekt-gradle-plugin:2.0.0-alpha.3` (pré-release aceito como risco documentado)
- JaCoCo exclusion patterns em `http_api/build.gradle.kts` podem precisar de ajuste
  se K2 2.4 gerar nomes de classe sintética diferentes dos atuais. Mitigação:
  verificar cobertura após o build e ajustar padrões se a gate 96% não for mantida

**Neutras:**
- Ktor 3.4.1: sem mudança — binariamente compatível com Kotlin 2.4
- Gradle 8.13: sem mudança — dentro da faixa suportada (7.6.3–9.5.0)
- Nenhum arquivo `.kt` de produção ou teste requer alteração

---

## Referências

- [Kotlin 2.4.0 What's New](https://kotlinlang.org/docs/whatsnew24.html)
- [Kotlin 2.4 Compatibility Guide](https://kotlinlang.org/docs/compatibility-guide-24.html)
- [kotlinx.serialization 1.11.0 Release](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.11.0)
- [Arrow 2.2.3 Release](https://github.com/arrow-kt/arrow/releases)
- ADR-0004 — Avaliação de Qualidade (protocolo de execução de gaps)
- PR #118 — Upgrade Kotlin 2.3.0 + Ktor 3.4.1 (contexto do upgrade anterior)
