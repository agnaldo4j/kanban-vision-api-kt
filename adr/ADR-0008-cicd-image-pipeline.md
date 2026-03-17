# ADR-0008 — Pipeline CI/CD de Build e Push de Imagem Docker

## Cabeçalho

| Campo     | Valor                                                    |
|-----------|----------------------------------------------------------|
| Status    | Aceita                                                   |
| Data      | 2026-03-17                                               |
| Autores   | @agnaldo4j                                               |
| Branch    | feat/adr-0008-cicd-image-pipeline                        |
| PR        | (preencher após abrir o PR)                              |
| Supersede | —                                                        |

---

## Contexto e Motivação

O CI atual (`.github/workflows/ci.yml`) executa apenas o job `quality` — Detekt,
KtLint, testes e JaCoCo. O `Dockerfile` multi-stage foi adicionado no GAP-G (PR #62),
mas nenhum pipeline constrói ou publica a imagem. Isso significa que:

- Não há artefato versionado e auditável por commit/tag.
- O `Dockerfile` nunca é exercitado automaticamente — falhas de build passam despercebidas.
- O processo de deploy é inteiramente manual, sem rastreabilidade entre código e imagem.

Esta ADR decide **onde** publicar a imagem e **quando** o build é acionado. O deploy
automatizado em cluster Kubernetes está fora do escopo desta ADR (depende de infraestrutura
de cluster ainda não definida) e será endereçado em ADR futura.

---

## Forças (Decision Drivers)

- [ ] Zero segredos adicionais para o caso base (reduz superfície de ataque e configuração)
- [ ] Build exercitado em todo PR para detectar falhas de Dockerfile rapidamente
- [ ] Imagem publicada com tag rastreável (SHA do commit e tag semântica)
- [ ] Sem custo adicional para o repositório público
- [ ] Integração nativa com o ecossistema GitHub já em uso
- [ ] Separação clara entre build/push (CI) e deploy (infraestrutura — escopo futuro)

---

## Opções Consideradas

- **Opção A**: GitHub Container Registry (ghcr.io) com `GITHUB_TOKEN`
- **Opção B**: Docker Hub com segredos `DOCKERHUB_USERNAME` + `DOCKERHUB_TOKEN`
- **Opção C**: Build local apenas — não publica imagem no CI

---

## Decisão

**Escolhemos a Opção A** (GitHub Container Registry com `GITHUB_TOKEN`) porque elimina
a necessidade de segredos externos, está disponível sem custo adicional para repositórios
públicos e se integra nativamente com as permissões já configuradas no CI. A imagem é
publicada com dois tags: `sha-<commit>` (rastreabilidade) e `latest` (conveniência).
Em push de tag semver (`v*.*.*`), a imagem recebe adicionalmente o tag de versão.

---

## Análise das Opções

### Opção A — GitHub Container Registry (ghcr.io)

**Prós:**
- Autenticação via `GITHUB_TOKEN` já disponível no runner — zero segredos adicionais
- Gratuito para repositórios públicos, sem limite de pulls
- Pacotes vinculados ao repositório — visibilidade e permissões herdadas do repo
- `docker/login-action` + `docker/metadata-action` + `docker/build-push-action` são
  actions oficiais mantidas pelo Docker, amplamente adotadas

**Contras:**
- URL `ghcr.io/org/repo` menos familiar que Docker Hub para times acostumados com hub
- Visibilidade do pacote precisa ser configurada manualmente na primeira publicação

### Opção B — Docker Hub

**Prós:**
- Registry mais conhecido; `docker pull` sem prefixo de registry
- Integração com Docker Scout para análise de vulnerabilidades

**Contras:**
- Requer criação e rotação de `DOCKERHUB_TOKEN` (segredo externo)
- Rate limiting para pulls em repositórios públicos (100 pulls/6h por IP anônimo)
- Custo para repositórios privados ou alto volume

### Opção C — Build local apenas (sem publicação)

**Prós:**
- Exercita o Dockerfile no CI sem necessidade de registry
- Custo e complexidade zero

**Contras:**
- Sem artefato versionado e rastreável — não resolve o gap de auditabilidade
- Deploy continua totalmente manual sem referência de imagem

---

## Consequências

**Positivas:**
- Cada merge em `main` produz uma imagem `ghcr.io/<owner>/kanban-vision-api:sha-<sha>`
- Cada tag `v*.*.*` produz `ghcr.io/<owner>/kanban-vision-api:v1.2.3` + `latest`
- Falhas de build de imagem bloqueiam o PR antes do merge
- Rastreabilidade completa: commit → imagem → tag semântica

**Negativas / Trade-offs:**
- Job `build` adiciona ~3–5 min ao pipeline (build multi-stage com cache de layers)
  → Mitigado com `cache-from`/`cache-to` via GitHub Actions cache
- Primeira publicação requer configurar visibilidade do pacote em ghcr.io
  → Documentado no Plano de Implementação

**Neutras:**
- Deploy em cluster continua manual até ADR de infraestrutura de cluster
- O `kustomization.yaml` já suporta override de tag via `images:` (GAP-G)

---

## Plano de Implementação

- [ ] 1. Adicionar job `build` em `.github/workflows/ci.yml` com dependência em `quality`
- [ ] 2. Usar `docker/metadata-action` para gerar tags (`sha-<sha>`, `latest`, semver)
- [ ] 3. Usar `docker/build-push-action` com `push: true` somente em `push` para `main` ou tag; `push: false` em PRs (valida build sem publicar)
- [ ] 4. Configurar `cache-from`/`cache-to` com `type=gha` para reutilizar layers entre runs
- [ ] 5. Adicionar permissão `packages: write` ao job `build`
- [ ] 6. Documentar no README como fazer pull da imagem e como acionar deploy manual com `kubectl`
- [ ] 7. Executar `./gradlew testAll` — build verde (ci.yml é infra, sem alteração de código Kotlin)

---

## Garantias de Qualidade

### DOD — Definition of Done

- [x] **1. Rastreabilidade**: branch `feat/adr-0008-cicd-image-pipeline` ↔ esta ADR ↔ PR
- [ ] **2. Testes**: job `build` com `push: false` em PRs valida o Dockerfile sem side effects
- [x] **3. Compatibilidade**: nenhuma quebra de API — apenas adição de job ao CI
- [x] **4. Segurança**: usa `GITHUB_TOKEN` (escopo mínimo) — nenhum segredo externo commitado
- [ ] **5. CI/CD**: `./gradlew testAll` verde; job `build` verde no primeiro push
- [x] **6. Observabilidade**: tags de imagem incluem SHA do commit — rastreabilidade de build
- [x] **7. Performance**: cache de layers do Docker minimiza tempo de build incremental
- [x] **8. Deploy seguro**: `push: false` em PRs evita publicação de imagens de branches temporárias
- [ ] **9. Documentação**: README atualizado com instruções de pull e deploy manual

### Qualidade de Código

| Ferramenta | Requisito | Status |
|---|---|---|
| Detekt | zero violações | N/A — sem código Kotlin novo |
| KtLint | zero erros | N/A — sem código Kotlin novo |
| JaCoCo | ≥ 95% | N/A — sem código Kotlin novo |

### Aderência à Arquitetura

- [x] **Dependency Rule**: alteração restrita a `.github/workflows/ci.yml` — zero impacto nas camadas de domínio
- [x] **Domain puro**: nenhuma dependência de framework introduzida no domínio

---

## Referências

- GAP-G (PR #62) — Dockerfile multi-stage que este pipeline constrói
- `k8s/kustomization.yaml` — suporte a override de tag via `images:`
- [docker/build-push-action](https://github.com/docker/build-push-action)
- [docker/metadata-action](https://github.com/docker/metadata-action)
- [GitHub Container Registry docs](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
- ADR-0004 — GAP-V classificado como estrutural `[E]`
