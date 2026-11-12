package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.EmbedOrigin
import com.assinafy.sdk.models.EmbedSession
import java.net.URI

/** Allowed embed origins and single-use signing sessions; account credentials stay on a trusted backend. */
class EmbeddedSigningResource internal constructor(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {
    /**
     * List embed origins. Requires the embedded-signing plan feature; origin management is unavailable to OAuth apps.
     *
     * Wire operation: `GET /v1/accounts/{accountId}/embed-origins`.
     *
     * Request body: none.
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * [
     *   {
     *     "id": "id-placeholder",
     *     "origin": "https://app.example.com",
     *     "created_at": "2026-11-04T12:05:00Z"
     *   }
     * ]
     * ```
     */
    suspend fun listOrigins(accountId: String? = null): List<EmbedOrigin> {
        val path = originsPath(accountId)
        return callList("Failed to list embed origins", EmbedOrigin::class.java) { http.get(path) }.data
    }

    /**
     * Add embed origin. Exact HTTPS origin, or HTTP localhost/127.0.0.1; at most ten origins. Unavailable to OAuth apps.
     *
     * Wire operation: `POST /v1/accounts/{accountId}/embed-origins`.
     *
     * Request body (`application/json`; optional fields may be omitted):
     * ```json
     * {
     *   "origin": "https://app.example.com"
     * }
     * ```
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * {
     *   "id": "id-placeholder",
     *   "origin": "https://app.example.com",
     *   "created_at": "2026-11-04T12:05:00Z"
     * }
     * ```
     */
    suspend fun addOrigin(origin: String, accountId: String? = null): EmbedOrigin {
        val path = originsPath(accountId)
        val normalized = validateOrigin(origin)
        return call("Failed to save embed origin", EmbedOrigin::class.java) { http.post(path, toJson(mapOf("origin" to normalized))) }
    }

    /**
     * Update embed origin. Exact HTTPS origin, or HTTP localhost/127.0.0.1; at most ten origins. Unavailable to OAuth apps.
     *
     * Wire operation: `PUT /v1/accounts/{accountId}/embed-origins/{originId}`.
     *
     * Request body (`application/json`; optional fields may be omitted):
     * ```json
     * {
     *   "origin": "https://app.example.com"
     * }
     * ```
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * {
     *   "id": "id-placeholder",
     *   "origin": "https://app.example.com",
     *   "created_at": "2026-11-04T12:05:00Z"
     * }
     * ```
     */
    suspend fun updateOrigin(originId: String, origin: String, accountId: String? = null): EmbedOrigin {
        val path = originsPath(accountId) + "/${pathSegment(requireId(originId, "Origin ID"))}"
        val normalized = validateOrigin(origin)
        return call("Failed to save embed origin", EmbedOrigin::class.java) { http.put(path, toJson(mapOf("origin" to normalized))) }
    }

    /**
     * Remove embed origin. Removing the last origin prevents new embed sessions.
     *
     * Wire operation: `DELETE /v1/accounts/{accountId}/embed-origins/{originId}`.
     *
     * Request body: none.
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * []
     * ```
     */
    suspend fun deleteOrigin(originId: String, accountId: String? = null) {
        val path = originsPath(accountId) + "/${pathSegment(requireId(originId, "Origin ID"))}"
        callVoid("Failed to remove embed origin") { http.delete(path) }
    }

    /**
     * Create an embed session. Requires an embedded signer. Load once within five minutes; do not prefetch or log the URL. Each reopening needs a new session.
     *
     * Wire operation: `POST /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/embed-sessions`.
     *
     * Request body: none.
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * {
     *   "url": "https://example.com/resource",
     *   "expires_at": "2026-11-04T12:05:00Z"
     * }
     * ```
     */
    suspend fun createSession(documentId: String, assignmentId: String, signerId: String): EmbedSession {
        val path = "/documents/${pathSegment(requireId(documentId, "Document ID"))}/assignments/" +
            "${pathSegment(requireId(assignmentId, "Assignment ID"))}/signers/${pathSegment(requireId(signerId, "Signer ID"))}/embed-sessions"
        return call("Failed to create embed session", EmbedSession::class.java) { http.post(path) }
    }

    private fun originsPath(accountId: String?): String = "/accounts/${pathSegment(accountId(accountId))}/embed-origins"

    private fun validateOrigin(value: String): String {
        val origin = requireId(value, "Embed origin")
        val uri = try {
            URI(origin)
        } catch (_: Exception) {
            throw ValidationException("Invalid embed origin")
        }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (host.isNullOrBlank() ||
            scheme != "https" &&
            !(scheme == "http" && host in setOf("localhost", "127.0.0.1")) ||
            uri.rawPath !in listOf("", "/") ||
            uri.rawQuery != null ||
            uri.rawFragment != null ||
            uri.rawUserInfo != null ||
            uri.port != -1 &&
            uri.port !in 1..65535
        ) {
            throw ValidationException("Embed origin must be an exact HTTPS origin, or HTTP localhost/127.0.0.1")
        }
        return origin
    }
}
