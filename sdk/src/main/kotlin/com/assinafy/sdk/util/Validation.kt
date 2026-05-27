package com.assinafy.sdk.util

import com.assinafy.sdk.exceptions.ValidationException

private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", RegexOption.IGNORE_CASE)

internal fun requireValidEmail(email: String, fieldName: String = "Email"): String {
    val normalized = email.trim()
    if (normalized.isBlank() || !EMAIL_REGEX.matches(normalized)) {
        throw ValidationException("Invalid email address", mapOf(fieldName to email))
    }
    return normalized
}
