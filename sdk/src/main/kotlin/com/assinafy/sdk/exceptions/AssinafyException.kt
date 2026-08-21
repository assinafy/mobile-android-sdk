package com.assinafy.sdk.exceptions

/**
 * Base runtime exception for SDK validation, transport, parsing, and API failures.
 *
 * @param message Human-readable failure description.
 * @property context Structured diagnostic values. API failures may include server-provided data;
 *   redact it before logging or reporting it outside the process.
 * @param cause Underlying failure, when one exists.
 */
open class AssinafyException(
    message: String,
    val context: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : RuntimeException(message, cause)
