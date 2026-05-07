package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

data class PaginationMeta(
    @SerializedName("current_page") val currentPage: Int? = null,
    @SerializedName("last_page") val lastPage: Int? = null,
    @SerializedName("per_page") val perPage: Int? = null,
    @SerializedName("total") val total: Int? = null,
)

data class PaginatedResult<T>(
    val data: List<T>,
    val meta: PaginationMeta? = null,
)
