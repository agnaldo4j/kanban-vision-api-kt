## ADR

> Fill in if this PR implements an architectural decision:
> Decisão documentada em: `adr/ADR-NNNN-slug.md` _(or N/A)_

---

## Contexto

> 1–3 frases sobre o problema ou gap que motiva este PR.

---

## O que muda

-
-

---

## Checklist de Qualidade

### Pipeline
- [ ] `./gradlew testAll` verde localmente (Detekt + KtLint + testes + JaCoCo ≥ 95%)
- [ ] Zero violações Detekt (`warningsAsErrors = true`)
- [ ] Zero erros KtLint (`./gradlew ktlintFormat` executado antes do commit)
- [ ] Cobertura JaCoCo ≥ 95% em todos os módulos afetados
- [ ] PR ≤ 400 linhas alteradas

### Arquitetura
- [ ] Dependency Rule respeitada: `domain ← usecases ← http_api / sql_persistence`
- [ ] Zero imports de framework em `domain/` ou `usecases/` (exceto Arrow-kt)
- [ ] Repository interfaces definidas em `usecases/repositories/`, implementações em `sql_persistence/`
- [ ] Erros de domínio modelados como `Either<DomainError, T>` — sem exceções não tratadas

### Segurança e Dados
- [ ] Sem secrets, tokens ou PII no código ou logs
- [ ] `JWT_DEV_MODE=true` não presente em código de produção

### Rastreabilidade
- [ ] Branch segue convenção: `feat/gap-X-slug`, `fix/...`, `docs/...`
- [ ] Commits com mensagens descritivas e referência ao gap/ticket
- [ ] Se ADR aplicável: status atualizado para `Aceita` e campo `PR` preenchido

### ADR-only checklist (preencher se este PR implementa uma ADR)
- [ ] Plano de implementação com todas as tarefas marcadas `[x]`
- [ ] Todos os itens do DOD confirmados ou marcados N/A com justificativa
- [ ] Diagramas C4 atualizados se novo módulo, rota ou use case foi adicionado

---

## Plano de Testes

- [ ] Testes unitários: _(descreva os cenários — happy path + erro)_
- [ ] Testes de integração: _(descreva os boundaries testados)_
- [ ] CI pipeline verde no PR

---

🤖 Generated with [Claude Code](https://claude.com/claude-code)
