# Quality Scorecard — post-GAP-CB (in-cluster observability + CI/supply-chain hardening)

Full re-score on `main` after the **GAP-CB cycle** (ADR-0043 + PRs #327/#328/#330/#333/#334 shipped the
in-cluster k8s observability stack) and its follow-ups **GAP-DC** (#335), **GAP-DB** (#337) and **GAP-DA**
(#338), plus the two supply-chain CVE fixes (#329 jackson, #331 netty). Each of the 22 dimensions was
re-assessed against the current codebase with its `/skill` rubric and the live CI signals. This file is the
in-repo, **immutable snapshot** (ADR-0023) and the source for the wiki `Quality-Analysis` page. It supersedes
`scorecard-2026-08.md` (it does not edit it).

## Overall — 9.35 / 10  (was 9.28)

**Method (reproducible):** the overall is the **simple arithmetic mean of the dimension scores** —
recomputable from the table (`205.7 / 22 = 9.35`).

What moved the headline, honestly:
- **Observability 9.2 → 9.5** — the stack was **docker-compose-only**; the annotations `k8s/03-deployment.yml`
  emitted (`prometheus.io/scrape`) had no in-cluster collector. ADR-0043 (GAP-CB) shipped a self-contained
  in-cluster stack — Prometheus + rules + `kubernetes_sd` scrape → Alertmanager + `alert-sink` → Grafana, no
  Operator/CRD — closing the "annotations without a collector" gap.
- **Circular-dependency 8.3 → 8.7** — the exact residual the last scorecard named ("class↔class cycles inside a
  single package are not analysed") is **closed**: `ClassCycleTest`/`ClassGraphTest`/`CycleDetectionTest`
  (GAP-CO/GAP-CV) build the class graph by FQN and detect intra-package cycles. It crosses into the 8.5+ band.
- **CI/supply-chain hardening rippled smaller honest gains** — Infra 9.3 → 9.5, Security 9.0 → 9.2, CI gates
  9.7 → 9.8, GraalVM 9.1 → 9.2, and a c4-model 9.2 → 9.3 for this docs refresh.

**Only two dimensions still sit below 9.0** (Performance 8.8, Circular-dependency 8.7).

> Overlap note (unchanged): **GraalVM** overlaps Infra + Performance, and **Circular-dependency** overlaps
> Clean Architecture + Refactoring. Excluding those two rows, the **20-skill** mean is **9.39** (`187.8 / 20`).

## 22-skill scorecard

| # | Skill | Dimension | Prev (2026-08) | New | Δ |
|---|---|---|---|---|---|
| 1 | ddd | Domain modeling | 9.7 | 9.7 | — |
| 2 | clean-architecture | Layering | 9.6 | 9.6 | — |
| 3 | screaming-architecture | Package intent | 9.3 | 9.3 | — |
| 4 | solid-principles | SOLID | 9.4 | 9.4 | — |
| 5 | fp-oo-kotlin | Functional Kotlin | 9.5 | 9.5 | — |
| 6 | refactoring | Code health | 9.3 | 9.3 | — |
| 7 | testing-and-observability | Test quality | 9.7 | 9.7 | — |
| 8 | kotlin-quality-pipeline | CI gates | 9.7 | **9.8** | +0.1 |
| 9 | openapi-quality | API docs | 9.0 | 9.0 | — |
| 10 | db-migrations | Schema | 9.1 | 9.1 | — |
| 11 | microservices-modular-monolith | Boundaries | 9.4 | 9.4 | — |
| 12 | local-and-production-environment | Infra | 9.3 | **9.5** | +0.2 |
| 13 | opentelemetry | Observability | 9.2 | **9.5** | +0.3 |
| 14 | evolutionary-change | Change mgmt | 9.4 | 9.4 | — |
| 15 | adr | Decision records | 9.7 | 9.7 | — |
| 16 | c4-model | Architecture docs | 9.2 | **9.3** | +0.1 |
| 17 | xp-kanban | Engineering practices | 9.4 | 9.4 | — |
| 18 | definition-of-done | DoD | 9.2 | 9.2 | — |
| 19 | owasp | Security | 9.0 | **9.2** | +0.2 |
| 20 | load-testing | Performance efficiency | 8.7 | **8.8** | +0.1 |
| 21 | graalvm | GraalVM / Native runtime | 9.1 | **9.2** | +0.1 |
| 22 | circular-dependency-control | Circular-dependency control | 8.3 | **8.7** | +0.4 |

**Mean = 205.7 / 22 = 9.35.** Eight dimensions rose; fourteen held; none regressed.

## What moved, and why (evidence)

- **Observability 9.2 → 9.5** — ADR-0043 + GAP-CB delivered the in-cluster stack the compose setup only
  simulated: `k8s/10-prometheus.yml` (Deployment + Service + a `ClusterRole` with `nodes/proxy` to *authorize*
  the kubelet cAdvisor scrape, not just discover it), `k8s/12-prometheus-config.yml` (`kubernetes_sd_configs`
  honouring the pod scrape annotations + node cAdvisor via the apiserver proxy), `k8s/11-prometheus-rules.yml`
  (rules, with the container rules scoped to `namespace="kanban-vision"`), `k8s/13-alertmanager.yml` (Alertmanager
  + a reachable in-cluster `alert-sink`, closing the "alerts to nowhere" gap), `k8s/14-grafana.yml`
  (Prometheus datasource + dashboard). **Residual cap (why not higher):** no in-cluster tracing (OTLP/Tempo is
  out of scope, `OTEL_*_EXPORTER=none`), monitoring is single-replica/ephemeral, and it's a reference manifest
  (no live cluster in CI — confirmation is by coherence, like ADR-0037/0040).
- **Circular-dependency 8.3 → 8.7** — `ClassCycleTest` + `ClassGraphTest` + `CycleDetectionTest` (GAP-CO/GAP-CV)
  now build the **class↔class** graph by FQN (with homonym-safe node identity, GAP-CV) and fail CI on an
  intra-package cycle — the precise residual the previous scorecard flagged. `PackageCycleTest` (whole-graph) and
  `ProjectDependencyGraphTest` (module graph) still hold. Residual: it is a static import/graph analysis, not a
  runtime call-graph.
- **Infra 9.3 → 9.5** — the k8s manifests went from "app + DB + HPA + PDB" to a full self-contained reference
  deployment including the observability stack (`k8s/10..14` + local config copies), all building under **default**
  kustomize restrictions so `kubectl apply -k k8s/` works (the `../observability` `apply -k` break was fixed by
  keeping the reused configs as local copies). Node-spread (ADR-0040) also landed.
- **Security 9.2** — the **SCA gate is now comprehensive**: the SBOM aggregates `runtimeClasspath` **and
  `migrationRuntime`** (GAP-DA), so a CVE reachable only through the `kanban-vision-migrate` binary can no longer
  pass the gate silently (the class of blind spot the #329 jackson fix had to check by hand); `osv-scanner.toml`
  carries **0 stale exceptions**; and the migrate binary is now booted in the CI smoke test. Held below 9.5 —
  unchanged: **HS256 symmetric JWT** (no asymmetric rotation) and **no automated DAST/pentest**.
- **CI gates 9.7 → 9.8** — the `config-lint` gate now validates the **k8s config copies**, not just
  `observability/*`: `amtool` on `k8s/alertmanager.yml`, `promtool` on the ConfigMap payloads extracted by
  `scripts/extract-k8s-observability-payloads.py`, a k8s-specific inhibit invariant
  (`scripts/assert-k8s-observability-invariants.py`, `equal ⊇ {namespace,pod}`) and a Grafana-yml drift-guard
  (GAP-DB) — closing the "opaque ConfigMap payloads pass the gate" hole that three reviews surfaced.
- **GraalVM 9.2** — the CI smoke test now additionally **boots the native `kanban-vision-migrate` binary**
  against ephemeral Postgres (GAP-DA), exercising the Flyway/native-path reachability metadata the main-binary
  smoke never touched. Native all-route smoke on the API binary is still a probed subset.
- **c4-model 9.3** — the wiki (`Observability`, `Operations`, `Home`, `Quality-Analysis`, `Security-Supply-Chain`)
  and README were refreshed to the in-cluster reality and current versions in this same change.

### Held (unchanged, no material change since the 2026-08 re-score)
DDD 9.7, Clean 9.6, Screaming 9.3, SOLID 9.4, Functional Kotlin 9.5, Refactoring 9.3, Test quality 9.7,
OpenAPI 9.0, Schema 9.1, Microservices 9.4, Change mgmt 9.4, ADR 9.7, XP/Kanban 9.4, DoD 9.2.

## Key metrics (current)

- **Modules:** **7** Gradle modules — `domain-common`, `domain-kanban`, `domain-simulation`, `usecases`,
  `sql_persistence`, `http_api`, plus the test-only `architecture`. Graph `simulation → kanban → common`
  (fitness-enforced).
- **Tests:** ~540 across the product modules + Konsist; pyramid = unit · Kotest property · embedded-Postgres
  integration · `testApplication` route · Pact contract.
- **Coverage:** JaCoCo gate **≥ 98%/module** (ADR-0029), all 7 modules; measured overall ~99%.
- **Mutation (PITest, STRONGER):** domain-common 100 (gate 90) · domain-kanban 82 (78) · domain-simulation 77
  (73) · usecases 60 (55) · sql_persistence 72 (65) · http_api 51 (45).
- **Static analysis:** Detekt 2.0.0-alpha.5 `warningsAsErrors`, **0 violations** · KtLint 1.5.0, 0.
- **Architecture fitness:** Konsist + JUnit **31 fitness tests across 11 suites** — Hexagonal, Ports placement,
  Conventions, Domain purity, Context boundary, Contract package, Package cycle, Project dependency graph, and the
  new **class-cycle** trio `ClassCycleTest`/`ClassGraphTest`/`CycleDetectionTest` (GAP-CO/CV).
- **Supply chain:** CycloneDX SBOM (plugin 3.3.0) over `runtimeClasspath` **+ `migrationRuntime`** + osv-scanner
  SCA, **blocking** (ADR-0025/GAP-DA); **0 active exceptions**.
- **Config-lint:** `amtool`/`promtool` over `observability/*` **and** the k8s config copies, blocking (GAP-CY/GAP-DB).
- **Observability:** in-cluster k8s stack (Prometheus + Alertmanager + alert-sink + Grafana, ADR-0043) alongside
  the docker-compose stack; traces via OTel SDK, no javaagent (ADR-0031).
- **Performance:** k6 baseline p95 ~22 ms, ~1,644 req/s (ADR-0027, manual); scheduled non-blocking regression
  tripwire (ADR-0039).
- **Runtime:** GraalVM Native Image — ~0.12 s startup, ~74 MiB RSS (ADR-0030→0032); both the api and the migrate
  binary are native and smoke-tested.

## Residual debt & candidate gaps for the next cycle

Recorded state, not a schedule (ADR-0023 — board #6 is the single source of progress). The last cycle **closed**
the in-cluster-observability, class-cycle-detection, and SBOM/migration-binary residuals; what remains:

- **Performance (8.8):** the profiles + comparator exist (GAP-BO) and the scheduled tripwire signals regression
  (ADR-0039), but there is still **no per-PR blocking regression gate** — by design (ADR-0027: shared CI runners
  are too noisy for a stable machine-dependent baseline); single reference environment.
- **Circular-dependency (8.7):** class-cycle detection is now static/import-graph based; a runtime call-graph
  analysis is out of scope.
- **Security (9.2):** the supply-chain and IDOR/header/rate-limit residuals are closed; what remains is
  **HS256 symmetric JWT** (no asymmetric rotation) and **no automated DAST/pentest**.
- **Microservices (9.4):** the **unified DB schema with cross-context FKs** stays — Database-per-context (ADR-0038's
  Option 4) was evaluated as premature/no-demand and its backlog card cancelled; reopen with a concrete driver.
- **GraalVM (9.2):** both native binaries are smoke-tested; the API smoke is still a **probed subset** of routes,
  not the full surface.
