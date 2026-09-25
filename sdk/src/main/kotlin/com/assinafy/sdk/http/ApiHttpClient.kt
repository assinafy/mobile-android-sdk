package com.assinafy.sdk.http

/**
 * Low-level transport abstraction over the Assinafy API. The default implementation is
 * [OkHttpApiClient]; tests provide their own. All methods are `suspend`; the default implementation
 * uses asynchronous OkHttp calls. Paths are relative to the configured base URL; query values are
 * URL-encoded by the implementation.
 */
interface ApiHttpClient {
    /** GET [path] with optional [queryParams] (null values are dropped). */
    suspend fun get(path: String, queryParams: Map<String, Any?> = emptyMap()): HttpRawResponse

    /** POST [path] with an optional JSON [jsonBody] (a null value sends an empty body). */
    suspend fun post(path: String, jsonBody: String? = null): HttpRawResponse

    /**
     * POST [fields] to [path] as `application/x-www-form-urlencoded`, the encoding of the OAuth token
     * and revocation endpoints.
     *
     * The request is sent at most once and never replayed — not even transparently after a dropped
     * connection — because repeating a refresh-token grant reuses a refresh token the first attempt
     * may already have rotated, which ends the user's whole OAuth connection.
     *
     * The default implementation sends nothing and throws [UnsupportedOperationException], so an
     * implementation written before this member existed still compiles and links. It does not fall
     * back to [post], which promises neither form encoding nor a single transmission.
     */
    suspend fun postForm(path: String, fields: Map<String, String>): HttpRawResponse =
        throw UnsupportedOperationException(
            "${javaClass.name} does not implement ApiHttpClient.postForm, which OAuth token and revocation " +
                "requests need. Override it to POST an application/x-www-form-urlencoded body exactly once, " +
                "never retransmitted (with OkHttp, a RequestBody whose isOneShot() returns true).",
        )

    /** POST a multipart upload (`file` + `name` + optional `metadata`) to [path]. */
    suspend fun postMultipart(path: String, fileName: String, fileData: ByteArray, name: String, metadata: String?): HttpRawResponse

    /** POST one multipart binary [fileData] part to [path]. */
    suspend fun postMultipartFile(
        path: String,
        fieldName: String,
        fileName: String,
        fileData: ByteArray,
        contentType: String,
    ): HttpRawResponse

    /** PUT [path] with an optional JSON [jsonBody] (a null value sends an empty body). */
    suspend fun put(path: String, jsonBody: String? = null): HttpRawResponse

    /** PATCH [path] with an optional JSON [jsonBody] (a null value sends an empty body). */
    suspend fun patch(path: String, jsonBody: String? = null): HttpRawResponse

    /** DELETE [path], optionally with a JSON [jsonBody]. */
    suspend fun delete(path: String, jsonBody: String? = null): HttpRawResponse

    /** GET [path] returning the raw response bytes (for downloads/thumbnails). */
    suspend fun getBinary(path: String): ByteArray

    /**
     * Uploads a signer signature/initial image as a raw binary body.
     *
     * Per the API contract the body is the raw image bytes with a `Content-Type` of
     * `image/png` or `image/jpeg` — not multipart form data.
     */
    suspend fun postSignature(path: String, imageData: ByteArray, contentType: String): HttpRawResponse

    /**
     * GET an absolute [url], which may point at another origin than the configured base URL.
     *
     * Used only for OAuth discovery documents, which are published at the API host's root
     * (`/.well-known/oauth-protected-resource`) and on the authorization server. The default
     * transport attaches credentials to same-origin requests only, so a cross-origin discovery
     * fetch never carries an API key or bearer token.
     */
    suspend fun getAbsolute(url: String): HttpRawResponse
}
