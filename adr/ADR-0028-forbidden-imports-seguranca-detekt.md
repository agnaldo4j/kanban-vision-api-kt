---
status: accepted
date: 2026-07-06
decision-makers: "@agnaldo4j"
---

# ADR-0028 — ForbiddenImports de segurança no Detekt (ObjectInputStream e MessageDigest)

## Context and Problem Statement

Dois vetores de segurança conhecidos hoje dependem exclusivamente de atenção humana em code
review para não entrarem no código:

1. **`java.io.ObjectInputStream`** — a desserialização nativa Java é um vetor clássico de RCE
   (OWASP A08:2025, Software and Data Integrity Failures): qualquer `readObject()` sobre dados
   não confiáveis permite execução de gadget chains. O projeto serializa exclusivamente via
   `kotlinx.serialization`; não existe uso legítimo previsto.
2. **`java.security.MessageDigest`** — API de crypto de baixo nível cujo uso direto conduz a
   escolhas fracas (MD5/SHA-1) e a erros de design (hash simples para senha, sem salt/custo).
   Hash de senha exige Argon2/BCrypt via biblioteca; integridade de dados exige API de alto nível.

A recomendação de bloquear ambos existe em `.claude/rules/security.md` desde o ciclo de
hardening, marcada como "adição futura via ADR" porque `config/detekt/detekt.yml` é imutável
por política (ADR-0023 + regras de qualidade). Grep confirma zero uso atual dos dois imports em
todos os módulos — a regra entra sem quebrar código existente.

**Pergunta a decidir**: onde aplicar o bloqueio desses imports como gate automático?

## Decision Drivers

- Gate deve falhar o build (não apenas avisar) — mesma força do `warningsAsErrors` já em vigor.
- Mensagem de erro deve ensinar a alternativa correta no ponto do erro.
- Reusar infraestrutura existente em vez de criar mecanismo novo.
- `detekt.yml` é protegido: a mudança precisa de decisão registrada e imutável.

## Considered Options

1. `ForbiddenImport` no `config/detekt/detekt.yml` — estender a regra que já bloqueia `Jdbc*`.
2. Regra Konsist no módulo `architecture/` — fitness function sobre imports.
3. Manter apenas documentação (`security.md`) + code review.

## Decision Outcome

**Escolhida: Opção 1 (ForbiddenImport no Detekt)**, porque o Detekt roda em todo `testAll` e no
CI com `warningsAsErrors: true` (falha o build no import, antes de qualquer teste), a entrada
carrega um `reason` exibido no ponto exato da violação (ensina a alternativa), e a regra
`ForbiddenImport` já está ativa com a entrada `Jdbc*` — custo marginal zero de infraestrutura.

### Confirmation

O próprio gate: Detekt com `warningsAsErrors: true` em `testAll` e no job `quality` do CI.
Validação negativa registrada no PR de implementação: adicionar `import java.io.ObjectInputStream`
temporário → `./gradlew detekt` FALHA com a mensagem do `reason` → remover.

## Consequences

- Bom: dois vetores OWASP (A08 e crypto fraca) passam de recomendação para gate bloqueante.
- Bom: o `reason` da regra documenta a alternativa correta no momento do erro.
- Ruim: uso legítimo futuro (ex.: `MessageDigest` para checksum não-criptográfico de cache)
  exigirá `@Suppress("ForbiddenImport")` com justificativa em comentário — fricção intencional.

## Pros and Cons of the Options

### Opção 1 — ForbiddenImport no detekt.yml

- Bom: falha no build em segundos, antes dos testes; mensagem com `reason` no ponto da violação.
- Bom: infraestrutura já existente (entrada `Jdbc*` ativa); manutenção em um único arquivo.
- Ruim: exige esta ADR (arquivo protegido) — custo pago uma única vez.

### Opção 2 — Regra Konsist em architecture/

- Bom: expressividade programática (poderia diferenciar contextos de uso).
- Ruim: roda apenas na fase de teste do `testAll` (mais tarde que o Detekt); mensagem de assert
  menos direta; o módulo é desenhado para fitness functions de arquitetura, não SAST.

### Opção 3 — Somente documentação + review

- Bom: zero mudança em arquivo protegido.
- Ruim: não é gate — depende de atenção humana, exatamente a fraqueza que motivou esta ADR.

## More Information

- Branch: `feat/adr-0028-detekt-security-imports` · PR: https://github.com/agnaldo4j/kanban-vision-api-kt/pull/235
- Item no board #6: GAP-AV — o plano de implementação vive lá, não aqui
- Referências: `.claude/rules/security.md` (recomendação original), ADR-0023 (imutabilidade de
  configs), skill `/owasp` (A08), regra `ForbiddenImport` existente (`detekt.yml:67`)
