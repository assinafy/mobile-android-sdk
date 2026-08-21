package com.assinafy.sdk.models

/**
 * Pagination metadata for a list response. Populated from the `X-Pagination-*` response headers
 * (not the JSON body), so these fields are plain Kotlin properties with no Gson annotations.
 *
 * @property currentPage Current one-based page.
 * @property lastPage Final available one-based page.
 * @property perPage Maximum records requested per page.
 * @property total Total matching records across all pages.
 */
data class PaginationMeta(
    val currentPage: Int? = null,
    val lastPage: Int? = null,
    val perPage: Int? = null,
    val total: Int? = null,
)

/**
 * A page of API list [data] plus optional pagination [meta].
 *
 * @property data Typed records returned in the response's `data` array.
 * @property meta Values parsed from `X-Pagination-*` headers, or `null` when absent.
 */
data class PaginatedResult<T>(
    val data: List<T>,
    val meta: PaginationMeta? = null,
)
