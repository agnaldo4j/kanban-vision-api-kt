# API Versioning Policy

> Decision: [ADR-0022](../adr/ADR-0022-api-versioning-strategy.md) — URL-based versioning (v1/v2).

This document is the operational contract for how the public HTTP API evolves. It exists so that
both maintainers and consumers can reason about compatibility without reading the code: what is safe
to change inside a version, what forces a new one, and how a version is retired.

## Strategy — URL-based versioning

Business routes are versioned by URL prefix. This is a deliberate choice over header- or
media-type-based versioning: the version is visible in the path, trivially routable, cacheable, and
obvious in logs and Swagger's "Try it out" — at the cost of slightly noisier URLs, which is an
acceptable trade for a small, discoverable API.

| Version | Prefix | Status |
|---|---|---|
| v1 | `/api/v1` | **Active** (current) |
| v2 | `/api/v2` | Does not exist — introduced by a future ADR when a breaking change is required |

Every application response carries the header **`API-Version: 1.0`** (`plugins/VersioningHeaders.kt`).
The OpenAPI spec (`/api.json`, Swagger at `/swagger`) documents the full paths including the `/api/v1`
prefix; the spec's `server.url` is `/` so the prefix isn't duplicated in "Try it out"
(`plugins/OpenApi.kt`). The prefix itself is mounted in `plugins/Routing.kt`
(`authenticate("jwt-auth") { route("/api/v1") { … } }`).

### Routes intentionally outside the version contract

Infrastructure routes are not part of the business contract and are not versioned:

| Route | Purpose |
|---|---|
| `/health`, `/health/live`, `/health/ready` | Kubernetes probes |
| `/metrics` | Prometheus scraping |
| `/api.json`, `/swagger` | OpenAPI documentation |
| `/auth/token` | Dev only (`JWT_DEV_MODE=true`) |

## Compatibility rules — additive-only within v1

The contract for a live version is **additive-only**: existing consumers must never break. Concretely:

**Allowed within v1** (no new version needed):

- Add new endpoints.
- Add **optional** request fields (with a default) and new response fields.
- Add new **input** enum values *when the server handles them with a safe fallback*.
- Improve documentation, examples and error messages (without changing contracts).

**Requires v2** (breaking change — forbidden in v1):

- Remove or rename a field, endpoint or parameter.
- Change the type, format or semantics of an existing field.
- Make a previously optional field required.
- Change status codes or the error structure of an existing endpoint.
- Remove enum values returned in responses.

> **Why "response fields are additive but response enum values are not":** a consumer may ignore an
> unknown *field*, but a new enum *value* in a response can break a consumer that exhaustively
> switches on it. New response fields are safe; new response enum values are not (see also the
> serialization config — `ignoreUnknownKeys = true` protects *our* request parsing, not the client's).

### Worked example

Adding a `priority` field to `CreateSimulationRequest` is additive **iff** it is optional with a
default and the response shape is unchanged — it stays in v1. Changing `wipLimit` from an `Int` to an
object `{ soft, hard }` is a semantic/type change — it requires v2.

## Lifecycle — 12 months per version

1. **Active** — v1 is current; receives additive-only changes.
2. **Deprecated** — when v2 ships, v1 begins deprecation and starts emitting
   `Deprecation: true`, `Sunset: <HTTP-date>` (RFC 8594) and `Link: <…>; rel="successor-version"`.
   These headers are reserved in `plugins/VersioningHeaders.kt` and activated when v2 exists.
3. **Supported** — v1 stays functional for **12 months** after v2 ships (security and critical bug
   fixes only; no new features).
4. **Removed** — announced **90 days** in advance via changelog and the `Sunset` header.

## Introducing v2 (checklist for the future ADR)

- A new ADR with the breaking-change rationale and a migration plan.
- A new `route("/api/v2")` block coexisting with v1; per-version OpenAPI specs (`/api.json` for v1
  and a named v2 spec — ktor-openapi 5.x supports named specs; validate at implementation time).
- Affected v1 endpoints marked `deprecated = true` in the spec.
- Activate the deprecation headers on v1 and start the 12-month support window.

## References

- [ADR-0022](../adr/ADR-0022-api-versioning-strategy.md) · [Wiki → API Reference](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/API-Reference) · [Wiki → Architecture HTTP API](https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/Architecture-HTTP-API)
- Code: `http_api/…/plugins/VersioningHeaders.kt`, `plugins/OpenApi.kt`, `plugins/Routing.kt`
