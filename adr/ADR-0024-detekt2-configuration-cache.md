---
status: accepted
date: 2026-07-05
decision-makers: "@agnaldo4j"
---

# ADR-0024 — Modernização do build: Detekt 2.0.0-alpha.5, daemon Java 25 e configuration cache

## Context and Problem Statement

O Detekt 1.23.8 é a única origem de três débitos de build (auditoria
`docs/quality/audit-2026-07.md`, GAP-AN): (1) emite a deprecation Gradle 10
`ReportingExtension.file`, que quebrará o build no upgrade de Gradle; (2) força o pin
`jvmTarget = "22"` no convention plugin enquanto o toolchain do projeto é Java 25; (3) prende o
daemon Gradle em Java 21 — a verificação empírica de 2026-07-02 (PR #205) mostrou que Kotlin 2.4.0
compila normalmente em daemon Java 25 com Gradle 9.6.1, e que o bloqueio real é o compilador
embutido do Detekt 1.23.8 (tasks abortam com `> 25.0.3`). Os comentários em `gradle.properties` e
no CI ainda atribuem o bloqueio ao Kotlin (entendimento antigo). Além disso, o configuration cache
do Gradle está desabilitado (`gradle.properties` só define `parallel` e `caching`).

Como modernizar o pipeline de análise estática e o build para eliminar esses débitos, sendo que a
linha Detekt 1.x está morta (último release: 1.23.8, fev/2025, contra Kotlin 2.0.21) e o Detekt
2.0.0 ainda não tem release estável?

## Decision Drivers

- Eliminar a deprecation Gradle 10 antes que vire erro de build.
- Alinhar daemon, toolchain e análise estática em Java 25 (um único JDK; fim do `.sdkmanrc` em 21).
- Compatibilidade com o stack atual: Kotlin 2.4.0 + Gradle 9.6.1 + JDK 25 — o Detekt 1.23.8 foi
  construído contra Kotlin 2.0.21 e a ADR-0016 já registrava o risco de falha de análise com KGP 2.4.
- Reduzir tempo de build local/CI (configuration cache; o plugin Detekt 2.x declara compatibilidade).
- Manter os gates de qualidade inalterados em semântica: 0 violações (`warningsAsErrors`),
  mesmos thresholds (cyclomatic 10, cognitive 15, LongMethod 30, LargeClass 200, linha 140,
  `ForbiddenImport` de `Jdbc*` fora do `AppModule`).
- `config/detekt/detekt.yml`, convention plugin e `gradle.properties` são imutáveis por política
  (`docs/politicas-explicitas.md` §4) — alterá-los exige esta ADR (gap `[E]`, ADR-first).

## Considered Options

1. **Adotar Detekt 2.0.0-alpha.5 agora** + daemon Java 25 + configuration cache (esta ADR).
2. **Aguardar Detekt 2.0.0 estável** — gap volta ao Backlog; deprecation, pin jvmTarget 22 e
   daemon Java 21 permanecem por prazo indefinido (não há data de release estável).
3. **Somente configuration cache**, mantendo Detekt 1.23.8 — resolve o débito menor e deixa os
   três débitos causados pelo Detekt intactos.

## Decision Outcome

Opção 1. O projeto adota:

1. **Detekt `2.0.0-alpha.5`** (novo plugin id/coordenadas `dev.detekt`), migrando
   `config/detekt/detekt.yml` para o schema 2.x **com semântica idêntica** de regras e thresholds.
   Adotar uma versão alpha é aceitável aqui porque: o alpha.5 (jun/2026) é construído exatamente
   contra Kotlin 2.4.0, Gradle 9.5.x e JDK 25 — o stack do projeto; a linha 1.x não recebe mais
   releases; e a ADR-0016 já havia aceitado `2.0.0-alpha.3` como fallback documentado, com
   compromisso de upgrade. O rollback é trivial (repinar `1.23.8` e restaurar o yml anterior).
   Bugs de regra introduzidos pelo alpha são tratados como ajuste de código ou upgrade de alpha,
   nunca como afrouxamento de config.
2. **Daemon Gradle em Java 25** — `.sdkmanrc`, CI (`setup-java`) e comentários de
   `gradle.properties`/`CLAUDE.md` atualizados; remoção do pin `jvmTarget = "22"` do convention
   plugin. Um único JDK para daemon, toolchain e runtime.
3. **Configuration cache habilitado** (`org.gradle.configuration-cache=true` em
   `gradle.properties`). Se algum plugin do build se mostrar incompatível (candidatos: ktlint-gradle,
   gradle-pitest-plugin, plugin Ktor), a incompatibilidade é documentada e a flag é mantida com a
   exceção registrada — claim reduzida explícita, conforme critério de aceite do GAP-AN.
4. Upgrade posterior de `2.0.0-alpha.5` para betas/RC/estável da linha 2.x é **normativo** (`[N]`,
   sem nova ADR), desde que os gates permaneçam com a mesma semântica.

## Confirmation

- `./gradlew testAll` verde com daemon em Java 25 (local via `.sdkmanrc`; CI com `setup-java` 25).
- `./gradlew testAll --warning-mode all` sem a deprecation `ReportingExtension.file` (e nenhuma
  outra deprecation originada do Detekt).
- `gradle.properties` contém `org.gradle.configuration-cache=true`; segunda invocação consecutiva
  reporta `Reusing configuration cache.` (ou a exceção documentada da claim reduzida).
- Gates preservados no CI: Detekt 0 violações, KtLint 0 erros, JaCoCo ≥ 97% por módulo,
  `:domain:pitest` verde.

## Consequences

- Bom: build livre da deprecation Gradle 10; caminho aberto para Gradle 10.
- Bom: um único JDK (25) para daemon, toolchain, análise estática e runtime — fim do setup especial
  de Java 21 local e no CI.
- Bom: configuration cache reduz tempo de configuração em builds repetidos (local e CI).
- Ruim: dependência de versão alpha no gate de qualidade — possíveis falsos positivos/negativos e
  breaking changes até a 2.0.0 estável; mitigado pelo rollback trivial e pelo item 4 acima.
- Ruim: migração do `detekt.yml` e do parsing de relatórios no CI (report `xml` → `checkstyle`)
  exige validação empírica cuidadosa para não silenciar o gate.

## More Information

- GAP-AN (ciclo P6): `docs/quality/audit-2026-07.md` · planejamento no
  [GitHub Project #6](https://github.com/users/agnaldo4j/projects/6).
- ADR-0016 (upgrade Kotlin 2.4) — manteve Detekt 1.23.8 e prometeu o plano de upgrade que esta ADR
  realiza. ADR-0023 — política de ADRs (MADR 4.0, ADR-first para gaps `[E]`).
- [Detekt 2.0.0 changelog/migração](https://detekt.dev/changelog-2.0.0) ·
  [Releases](https://github.com/detekt/detekt/releases) ·
  [Gradle configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html)
