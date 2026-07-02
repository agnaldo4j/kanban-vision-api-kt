# Política de Versionamento da API

> Decisão: [ADR-0022](../adr/ADR-0022-api-versioning-strategy.md) · Gap: GAP-AL (ciclo P5)

## Estratégia — URL-based versioning

As rotas de negócio são versionadas pelo prefixo de URL:

| Versão | Prefixo | Status |
|---|---|---|
| v1 | `/api/v1` | **Ativa** (versão atual) |
| v2 | `/api/v2` | Não existe — será introduzida por ADR futura quando houver breaking change |

Toda resposta da aplicação carrega o header **`API-Version: 1.0`** (plugin `VersioningHeaders`).
A spec OpenAPI (`/api.json`, Swagger em `/swagger`) documenta os paths completos com o prefixo `/api/v1`.

### Rotas intencionalmente fora do versionamento

Rotas de infraestrutura não pertencem ao contrato de negócio e não são versionadas:

| Rota | Função |
|---|---|
| `/health`, `/health/live`, `/health/ready` | Probes Kubernetes |
| `/metrics` | Scraping Prometheus |
| `/api.json`, `/swagger` | Documentação OpenAPI |
| `/auth/token` | Somente dev (`JWT_DEV_MODE=true`) |

## Regras de compatibilidade — additive-only em v1

**Permitido dentro de v1** (não exige nova versão):

- Adicionar novos endpoints.
- Adicionar campos **opcionais** em requests (com default) e novos campos em responses.
- Adicionar novos valores em enums de **entrada** quando o servidor os trata com fallback seguro.
- Melhorar documentação, exemplos e mensagens de erro (sem mudar contratos).

**Exige v2** (breaking change — proibido em v1):

- Remover ou renomear campo, endpoint ou parâmetro.
- Mudar tipo, formato ou semântica de um campo existente.
- Tornar obrigatório um campo antes opcional.
- Mudar códigos de status ou a estrutura de erro de um endpoint existente.
- Remover valores de enums retornados em responses.

## Ciclo de vida — 12 meses por versão

1. **Ativa** — v1 é a versão corrente; recebe mudanças additive-only.
2. **Deprecada** — no lançamento da v2, a v1 entra em deprecação e passa a emitir os headers
   `Deprecation: true`, `Sunset: <data RFC 8594>` e `Link: <...>; rel="successor-version"`
   (serão adicionados ao plugin `VersioningHeaders` quando a v2 existir).
3. **Suporte** — a v1 permanece funcional por **12 meses** após o lançamento da v2 (correções de
   segurança e bugs críticos; sem features novas).
4. **Remoção** — anunciada com **90 dias** de antecedência via changelog e header `Sunset`.

## Como introduzir a v2 (resumo para a ADR futura)

- Nova ADR com a motivação da breaking change e o plano de migração.
- Novo bloco `route("/api/v2")` convivendo com o v1; specs OpenAPI separadas por versão
  (`/api.json` v1 e `/api/v2.json` — ktor-openapi 5.x suporta specs nomeadas; validar na implementação).
- Endpoints v1 afetados marcados com `deprecated = true` na spec.
- Ativação dos headers de deprecação na v1 e início da janela de 12 meses.
