package com.assinafy.sdk.exceptions

import com.assinafy.sdk.oauth.OAuthChallenge
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Thrown when the API returns a non-2xx status (HTTP status or envelope `status`).
 *
 * @property statusCode the HTTP/envelope status code.
 * @property responseData the parsed error body (a `Map` when JSON), or the raw value when not
 *   parseable. It may contain request-derived or server-provided sensitive data and must be redacted
 *   before logging.
 * @property challenge the response's parsed `WWW-Authenticate: Bearer` challenge, or `null` when it
 *   carried none. A `403` whose challenge [OAuthChallenge.isInsufficientScope] means the OAuth token
 *   lacks [OAuthChallenge.scope]: connect the user again requesting it instead of retrying. A `403`
 *   without it means another workspace, the user's role, or an area OAuth tokens cannot reach.
 */
class ApiException private constructor(
    message: String,
    val statusCode: Int,
    val responseData: Any?,
    cause: Throwable?,
    val challenge: OAuthChallenge?,
) : AssinafyException(
    message,
    buildMap {
        put("statusCode", statusCode)
        responseData?.let { put("responseData", it) }
    },
    cause,
) {

    /**
     * Creates an exception without an authentication challenge.
     *
     * @param message Human-readable failure description.
     * @param statusCode HTTP or Assinafy envelope status.
     * @param responseData Parsed error body, raw value, or `null`.
     * @param cause Underlying failure, when there is one.
     */
    constructor(
        message: String,
        statusCode: Int,
        responseData: Any? = null,
        cause: Throwable? = null,
    ) : this(message, statusCode, responseData, cause, null)

    /** Factories for converting raw API error responses to typed exceptions. */
    companion object {
        private val GSON = Gson()

        /**
         * Builds an [ApiException] from a status code and a response body. [responseData] may be an
         * already-parsed `Map`, a raw JSON `String` (e.g. from a binary endpoint's error body), or
         * any other value. The human-readable `message`/`error` field is extracted when present.
         *
         * @param statusCode HTTP or Assinafy envelope status.
         * @param responseData Parsed response, raw JSON/text, or `null` for an empty response.
         * @return Exception with normalized message, status, response data, and structured context.
         */
        fun fromResponse(statusCode: Int, responseData: Any?): ApiException = fromResponse(statusCode, responseData, null)

        internal fun fromResponse(statusCode: Int, responseData: Any?, wwwAuthenticate: String?): ApiException {
            val data = responseData.asMapOrNull()
            val message = when {
                (data?.get("message") as? String)?.isNotBlank() == true -> data["message"] as String
                (data?.get("error") as? String)?.isNotBlank() == true -> data["error"] as String
                responseData is String && responseData.isNotBlank() -> responseData
                else -> "API request failed"
            }
            return ApiException(message, statusCode, data ?: responseData, null, OAuthChallenge.parse(wwwAuthenticate))
        }

        @Suppress("UNCHECKED_CAST")
        private fun Any?.asMapOrNull(): Map<String, Any>? = when (this) {
            is Map<*, *> -> this as Map<String, Any>
            is String -> if (isBlank()) {
                null
            } else {
                try {
                    GSON.fromJson<Map<String, Any>>(this, object : TypeToken<Map<String, Any>>() {}.type)
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
}
