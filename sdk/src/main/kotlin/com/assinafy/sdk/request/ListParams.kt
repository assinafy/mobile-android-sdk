package com.assinafy.sdk.request

data class ListParams(
    val page: Int? = null,
    val perPage: Int? = null,
    val search: String? = null,
    val sort: String? = null,
) {
    fun toQueryMap(): Map<String, Any?> = buildMap {
        page?.let { put("page", it) }
        perPage?.let { put("per-page", it) }
        search?.let { put("search", it) }
        sort?.let { put("sort", it) }
    }
}
