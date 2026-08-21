package com.assinafy.sdk.exceptions

/**
 * Thrown before a request when caller input violates an SDK or API constraint.
 *
 * @param message Human-readable validation failure.
 * @property errors Structured field or index details associated with the failure.
 */
class ValidationException(
    message: String = "Validation failed",
    val errors: Map<String, Any> = emptyMap(),
) : AssinafyException(message, mapOf("errors" to errors))
