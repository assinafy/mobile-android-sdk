package com.assinafy.sdk.exceptions

class ValidationException(
    message: String = "Validation failed",
    val errors: Map<String, Any> = emptyMap(),
) : AssinafyException(message, mapOf("errors" to errors))
