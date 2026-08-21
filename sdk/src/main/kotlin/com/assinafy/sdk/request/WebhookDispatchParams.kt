package com.assinafy.sdk.request

import com.assinafy.sdk.exceptions.ValidationException

/**
 * Filters accepted by `GET /accounts/{accountId}/webhooks`.
 *
 * @property event Exact event identifier to include.
 * @property delivered Whether to include successful or failed delivery attempts.
 * @property from Inclusive minimum Unix timestamp.
 * @property to Inclusive maximum Unix timestamp.
 * @property page One-based results page.
 * @property perPage Records per page from 1 through 100, sent as `per-page`.
 */
data class WebhookDispatchParams(
    val event: String? = null,
    val delivered: Boolean? = null,
    val from: Long? = null,
    val to: Long? = null,
    val page: Int? = null,
    val perPage: Int? = null,
) {
    internal fun toQueryMap(): Map<String, Any?> {
        if (from != null && from < 0 || to != null && to < 0 || from != null && to != null && from > to) {
            throw ValidationException("Invalid webhook dispatch timestamp range")
        }
        if (page != null && page <= 0) throw ValidationException("Webhook dispatch page must be positive")
        if (perPage != null && perPage !in 1..100) {
            throw ValidationException("Webhook dispatch per-page must be between 1 and 100")
        }
        return buildMap {
            event?.trim()?.takeIf(String::isNotEmpty)?.let { put("event", it) }
            delivered?.let { put("delivered", it) }
            from?.let { put("from", it) }
            to?.let { put("to", it) }
            page?.let { put("page", it) }
            perPage?.let { put("per-page", it) }
        }
    }
}
