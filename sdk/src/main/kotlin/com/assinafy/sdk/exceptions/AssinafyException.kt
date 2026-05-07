package com.assinafy.sdk.exceptions

open class AssinafyException(
    message: String,
    val context: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : RuntimeException(message, cause)
