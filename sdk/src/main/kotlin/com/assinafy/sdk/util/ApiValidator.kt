package com.assinafy.sdk.util

import com.assinafy.sdk.exceptions.ValidationException

object ApiValidator {
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
}
