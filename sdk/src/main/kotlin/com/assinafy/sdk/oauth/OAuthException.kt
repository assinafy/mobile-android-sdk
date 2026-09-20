package com.assinafy.sdk.oauth

import com.assinafy.sdk.exceptions.AssinafyException

/**
 * Thrown when an OAuth endpoint or an authorization redirect reports a failure.
 *
 * OAuth responses are flat RFC 6749 §5.2 objects (`{"error": "...", "error_description": "..."}`)
 * rather than this API's `{status, message, data}` envelope, so these failures carry the standard
 * [error] code instead of an envelope message.
 *
 * @property error RFC 6749 error code, such as `invalid_grant`, `invalid_client`, `access_denied`
 *   or `invalid_target`.
 * @property errorDescription Server-supplied detail, when present.
 * @property statusCode HTTP status, or `null` for an error delivered on the redirect URI.
 */
class OAuthException(
    val error: String,
    val errorDescription: String? = null,
    val statusCode: Int? = null,
) : AssinafyException(
    errorDescription?.takeIf { it.isNotBlank() }?.let { "$error: $it" } ?: error,
    buildMap {
        put("error", error)
        errorDescription?.let { put("errorDescription", it) }
        statusCode?.let { put("statusCode", it) }
    },
) {
    /** Whether the user declined the connection on the approval screen. */
    val isAccessDenied: Boolean get() = error == ACCESS_DENIED

    /**
     * Whether the grant is unusable and the user must approve the application again: an expired,
     * replayed or wrong-client authorization code, a mismatched `code_verifier` or `redirect_uri`,
     * or a refresh token that was already used, expired, or superseded by a new approval.
     */
    val isInvalidGrant: Boolean get() = error == INVALID_GRANT

    /** Standard OAuth error codes returned by the Assinafy authorization server. */
    companion object {
        /** The user declined on the approval screen. */
        const val ACCESS_DENIED = "access_denied"

        /** The code or refresh token is expired, already used, or no longer authorized. */
        const val INVALID_GRANT = "invalid_grant"

        /** Unknown or disabled client, or failed client authentication. */
        const val INVALID_CLIENT = "invalid_client"

        /** The `resource` indicator does not match the one that was authorized. */
        const val INVALID_TARGET = "invalid_target"

        /** A grant type other than `authorization_code` or `refresh_token` was requested. */
        const val UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type"

        /** Missing or malformed request parameters, including PKCE values. */
        const val INVALID_REQUEST = "invalid_request"

        /** A scope the application is not registered for, or an empty scope. */
        const val INVALID_SCOPE = "invalid_scope"
    }
}
