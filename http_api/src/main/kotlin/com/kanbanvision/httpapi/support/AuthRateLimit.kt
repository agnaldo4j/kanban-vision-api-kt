package com.kanbanvision.httpapi.support

import io.ktor.server.plugins.ratelimit.RateLimitName

/**
 * Named limiter for the `/auth` routes — stricter than the global limit (OWASP A07, security.md).
 *
 * Lives in the neutral `support` package so both the `RateLimit` plugin (which registers it) and
 * the `routes` that apply it can reference it without a package cycle (enforced by `PackageCycleTest`).
 */
val AUTH_RATE_LIMIT_NAME = RateLimitName("auth")
