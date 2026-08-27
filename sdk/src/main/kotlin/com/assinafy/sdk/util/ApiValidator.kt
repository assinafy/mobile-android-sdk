package com.assinafy.sdk.util

import com.assinafy.sdk.exceptions.ValidationException

internal object ApiValidator {
    private val verificationMethods = setOf("Email", "Whatsapp", "DigitalCertificate")
    private val notificationMethods = setOf("Email", "Whatsapp")

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

    /**
     * Validates the API's signer verification/notification coupling rules. A supplied
     * [notifications] list must hold exactly one method, and `Email`/`Whatsapp` verification must be
     * paired with the same notification channel; `DigitalCertificate` accepts either. Omitting a
     * side leaves it for the API to infer, and omitting both defaults to `Email`.
     */
    fun requireValidSignerChannels(verification: String?, notifications: List<String>?) {
        if (verification != null && verification !in verificationMethods) {
            throw ValidationException("Unsupported verification method: $verification")
        }
        if (notifications == null) return
        if (notifications.size != 1 || notifications.single() !in notificationMethods) {
            throw ValidationException("Exactly one notification method (Email or Whatsapp) is required")
        }
        if (verification != null && verification != "DigitalCertificate" && verification !in notifications) {
            throw ValidationException("Verification and notification methods must match")
        }
    }

    /** Requires every digital-certificate signer to be the only signer in its signing step. */
    fun requireDigitalCertificateStepIsolation(signers: List<Pair<String?, Int?>>) {
        val stepCounts = signers.groupingBy { it.second ?: 1 }.eachCount()
        if (signers.any { (verification, step) ->
                verification == "DigitalCertificate" && stepCounts.getValue(step ?: 1) > 1
            }
        ) {
            throw ValidationException("A DigitalCertificate signer must be alone in its signing step")
        }
    }
}
