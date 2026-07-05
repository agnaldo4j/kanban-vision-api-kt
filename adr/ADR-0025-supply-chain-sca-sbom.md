---
status: accepted
date: 2026-07-05
decision-makers: "@agnaldo4j"
---

# ADR-0025 — Supply chain: SBOM via CycloneDX e SCA via osv-scanner com gate bloqueante no CI

## Context and Problem Statement

O projeto não tem hoje nenhum scan de vulnerabilidades conhecidas (SCA) nem geração de
SBOM — o DoD §4 (Security and Compliance) exige SCA e a auditoria `docs/quality/audit-2026-07.md`
apontou o gap (GAP-AO, dimensão Security). O Dependabot mantém as dependências atualizadas,
mas não bloqueia um PR que introduza (ou conviva com) uma CVE conhecida, e não produz um
inventário auditável do que a imagem publicada em `ghcr.io` contém.

Restrição relevante: o osv-scanner não resolve dependências Gradle sem lockfile ou
verification-metadata — e o projeto não usa nenhum dos dois. Qual ferramenta de SCA + SBOM
adotar no CI, e com qual comportamento de gate?

## Decision Drivers

- Fechar o DoD §4 (SCA) com gate automatizado, não com verificação manual.
- SBOM como artefato auditável de cada build (rastreabilidade de supply chain).
- CI rápido: o pipeline de qualidade hoje roda em poucos minutos; o scan não pode dominá-lo.
- Zero novos secrets a gerenciar, se possível.
- Fail closed (security.md): erro ou vulnerabilidade → build vermelho; exceções sempre explícitas.

## Considered Options

1. CycloneDX Gradle plugin (SBOM) + osv-scanner v2 escaneando o SBOM (SCA).
2. OWASP dependency-check (plugin Gradle, base NVD).
3. GitHub dependency submission + Dependabot alerts (nativo do GitHub).

## Decision Outcome

**Escolhida: Opção 1 — CycloneDX + osv-scanner**, com gate bloqueante desde o primeiro dia.
O plugin `org.cyclonedx.bom` gera o SBOM agregado dos módulos a partir do grafo real do
Gradle (contornando a limitação de lockfile do osv-scanner) e o osv-scanner escaneia esse
SBOM contra a base OSV.dev em segundos, sem API key. Uma única execução produz os dois
artefatos exigidos (SBOM + relatório de SCA). Vulnerabilidade conhecida falha o job;
exceções só via `osv-scanner.toml` com `reason` documentada — nunca desligando o gate.

### Confirmation

Job `supply-chain` em `.github/workflows/ci.yml`: gera o SBOM (`cyclonedxBom`), publica-o
como artifact e roda o osv-scanner contra ele; o job `build` (imagem Docker) passa a
depender de `quality` **e** `supply-chain` — nenhuma imagem é publicada com CVE conhecida
não tratada. Exceções são auditáveis por grep em `osv-scanner.toml` (cada entrada exige
`reason`). Verificação em review: PR que adicionar ignore sem justificativa é rejeitado.

## Consequences

- Bom: DoD §4 (SCA) fecha com gate automatizado; SBOM auditável a cada build.
- Bom: sem secrets novos e sem downloads pesados de base de CVE no CI.
- Ruim: uma CVE nova em dependência transitiva pode avermelhar PRs não relacionados;
  mitigação: Dependabot semanal já mantém as deps recentes e o `osv-scanner.toml` permite
  exceção temporária documentada enquanto o upgrade não chega.
- Ruim: cobertura OSV.dev para o ecossistema Maven difere da NVD em casos raros;
  mitigação: os Dependabot alerts do GitHub (GHSA) seguem ativos como segunda fonte.

## Pros and Cons of the Options

### Opção 1 — CycloneDX + osv-scanner
- Bom: rápido (segundos), sem API key, SBOM nativo, action oficial do Google, compatível Gradle 9 + configuration cache.
- Ruim: depende do SBOM como entrada (mais um passo de build encadeado no job).

### Opção 2 — OWASP dependency-check
- Bom: cumpre literalmente o texto atual do DoD §4; base NVD consolidada.
- Ruim: exige NVD API key (novo secret), download de base de minutos por build, histórico de falsos positivos em JVM e não gera SBOM — o CycloneDX seria necessário de qualquer forma.

### Opção 3 — GitHub dependency submission + Dependabot alerts
- Bom: nativo, zero manutenção no repo.
- Ruim: alerta assíncrono, não bloqueia PR; não produz SBOM; acopla o gate ao GitHub em vez do pipeline.

## More Information

- Branch: `feat/adr-0025-supply-chain-sca-sbom` · PR: https://github.com/agnaldo4j/kanban-vision-api-kt/pull/216
- Item no board #6: GAP-AO [M] — Supply chain: SCA + SBOM no CI (ciclo P6) — o plano de implementação vive lá e no PR de implementação, não aqui.
- Referências: `docs/quality/audit-2026-07.md` · `.claude/rules/security.md` (DoD §4) ·
  [CycloneDX Gradle plugin](https://github.com/CycloneDX/cyclonedx-gradle-plugin) ·
  [osv-scanner](https://google.github.io/osv-scanner/) · ADR-0023 (política de ADRs) · ADR-0024 (build no qual o job se apoia).
