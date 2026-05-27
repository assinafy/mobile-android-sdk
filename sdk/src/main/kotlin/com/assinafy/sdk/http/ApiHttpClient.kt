package com.assinafy.sdk.http

interface ApiHttpClient {
    suspend fun get(path: String, queryParams: Map<String, Any?> = emptyMap()): HttpRawResponse
    suspend fun post(path: String, jsonBody: String? = null): HttpRawResponse
    suspend fun postMultipart(path: String, fileName: String, fileData: ByteArray, name: String, metadata: String?): HttpRawResponse
    suspend fun put(path: String, jsonBody: String? = null): HttpRawResponse
    suspend fun delete(path: String): HttpRawResponse
    suspend fun getBinary(path: String): ByteArray

    /**
     * Uploads a signer signature/initial image as a raw binary body.
     *
     * Per the API contract the body is the raw image bytes with a `Content-Type` of
     * `image/png` or `image/jpeg` — not multipart form data.
     */
    suspend fun postSignature(path: String, imageData: ByteArray, contentType: String): HttpRawResponse
}
