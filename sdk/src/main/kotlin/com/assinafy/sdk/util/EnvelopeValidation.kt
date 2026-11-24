package com.assinafy.sdk.util

import com.assinafy.sdk.AssignmentMethod
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.request.CreateAssignmentRequest
import com.assinafy.sdk.request.CreateEnvelopeRequest
import com.assinafy.sdk.request.EnvelopeDocumentRequest
import com.assinafy.sdk.request.SignerReference

internal object EnvelopeValidation {
    fun envelopeName(value: String): String =
        ApiValidator.requireNonBlank(value, "Envelope name").also {
            if (it.codePointCount(0, it.length) > 255 ||
                '<' in it
            ) {
                throw ValidationException("Envelope name must be at most 255 characters without <")
            }
        }

    fun distinctIds(
        values: List<String>,
        label: String,
    ): List<String> {
        val ids = values.map { ApiValidator.requireNonBlank(it, label) }
        if (ids.isEmpty() || ids.distinct().size != ids.size) throw ValidationException("$label list must be nonempty and distinct")
        return ids
    }

    fun normalise(request: CreateEnvelopeRequest): Map<String, Any?> {
        val name = envelopeName(request.name)
        ApiValidator.requireCreditCeiling(request.maxTotalCredits)
        ApiValidator.requireIdempotencyKey(request.idempotencyKey)
        if (request.signers.size !in 1..20 ||
            request.documents.size !in 1..20
        ) {
            throw ValidationException("An envelope requires 1..20 signers and documents")
        }
        val ids = distinctIds(request.signers.map { ApiValidator.requireNonBlank(it.id, "Signer ID") }, "Signer ID")
        distinctIds(request.documents.map { it.id }, "Document ID")
        if (request.signers.any { it.signAs != null }) throw ValidationException("Signer capacities are supported only on assignments")
        if (request.signers.any { it.step != null }) throw ValidationException("Envelope signers cannot have signing steps")
        val signers =
            request.signers.map { signer ->
                ApiValidator.requireValidSignerChannels(signer.verificationMethod, signer.notificationMethods)
                ApiValidator.requireEmbeddedMethod(signer.embedded, signer.verificationMethod)
                signer.copy(id = ApiValidator.requireNonBlank(signer.id, "Signer ID"))
            }
        val usedIds =
            request.documents
                .flatMap { it.signerIds }
                .map(String::trim)
                .toSet()
        if (usedIds !=
            ids.toSet()
        ) {
            throw ValidationException(
                "Every envelope signer must belong to a document and every document signer must belong to the envelope",
            )
        }
        return mapOf(
            "name" to name,
            "message" to request.message,
            "signers" to signers,
            "documents" to request.documents.map { documentBody(it, signers) },
            "max_total_credits" to request.maxTotalCredits,
        )
    }

    fun documentBody(
        document: EnvelopeDocumentRequest,
        signers: List<SignerReference>,
    ): Map<String, Any?> {
        val id = ApiValidator.requireNonBlank(document.id, "Document ID")
        val ids = distinctIds(document.signerIds, "Signer ID")
        val method = ApiValidator.requireNonBlank(document.method, "Assignment method")
        val request =
            CreateAssignmentRequest(method = method, signers = signers.filter { it.id?.trim() in ids }, entries = document.entries)
        AssignmentValidation.validateRequest(request, estimate = false)
        if (method == AssignmentMethod.COLLECT &&
            document.entries
                .orEmpty()
                .flatMap { it.fields }
                .any { it.displaySettings == null }
        ) {
            throw ValidationException("Envelope collect fields require display settings")
        }
        return mapOf(
            "id" to id,
            "signer_ids" to ids,
            "method" to method,
            "entries" to AssignmentValidation.normalise(request)["entries"],
            "expires_at" to document.expiresAt,
            "copy_receiver_ids" to document.copyReceiverIds?.map { ApiValidator.requireNonBlank(it, "Copy receiver ID") },
        )
    }
}
