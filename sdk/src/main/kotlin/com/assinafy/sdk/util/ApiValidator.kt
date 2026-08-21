package com.assinafy.sdk.util

import com.assinafy.sdk.exceptions.ValidationException

internal object ApiValidator {
    fun requireNonBlank(value: String?, name: String): String {
        if (value.isNullOrBlank()) {
            throw ValidationException("$name is required")
        }
        return value.trim()
    }

    fun requireAccountId(explicit: String?, default: String?): String {
        val id = explicit ?: default
        if (id.isNullOrBlank()) {
            throw ValidationException(
                "Account ID is required. Provide it as a parameter or set a default in the client.",
            )
        }
        return id.trim()
    }

    fun requireAtLeastOne(items: List<*>, name: String) {
        if (items.isEmpty()) {
            throw ValidationException("At least one $name is required")
        }
    }

    /** Validates the API's optional, contiguous, one-based sequential-signing steps. */
    fun requireValidSigningSteps(steps: List<Int?>) {
        if (steps.all { it == null }) return
        if (steps.any { it == null }) {
            throw ValidationException("Signing step is required for every signer when any step is supplied")
        }
        val values = steps.filterNotNull()
        val distinct = values.distinct().sorted()
        if (distinct.withIndex().any { (index, value) -> value != index + 1 }) {
            throw ValidationException("Signing steps must form a contiguous sequence starting at 1")
        }
    }
}
