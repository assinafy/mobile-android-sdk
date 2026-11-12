package com.assinafy.sdk.util

import com.assinafy.sdk.AssignmentMethod
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.request.CreateAssignmentRequest
import com.assinafy.sdk.request.SignerReference

internal object AssignmentValidation {
    fun normalise(
        request: CreateAssignmentRequest,
        estimate: Boolean = false,
    ): Map<String, Any?> {
        val signers = request.signers.map { ref -> normaliseRef(ref, estimate) }
        return buildMap {
            put("method", ApiValidator.requireNonBlank(request.method, "Assignment method"))
            put("signers", signers)
            request.entries?.let { entries ->
                put(
                    "entries",
                    entries.map { entry ->
                        entry.copy(
                            pageId = entry.pageId.trim(),
                            fields = entry.fields.map { field ->
                                field.copy(signerId = field.signerId.trim(), fieldId = field.fieldId.trim())
                            },
                        )
                    },
                )
            }
            if (!estimate) {
                request.maxTotalCredits?.let { put("max_total_credits", it) }
                request.message?.let { put("message", it) }
                request.expiresAt?.let { put("expires_at", it.trim()) }
                request.copyReceivers?.let { put("copy_receivers", it.map { id -> ApiValidator.requireNonBlank(id, "Copy receiver signer ID") }) }
            }
        }
    }

    private fun normaliseRef(ref: SignerReference, estimate: Boolean): Map<String, Any?> {
        if (!estimate) ApiValidator.requireNonBlank(ref.id, "Signer ID")
        return buildMap {
            if (!estimate) ref.id?.trim()?.let { put("id", it) }
            ref.verificationMethod?.let { put("verification_method", it) }
            ref.notificationMethods?.let { put("notification_methods", it) }
            if (!estimate) ref.step?.let { put("step", it) }
            if (!estimate) ref.embedded?.let { put("embedded", it) }
        }
    }

    fun validateRequest(request: CreateAssignmentRequest, estimate: Boolean) {
        ApiValidator.requireCreditCeiling(request.maxTotalCredits)
        val method = ApiValidator.requireNonBlank(request.method, "Assignment method")
        if (method !in setOf(AssignmentMethod.VIRTUAL, AssignmentMethod.COLLECT)) throw ValidationException("Assignment method must be virtual or collect")
        // Required for both methods: creation needs to know who signs, and an estimate is priced
        // per signer. The contract marks `signers` required only for `virtual`, but the API
        // answers a signer-less body with 400 "Pelo menos um signatários precisa ser informado."
        if (request.signers.isEmpty()) {
            throw ValidationException("At least one signer is required")
        }
        ApiValidator.requireDigitalCertificateVirtualAssignment(
            method,
            request.signers.map(SignerReference::verificationMethod),
        )
        if (method == AssignmentMethod.COLLECT && request.entries.isNullOrEmpty()) {
            throw ValidationException("At least one field-placement entry is required for a collect assignment")
        }
        request.signers.forEach { signer ->
            ApiValidator.requireValidSignerChannels(signer.verificationMethod, signer.notificationMethods)
            ApiValidator.requireEmbeddedMethod(signer.embedded, signer.verificationMethod)
        }
        if (!estimate) {
            val ids = request.signers.map { ApiValidator.requireNonBlank(it.id, "Signer ID") }
            if (ids.distinct().size != ids.size) throw ValidationException("Every assignment signer must appear once")
            ApiValidator.requireValidSigningSteps(request.signers.map(SignerReference::step))
            ApiValidator.requireDigitalCertificateStepIsolation(
                request.signers.map { it.verificationMethod to it.step },
            )
            if (method == AssignmentMethod.COLLECT) {
                val signerIds = request.signers.map { ApiValidator.requireNonBlank(it.id, "Signer ID") }.toSet()
                val fieldSignerIds = request.entries.orEmpty().flatMap { it.fields }.map { ApiValidator.requireNonBlank(it.signerId, "Assignment field signer ID") }.toSet()
                if (fieldSignerIds != signerIds) {
                    throw ValidationException("Every collect assignment signer must own at least one field")
                }
            }
            request.entries.orEmpty().forEach { entry ->
                ApiValidator.requireNonBlank(entry.pageId, "Assignment page ID")
                ApiValidator.requireAtLeastOne(entry.fields, "assignment field")
                entry.fields.forEach { field ->
                    ApiValidator.requireNonBlank(field.signerId, "Assignment field signer ID")
                    ApiValidator.requireNonBlank(field.fieldId, "Assignment field ID")
                    field.displaySettings?.let { settings ->
                        if (listOf(settings.left, settings.top, settings.width, settings.height, settings.fontSize).any { !it.isFinite() } ||
                            settings.left < 0 ||
                            settings.top < 0 ||
                            settings.width <= 0 ||
                            settings.height <= 0 ||
                            settings.fontSize <= 0
                        ) {
                            throw ValidationException("Invalid assignment field display settings")
                        }
                    }
                }
            }
        }
    }
}
