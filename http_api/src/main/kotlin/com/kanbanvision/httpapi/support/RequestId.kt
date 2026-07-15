package com.kanbanvision.httpapi.support

import io.ktor.util.AttributeKey

/**
 * Correlation-id attribute key propagated per request (set by the Observability plugin, read by
 * authentication, error handling and response adapters).
 *
 * Lives in the neutral `support` package so both `plugins` and `adapters`/`routes` can reference it
 * without a package cycle (enforced by `PackageCycleTest`).
 */
val REQUEST_ID_KEY = AttributeKey<String>("RequestId")
