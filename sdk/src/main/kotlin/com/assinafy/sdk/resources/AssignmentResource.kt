package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.Assignment
import com.assinafy.sdk.models.ResendEmailResponse
import com.assinafy.sdk.models.WhatsappNotification
import com.assinafy.sdk.request.CreateAssignmentRequest
import com.assinafy.sdk.request.SignerReference
import com.assinafy.sdk.util.ApiValidator

class AssignmentResource(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    suspend fun create(documentId: String, request: CreateAssignmentRequest): Assignment {
        val docId = requireId(documentId, "Document ID")
        ApiValidator.requireAtLeastOne(request.signers, "signer")
        logger.info("Creating assignment", mapOf("documentId" to docId, "signers" to request.signers.size))
        return call("Failed to create assignment", Assignment::class.java) {
            http.post("/documents/${pathSegment(docId)}/assignments", toJson(normalise(request)))
        }
    }

    suspend fun estimateCost(documentId: String, request: CreateAssignmentRequest): Map<String, Any> {
        val docId = requireId(documentId, "Document ID")
        return callMap("Failed to estimate assignment cost") {
            http.post(
                "/documents/${pathSegment(docId)}/assignments/estimate-cost",
                toJson(normalise(request, allowWithoutId = true)),
            )
        }
    }

    /**
     * Sets a new expiration for an assignment. Pass `null` to remove the expiration entirely
     * (the assignment will no longer expire).
     */
    suspend fun resetExpiration(documentId: String, assignmentId: String, expiresAt: String?): Assignment {
        val docId = requireId(documentId, "Document ID")
        val asgId = requireId(assignmentId, "Assignment ID")
        return call("Failed to update assignment expiration", Assignment::class.java) {
            http.put(
                "/documents/${pathSegment(docId)}/assignments/${pathSegment(asgId)}/reset-expiration",
                toJsonAllowNulls(mapOf("expires_at" to expiresAt?.trim()?.takeIf { it.isNotEmpty() })),
            )
        }
    }

    /** Declines (rejects) an assignment on behalf of a signer using their access code. */
    suspend fun decline(documentId: String, assignmentId: String, signerAccessCode: String, reason: String) {
        val docId = requireId(documentId, "Document ID")
        val asgId = requireId(assignmentId, "Assignment ID")
        val code = requireId(signerAccessCode, "Signer access code")
        val declineReason = requireId(reason, "Decline reason")
        callVoid("Failed to decline assignment") {
            http.put(
                "/documents/${pathSegment(docId)}/assignments/${pathSegment(asgId)}/reject" +
                    queryString("signer-access-code" to code),
                toJson(mapOf("decline_reason" to declineReason)),
            )
        }
    }

    /** Lists the WhatsApp notification messages dispatched for an assignment. */
    suspend fun listWhatsappNotifications(documentId: String, assignmentId: String): List<WhatsappNotification> {
        val docId = requireId(documentId, "Document ID")
        val asgId = requireId(assignmentId, "Assignment ID")
        val result = callList("Failed to list WhatsApp notifications", WhatsappNotification::class.java) {
            http.get("/documents/${pathSegment(docId)}/assignments/${pathSegment(asgId)}/whatsapp-notifications")
        }
        return result.data
    }

    suspend fun resendNotification(documentId: String, assignmentId: String, signerId: String): ResendEmailResponse {
        val docId = requireId(documentId, "Document ID")
        val asgId = requireId(assignmentId, "Assignment ID")
        val sid = requireId(signerId, "Signer ID")
        return call("Failed to resend signer notification", ResendEmailResponse::class.java) {
            http.put(
                "/documents/${pathSegment(docId)}/assignments/${pathSegment(asgId)}/signers/${pathSegment(sid)}/resend",
            )
        }
    }

    suspend fun estimateResendCost(documentId: String, assignmentId: String, signerId: String): Map<String, Any> {
        val docId = requireId(documentId, "Document ID")
        val asgId = requireId(assignmentId, "Assignment ID")
        val sid = requireId(signerId, "Signer ID")
        return callMap("Failed to estimate resend cost") {
            http.post(
                "/documents/${pathSegment(docId)}/assignments/${pathSegment(asgId)}/signers/${pathSegment(sid)}/estimate-resend-cost",
            )
        }
    }

    private fun normalise(
        request: CreateAssignmentRequest,
        allowWithoutId: Boolean = false,
    ): Map<String, Any?> {
        val signers = request.signers.map { ref -> normaliseRef(ref, allowWithoutId) }
        return buildMap {
            put("method", requireId(request.method, "Assignment method"))
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
            ref.step?.let { put("step", it) }
        }
        if (result.isEmpty() && !allowWithoutId) {
            throw ValidationException("Invalid signer reference: id is required")
        }
        return result
    }
}
