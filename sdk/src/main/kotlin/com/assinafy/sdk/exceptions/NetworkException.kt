package com.assinafy.sdk.exceptions

/**
 * Wraps an I/O failure that prevented an API response from being received.
 *
 * @param message Operation-specific network error description.
 * @param cause Underlying I/O or transport failure.
 */
class NetworkException(message: String, cause: Throwable? = null) : AssinafyException(message, emptyMap(), cause)
