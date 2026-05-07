package com.assinafy.sdk.exceptions

class ApiException(
    message: String,
    val statusCode: Int,
    val responseData: Any? = null,
    cause: Throwable? = null,
) : AssinafyException(message, mapOf("statusCode" to statusCode, "responseData" to (responseData ?: "")), cause) {

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromResponse(statusCode: Int, responseData: Any?): ApiException {
            val data = responseData as? Map<String, Any> ?: emptyMap()
            val message = when {
                data["message"] is String && (data["message"] as String).isNotEmpty() -> data["message"] as String
                data["error"] is String -> data["error"] as String
                else -> "API request failed"
            }
            return ApiException(message, statusCode, responseData)
        }
    }
}
