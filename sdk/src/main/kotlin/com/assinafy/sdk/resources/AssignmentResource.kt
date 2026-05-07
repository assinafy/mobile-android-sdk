package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.Assignment
import com.assinafy.sdk.request.CreateAssignmentRequest
import com.assinafy.sdk.request.SignerReference
import com.assinafy.sdk.util.ResponseHandler

class AssignmentResource(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    suspend fun create(documentId: String, request: CreateAssignmentRequest): Assignment {
        val docId = requireId(documentId, "Document ID")
        if (request.signers.isEmpty()) {
            throw ValidationException("At least one signer is required")
        }
        logger.info("Creating assignment", mapOf("documentId" to docId, "signers" to request.signers.size))
        return call("Failed to create assignment", Assignment::class.java) {
            http.post("/documents/$docId/assignments", ResponseHandler.GSON.toJson(normalise(request)))
        }
    }

    suspend fun estimateCost(documentId: String, request: CreateAssignmentRequest): Map<String, Any> {
        val docId = requireId(documentId, "Document ID")
        return callMap("Failed to estimate assignment cost") {
            http.post(
                "/documents/$docId/assignments/estimate-cost",
                ResponseHandler.GSON.toJson(normalise(request, allowWithoutId = true)),
            )
        }
    }

    suspend fun resetExpiration(documentId: String, assignmentId: String, expiresAt: String): Assignment {
        val docId = requireId(documentId, "Document ID")
        val asgId = requireId(assignmentId, "Assignment ID")
        return call("Failed to update assignment expiration", Assignment::class.java) {
            http.put(
                "/documents/$docId/assignments/$asgId/reset-expiration",
                ResponseHandler.GSON.toJson(mapOf("expires_at" to expiresAt)),
            )
        }
    }

    suspend fun resendNotification(documentId: String, assignmentId: String, signerId: String): Map<String, Any> {
        val docId = requireId(documentId, "Document ID")
        val asgId = requireId(assignmentId, "Assignment ID")
        val sid = requireId(signerId, "Signer ID")
        return callMap("Failed to resend signer notification") {
            http.put("/documents/$docId/assignments/$asgId/signers/$sid/resend")
        }
    }

    suspend fun estimateResendCost(documentId: String, assignmentId: String, signerId: String): Map<String, Any> {
        val docId = requireId(documentId, "Document ID")
        val asgId = requireId(assignmentId, "Assignment ID")
        val sid = requireId(signerId, "Signer ID")
        return callMap("Failed to estimate resend cost") {
            http.post("/documents/$docId/assignments/$asgId/signers/$sid/estimate-resend-cost")
        }
    }

    suspend fun cancel(documentId: String, reason: String, accountId: String? = null): Map<String, Any> {
        val docId = requireId(documentId, "Document ID")
        val accId = accountId(accountId)
        logger.info("Cancelling signature request", mapOf("documentId" to docId, "reason" to reason))
        return callMap("Failed to cancel signature request") {
            http.post(
                "/accounts/$accId/signature-requests/$docId/cancel",
                ResponseHandler.GSON.toJson(mapOf("document_id" to docId, "reason" to reason)),
            )
        }
    }

    private fun normalise(
        request: CreateAssignmentRequest,
        allowWithoutId: Boolean = false,
    ): Map<String, Any?> {
        val signers = request.signers.map { ref -> normaliseRef(ref, allowWithoutId) }
        return buildMap {
            put("method", request.method)
            put("signers", signers)
            request.message?.let { put("message", it) }
            request.expiresAt?.let { put("expires_at", it) }
            request.copyReceivers?.let { put("copy_receivers", it) }
            request.entries?.let { put("entries", it) }
        }
    }

    private fun normaliseRef(ref: SignerReference, allowWithoutId: Boolean): Map<String, Any?> {
        val result = buildMap {
            ref.id?.takeIf { it.isNotBlank() }?.let { put("id", it) }
            ref.verificationMethod?.let { put("verification_method", it) }
            ref.notificationMethods?.let { put("notification_methods", it) }
        }
        if (result.isEmpty() && !allowWithoutId) {
            throw ValidationException("Invalid signer reference: id is required")
        }
        return result
    }
}
