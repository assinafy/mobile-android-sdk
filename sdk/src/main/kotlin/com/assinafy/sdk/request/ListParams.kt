package com.assinafy.sdk.request

import com.assinafy.sdk.exceptions.ValidationException

/**
 * Common query parameters for list endpoints.
 *
 * @property page 1-based page number.
 * @property perPage Records per page (max 100). Sent as the hyphenated `per-page` key the API expects.
 * @property search Partial-match search term.
 * @property sort Sort expression, e.g. `"name"` or `"-updated_at"`.
 * @property status Status filter (documents and templates list).
 * @property method Signature-method filter, `"virtual"` or `"collect"` (documents list).
 * @property tags Tag IDs to filter by; serialized as a comma-separated `tags` value (AND semantics).
 */
data class ListParams(
    val page: Int? = null,
    val perPage: Int? = null,
    val search: String? = null,
    val sort: String? = null,
    val status: String? = null,
    val method: String? = null,
    val tags: List<String>? = null,
) {
    init {
        if (page != null && page < 1) throw ValidationException("Page must be at least 1")
        if (perPage != null && perPage !in 1..100) {
            throw ValidationException("Per-page must be between 1 and 100")
        }
    }

    /**
     * Converts populated properties to the API's exact query keys, omitting null and empty values.
     *
     * @return Query map suitable for [com.assinafy.sdk.http.ApiHttpClient.get].
     */
    fun toQueryMap(): Map<String, Any?> = buildMap {
        page?.let { put("page", it) }
        perPage?.let { put("per-page", it) }
        search?.let { put("search", it) }
        sort?.let { put("sort", it) }
        status?.let { put("status", it) }
        method?.let { put("method", it) }
        tags?.takeIf { it.isNotEmpty() }?.let { put("tags", it.joinToString(",")) }
    }
}
