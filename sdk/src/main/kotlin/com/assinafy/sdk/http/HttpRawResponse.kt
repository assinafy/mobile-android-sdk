package com.assinafy.sdk.http

data class HttpRawResponse(
    val statusCode: Int,
    val body: String?,
    val headers: Map<String, String>,
)
