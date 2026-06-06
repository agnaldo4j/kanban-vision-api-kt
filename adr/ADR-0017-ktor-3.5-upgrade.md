# ADR-0017 — Upgrade Ktor de 3.4.1 para 3.5.0

## Cabeçalho

| Campo     | Valor                          |
|-----------|--------------------------------|
| Status    | Aceita                         |
| Data      | 2026-06-05                     |
| Autores   | @agnaldo4j                     |
| Branch    | chore/ktor-3.5-upgrade         |
| PR        | https://github.com/agnaldo4j/kanban-vision-api-kt/pull/129 |
| Supersede | —                              |

---

## Contexto e Motivação

O projeto usa Ktor **3.4.1** desde o PR #118 (março 2026). Ktor **3.5.0** foi
lançado em **14 de maio de 2026** como release estável.

Três bugs que afetam o projeto diretamente são resolvidos nesta versão:

1. **KTOR-8320** — `CallLogging` instalado em `testApplication` exibia logs no stdout
   de CI, interferindo na legibilidade dos relatórios de teste.
2. **KTOR-8151** — `MicrometerMetrics` emitia warning `MeterFilters configured after
   a Meter has been registered` ao instalar o plugin com registry Prometheus via Koin
   antes do startup — exatamente o padrão usado em `configureMetrics()`.
3. **KTOR-9542** — regressão introduzida em 3.4.3 onde handlers de requisição Netty
   passaram a rodar no worker event loop em vez do call event loop correto. O projeto
   está em 3.4.1 (pré-regressão); o upgrade fecha a janela de risco para patches
   futuros e confirma que a versão em produção nunca terá o bug.

Adicionalmente, `io.ktor.server.auth.Principal` recebeu `@Deprecated` com
`DeprecationLevel.WARNING` (KTOR-9552). `JWTPrincipal` — usada no projeto — não
foi deprecada. Nenhuma ação imediata é necessária.

---

## Forças (Decision Drivers)

- Ktor 3.5.0 estável e production-ready desde 14/05/2026
- Kotlin 2.4.0 (projeto) é forward-compatible com Ktor 3.5.0 (compilado em Kotlin 2.3.21)
- Três bugs que afetam o projeto em produção e CI são resolvidos
- `io.github.smiley4:ktor-swagger-ui:5.7.0` reduz o gap de compatibilidade com Ktor 3.5
- Nenhum arquivo `.kt` de produção ou teste requer alteração
- CI mantém `./gradlew testAll` como safety gate antes de merge

---

## Opções Consideradas

### Opção A — Atualizar para Ktor 3.5.0 com co-upgrade smiley4 5.7.0 [ESCOLHIDA]

- Todos os artefatos `io.ktor:*`: `3.4.1` → `3.5.0` (12 artefatos)
- Plugin `io.ktor.plugin`: `3.4.1` → `3.5.0` (`build.gradle.kts` raiz)
- `ktor-openapi` e `ktor-swagger-ui`: `5.6.0` → `5.7.0`

### Opção B — Atualizar Ktor 3.5.0 sem bump do smiley4

Manter `5.6.0`. A API de roteamento do Ktor é retrocompatível entre 3.3.x e 3.5.x;
a biblioteca funcionaria em runtime. Sem benefício em adiar o bump — 5.7.0 traz
apenas melhorias aditivas.

### Opção C — Permanecer em Ktor 3.4.1

Aguardar mais tempo. Os três bugs continuam presentes; acumula drift com o ecossistema.

---

## Decisão

**Opção A** — upgrade para Ktor 3.5.0 com co-upgrade smiley4 5.7.0.

O co-upgrade do smiley4 elimina o gap de compatibilidade não testada sem custo
adicional (zero breaking changes em 5.7.0). Nenhum `.kt` requer edição.

---

## Consequências

**Positivas:**
- Logs de `CallLogging` não mais poluem stdout em testes (KTOR-8320)
- Warning `MicrometerMetrics` eliminado em startup (KTOR-8151)
- Proteção contra regressão Netty 3.4.3 garantida (KTOR-9542)
- `DynamicProviderConfig.authenticateFunction` agora é `suspend` (KTOR-9276) —
  disponível para uso futuro sem novo upgrade
- `ktor-swagger-ui:5.7.0` — webjars de swagger-ui e redoc resolvidos por versão
  automaticamente, remove acoplamento implícito a versões específicas de webjars

**Riscos:**
- `Principal` deprecado com `WARNING`: K2 emitirá aviso em compilação, mas não
  erro (`DeprecationLevel.WARNING`). Detekt `warningsAsErrors` não cobre annotations
  `@Deprecated` do Kotlin — gates de qualidade não são afetados. Ação futura:
  quando o Ktor emitir `DeprecationLevel.ERROR` (esperado no 4.x), substituir uso
  direto de `Principal` no `Authentication.kt`.
- Exclusion patterns JaCoCo (`**/*RoutesKt$*`, `**/plugins/*Kt$*`): gerados pelo
  compilador do projeto (Kotlin 2.4.0), não pelo Ktor. Risco de mudança mínimo;
  verificar cobertura ≥ 96% após build.

**Neutras:**
- Kotlin 2.4.0 compatível com Ktor 3.5.0 (compilado em Kotlin 2.3.21)
- Gradle 8.13: sem mudança — dentro da faixa suportada
- Nenhum arquivo `.kt` de produção ou teste requer alteração
- Próxima revisão: Ktor 3.6.0 ou Kotlin 2.5.0 (dez/2026)

---

## Referências

- [Ktor 3.5.0 Changelog](https://ktor.io/changelog/3.5/)
- [KTOR-8320 — CallLogging test stdout](https://youtrack.jetbrains.com/issue/KTOR-8320)
- [KTOR-8151 — MicrometerMetrics warning](https://youtrack.jetbrains.com/issue/KTOR-8151)
- [KTOR-9542 — Netty threading regression](https://youtrack.jetbrains.com/issue/KTOR-9542)
- [KTOR-9552 — Principal deprecation](https://youtrack.jetbrains.com/issue/KTOR-9552)
- [ktor-swagger-ui 5.7.0 Release](https://github.com/smiley4/ktor-swagger-ui/releases/tag/5.7.0)
- ADR-0016 — Upgrade Kotlin 2.4.0
- PR #118 — Upgrade Kotlin 2.3.0 + Ktor 3.4.1
