package com.assinafy.sdk.models

/**
 * Pagination metadata for a list response. Populated from the `X-Pagination-*` response headers
 * (not the JSON body), so these fields are plain Kotlin properties with no Gson annotations.
 */
data class PaginationMeta(
    val currentPage: Int? = null,
    val lastPage: Int? = null,
    val perPage: Int? = null,
    val total: Int? = null,
)

/** A page of [data] plus optional pagination [meta] (from the `X-Pagination-*` headers). */
data class PaginatedResult<T>(
    val data: List<T>,
    val meta: PaginationMeta? = null,
)
