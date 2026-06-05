package com.assinafy.sdk.exceptions

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Thrown when the API returns a non-2xx status (HTTP status or envelope `status`).
 *
 * @property statusCode the HTTP/envelope status code.
 * @property responseData the parsed error body (a `Map` when JSON), or the raw value when not parseable.
 */
class ApiException(
    message: String,
    val statusCode: Int,
    val responseData: Any? = null,
    cause: Throwable? = null,
) : AssinafyException(
    message,
    buildMap {
        put("statusCode", statusCode)
        responseData?.let { put("responseData", it) }
    },
    cause,
) {

    companion object {
        private val GSON = Gson()

        /**
         * Builds an [ApiException] from a status code and a response body. [responseData] may be an
         * already-parsed `Map`, a raw JSON `String` (e.g. from a binary endpoint's error body), or
         * any other value. The human-readable `message`/`error` field is extracted when present.
         */
        fun fromResponse(statusCode: Int, responseData: Any?): ApiException {
            val data = responseData.asMapOrNull()
            val message = when {
                (data?.get("message") as? String)?.isNotBlank() == true -> data["message"] as String
                (data?.get("error") as? String)?.isNotBlank() == true -> data["error"] as String
                responseData is String && responseData.isNotBlank() -> responseData
                else -> "API request failed"
            }
            return ApiException(message, statusCode, data ?: responseData)
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
