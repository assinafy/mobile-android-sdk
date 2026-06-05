package com.assinafy.sdk.http

/**
 * Low-level transport abstraction over the Assinafy API. The default implementation is
 * [OkHttpApiClient]; tests provide their own. All methods are `suspend` and run off the main thread.
 * Paths are relative to the configured base URL; query values are URL-encoded by the implementation.
 */
interface ApiHttpClient {
    /** GET [path] with optional [queryParams] (null values are dropped). */
    suspend fun get(path: String, queryParams: Map<String, Any?> = emptyMap()): HttpRawResponse

    /** POST [path] with an optional JSON [jsonBody] (defaults to an empty `{}` object). */
    suspend fun post(path: String, jsonBody: String? = null): HttpRawResponse

    /** POST a multipart upload (`file` + `name` + optional `metadata`) to [path]. */
    suspend fun postMultipart(path: String, fileName: String, fileData: ByteArray, name: String, metadata: String?): HttpRawResponse

    /** PUT [path] with an optional JSON [jsonBody] (defaults to an empty `{}` object). */
    suspend fun put(path: String, jsonBody: String? = null): HttpRawResponse

    /** DELETE [path]. */
    suspend fun delete(path: String): HttpRawResponse

    /** GET [path] returning the raw response bytes (for downloads/thumbnails). */
    suspend fun getBinary(path: String): ByteArray

    /**
     * Uploads a signer signature/initial image as a raw binary body.
     *
     * Per the API contract the body is the raw image bytes with a `Content-Type` of
     * `image/png` or `image/jpeg` — not multipart form data.
     */
    suspend fun postSignature(path: String, imageData: ByteArray, contentType: String): HttpRawResponse
}
