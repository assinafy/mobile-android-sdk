package com.assinafy.sdk.http

/**
 * Transport-neutral HTTP response consumed by resource response handlers.
 *
 * @property statusCode Numeric HTTP response status.
 * @property body UTF-8 response body, or `null` when the response has no body.
 * @property headers Response headers keyed case-insensitively by the default transport.
 */
data class HttpRawResponse(
    val statusCode: Int,
    val body: String?,
    val headers: Map<String, String>,
)
