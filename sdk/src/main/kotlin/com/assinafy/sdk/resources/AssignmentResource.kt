package com.assinafy.sdk.resources

import com.assinafy.sdk.AssignmentMethod
import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.Assignment
import com.assinafy.sdk.models.CostEstimate
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.models.ResendEmailResponse
import com.assinafy.sdk.models.WhatsappNotification
import com.assinafy.sdk.request.CreateAssignmentRequest
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.SignerReference
import com.assinafy.sdk.util.ApiValidator

/**
 * Assignment (signature request) operations scoped to a document: create, cost estimation,
 * expiration management, decline, resend, and WhatsApp notification listing.
 * API failures surface as [com.assinafy.sdk.exceptions.ApiException]; transport failures surface as
 * [com.assinafy.sdk.exceptions.NetworkException].
 */
class AssignmentResource internal constructor(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
    private val publicHttp: ApiHttpClient = http,
) : BaseResource(http, defaultAccountId, logger) {

    /**
     * Lists assignments with `GET /assignments` and no request body.
     *
     * Only `page` and `per-page` are read from [params]. Supplying [accountId] adds the legacy
     * `accountId` query used by older API-key deployments; omit it for the current OpenAPI request.
     * Response `data` is an array of complete [Assignment] values and `X-Pagination-*` headers are
     * exposed through [PaginatedResult.meta].
     *
     * Wire operation: `GET /v1/assignments`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * [
     *   {
     *     "resource": "assignment",
     *     "id": "id-placeholder",
     *     "sender_email": "person@example.com",
     *     "method": "virtual",
     *     "expires_at": null,
     *     "message": null,
     *     "signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],
     *     "copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],
     *     "items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],
     *     "summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},
     *     "signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]
     *   }
     * ]
     * ```
     *
     * @param params Optional one-based page and records-per-page values.
     * @param accountId Optional compatibility scoping query; the client's default account is not sent.
     * @return Assignment page and optional pagination metadata.
     * @throws ValidationException if an explicitly supplied [accountId] is blank.
     */
    suspend fun list(params: ListParams = ListParams(), accountId: String? = null): PaginatedResult<Assignment> {
        val query = buildMap<String, Any?> {
            params.page?.let { put("page", it) }
            params.perPage?.let { put("per-page", it) }
            // Older API-key deployments require this undocumented scoping query. It is only sent
            // when the caller explicitly opts in, keeping the default request OpenAPI-exact.
            accountId?.let { put("accountId", requireId(it, "Account ID")) }
        }
        return callList("Failed to list assignments", Assignment::class.java) {
            http.get("/assignments", query)
        }
    }

    /**
     * Creates an assignment (`POST /documents/{documentId}/assignments`). The response includes the
     * per-signer `signing_urls` and a `summary`.
     *
     * Request body — null values are omitted, and `entries` is required only for `collect`:
     * Each `signing_urls` entry ends in the signer's one-time access code. Treat it as a
     * credential: deliver it through the intended channel and never log or persist it.
     *
     * Wire operation: `POST /v1/documents/{documentId}/assignments`.
     *
     * Request body (`application/json`; optional members may be omitted):
     * ```json
     * {
     *   "method": "collect",
     *   "signers": [{"id": "id-placeholder","verification_method": "Email","notification_methods": ["Email"],"step": 1}],
     *   "entries": [{"page_id": "page-id-placeholder","fields": [{"signer_id": "signer-id-placeholder","field_id": "field-id-placeholder","display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"}}]}],
     *   "message": "example",
     *   "expires_at": "2026-01-01T00:00:00Z",
     *   "copy_receivers": ["example"]
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "resource": "assignment",
     *   "id": "id-placeholder",
     *   "sender_email": "person@example.com",
     *   "method": "virtual",
     *   "expires_at": null,
     *   "message": null,
     *   "signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],
     *   "copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],
     *   "items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],
     *   "summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},
     *   "signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]
     * }
     * ```
     *
     * @param documentId Stable document identifier placed in the path.
     * @param request Assignment method, signers, optional field placements, and delivery settings.
     * @return Created assignment, including signer state, summary, items, and generated signing URLs.
     * @throws ValidationException for blank IDs, missing required signers/entries, unsupported methods
     * or channels, invalid signing steps, or invalid collect-field placement.
     */
    suspend fun create(documentId: String, request: CreateAssignmentRequest): Assignment {
        val docId = requireId(documentId, "Document ID")
        ApiValidator.requireAtLeastOne(request.signers, "signer")
        validateRequest(request, estimate = false)
        logger.info("Creating assignment", mapOf("documentId" to docId, "signers" to request.signers.size))
        return call("Failed to create assignment", Assignment::class.java) {
            http.post("/documents/${pathSegment(docId)}/assignments", toJson(normalise(request)))
        }
    }

    /**
     * Estimates assignment cost with `POST /documents/{documentId}/assignments/estimate-cost`.
     *
     * Request body carries only what affects pricing — signer IDs, steps, messages, expiration and
     * copy receivers are deliberately omitted:
     * `blocking_reason` is one of `PendingPayment`, `InsufficientDocuments`, or
     * `InsufficientCredits`. A digital-certificate signer adds a `SignatureDigitalCertificate`
     * breakdown line worth two credits on top of its notification.
     *
     * Wire operation: `POST /v1/documents/{documentId}/assignments/estimate-cost`.
     *
     * Request body (`application/json`; optional members may be omitted):
     * ```json
     * {
     *   "method": "collect",
     *   "signers": [{"verification_method": "Email","notification_methods": ["Email"]}],
     *   "entries": [{"page_id":"page-placeholder","fields":[{"signer_id":"signer-placeholder","field_id":"field-placeholder","display_settings":{"left":72,"top":640,"width":180,"height":40,"fontSize":12}}]}]
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "documents": 1,
     *   "credits": 1,
     *   "needs_extra_document": true,
     *   "extra_document_cost": 1,
     *   "total_credits": 1,
     *   "breakdown": [{"code": "example","name": "Example","cost": 1,"quantity": 1,"unit_cost": 1}],
     *   "document_balance": 1,
     *   "credit_balance": 1,
     *   "has_sufficient_resources": true,
     *   "blocking_reason": "PendingPayment",
     *   "message": null
     * }
     * ```
     *
     * @param documentId Stable document identifier placed in the path.
     * @param request Proposed method, signer channels, and optional collect placements.
     * @return Credit/document estimate and resource-sufficiency result.
     * @throws ValidationException for blank IDs, missing required signers/entries, unsupported methods
     * or channels, or invalid collect requirements.
     */
    suspend fun estimateCost(documentId: String, request: CreateAssignmentRequest): CostEstimate {
        val docId = requireId(documentId, "Document ID")
        validateRequest(request, estimate = true)
        return call("Failed to estimate assignment cost", CostEstimate::class.java) {
            http.post(
                "/documents/${pathSegment(docId)}/assignments/estimate-cost",
                toJson(normalise(request, estimate = true)),
            )
        }
    }

    /**
     * Sets a new expiration for an assignment. A non-blank [expiresAt] follows the current OpenAPI
     * request exactly. Explicitly passing `null` or blank opts into deployed-service compatibility
     * that clears the expiration entirely (the assignment will no longer expire).
     *
     * Wire request: `PUT /documents/{documentId}/assignments/{assignmentId}/reset-expiration` with
     * exactly `{"expires_at":"..."}`. The compatibility clear request is
     * `{"expires_at":null}`. The response `data` is the complete updated [Assignment].
     *
     * Wire operation: `PUT /v1/documents/{documentId}/assignments/{assignmentId}/reset-expiration`.
     *
     * Request body (`application/json`; optional members may be omitted):
     * ```json
     * {
     *   "expires_at": "2026-01-01T00:00:00Z"
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "resource": "assignment",
     *   "id": "id-placeholder",
     *   "sender_email": "person@example.com",
     *   "method": "virtual",
     *   "expires_at": null,
     *   "message": null,
     *   "signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true,"verification_method": null,"notification_methods": ["Email"],"step": 1,"notified": true,"completed": true,"notification_history": [{"event": "example","status": "sent","error_code": null,"error_message": null,"sent_at": null,"failed_at": null}]}],
     *   "copy_receivers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}],
     *   "items": [{"id": "id-placeholder","page": {"id": "id-placeholder","number": 1,"height": 1,"width": 1,"download_url": "https://example.com/resource"},"signer": {"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true},"field": {"resource": "field","id": "id-placeholder","name": "Example","type": "text","regex": null,"is_pre_defined": true,"is_active": true,"is_required": true,"is_standard": true,"is_read_only": true,"is_visible": true},"display_settings": {"left": 1,"top": 1,"width": 1,"height": 1,"fontFamily": "Arial","fontSize": 1,"backgroundColor": "#D5EBFF"},"value": null,"completed": true}],
     *   "summary": {"signer_count": 1,"completed_count": 1,"signers": [{"resource": "signer","id": "id-placeholder","full_name": "Example","email": null,"whatsapp_phone_number": null,"has_accepted_terms": true}]},
     *   "signing_urls": [{"signer_id": "signer-id-placeholder","url": "https://example.com/resource"}]
     * }
     * ```
     *
     * @param documentId Stable document identifier.
     * @param assignmentId Stable assignment identifier.
     * @param expiresAt Replacement ISO-8601 expiration, or explicit compatibility `null`/blank to clear it.
     * @return Complete updated assignment.
     * @throws ValidationException if either identifier is blank.
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

    /**
     * Declines an assignment through the public signer route.
     *
     * Sends `PUT /documents/{documentId}/assignments/{assignmentId}/reject`, the access code only as
     * `signer-access-code` in the query, and `{"decline_reason":"..."}` as the complete JSON body.
     * A successful empty-data envelope is returned as [Unit].
     *
     * Wire operation: `PUT /v1/documents/{documentId}/assignments/{assignmentId}/reject`.
     *
     * Request body (`application/json`; optional members may be omitted):
     * ```json
     * {
     *   "decline_reason": "example"
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * []
     * ```
     *
     * @param documentId Stable document identifier.
     * @param assignmentId Stable assignment identifier.
     * @param signerAccessCode One-time signer code sent only in the query.
     * @param reason Required free-text decline reason.
     * @throws ValidationException if any identifier or [reason] is blank.
     */
    @Deprecated("Use AssinafyClient.signerDocuments.decline")
    suspend fun decline(documentId: String, assignmentId: String, signerAccessCode: String, reason: String) =
        SignerDocumentResource(publicHttp).decline(documentId, assignmentId, signerAccessCode, reason)

    /**
     * Lists WhatsApp messages dispatched for an assignment.
     *
     * Wire request: `GET /documents/{documentId}/assignments/{assignmentId}/whatsapp-notifications`
     * with no query or body. Response `data` is an array containing render text, destination,
     * signer, send time, and buttons for each [WhatsappNotification].
     *
     * Wire operation: `GET /v1/documents/{documentId}/assignments/{assignmentId}/whatsapp-notifications`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * [
     *   {
     *     "sent_at": 1,
     *     "header": "example",
     *     "body": "example",
     *     "buttons": [{"text": "example"}],
     *     "phone_number": "+15555550100",
     *     "signer_id": "signer-id-placeholder"
     *   }
     * ]
     * ```
     *
     * @param documentId Stable document identifier.
     * @param assignmentId Stable assignment identifier.
     * @return Rendered WhatsApp notification history.
     * @throws ValidationException if either identifier is blank.
     */
    suspend fun listWhatsappNotifications(documentId: String, assignmentId: String): List<WhatsappNotification> {
        val docId = requireId(documentId, "Document ID")
        val asgId = requireId(assignmentId, "Assignment ID")
        val result = callList("Failed to list WhatsApp notifications", WhatsappNotification::class.java) {
            http.get("/documents/${pathSegment(docId)}/assignments/${pathSegment(asgId)}/whatsapp-notifications")
        }
        return result.data
    }

    /**
     * Resends one signer's signature notification.
     *
     * Sends a `PUT` to
     * `/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/resend`. The current
     * OpenAPI request has no body; [channel] opts into the deployed compatibility body
     * `{"channel":"email"}` or `{"channel":"whatsapp"}`. Response `data` is
     * [ResendEmailResponse]: delivery acceptance plus document and signer IDs.
     *
     * Wire operation: `PUT /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/resend`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "is_sent": true,
     *   "document_id": "document-id-placeholder",
     *   "signer_id": "signer-id-placeholder"
     * }
     * ```
     *
     * @param documentId Stable document identifier.
     * @param assignmentId Stable assignment identifier.
     * @param signerId Stable signer identifier.
     * @param channel Optional deployed-service notification channel; omit for the OpenAPI request.
     * @return Resend acceptance and related IDs.
     * @throws ValidationException if any identifier is blank.
     */
    suspend fun resendNotification(
        documentId: String,
        assignmentId: String,
        signerId: String,
        channel: String? = null,
    ): ResendEmailResponse {
        val docId = requireId(documentId, "Document ID")
        val asgId = requireId(assignmentId, "Assignment ID")
        val sid = requireId(signerId, "Signer ID")
        val normalizedChannel = channel?.trim()?.lowercase()
        if (normalizedChannel != null && normalizedChannel !in RESEND_CHANNELS) {
            throw ValidationException("Resend channel must be email or whatsapp")
        }
        return call("Failed to resend signer notification", ResendEmailResponse::class.java) {
            http.put(
                "/documents/${pathSegment(docId)}/assignments/${pathSegment(asgId)}/signers/${pathSegment(sid)}/resend",
                normalizedChannel?.let { toJson(mapOf("channel" to it)) },
            )
        }
    }

    /**
     * Estimates the cost of resending one signer notification.
     *
     * Sends an empty-body `POST` to
     * `/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/estimate-resend-cost`.
     * Response `data` is the complete [CostEstimate] payload.
     *
     * Wire operation: `POST /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/estimate-resend-cost`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "documents": 1,
     *   "credits": 1,
     *   "needs_extra_document": true,
     *   "extra_document_cost": 1,
     *   "total_credits": 1,
     *   "breakdown": [{"code": "example","name": "Example","cost": 1,"quantity": 1,"unit_cost": 1}],
     *   "document_balance": 1,
     *   "credit_balance": 1,
     *   "has_sufficient_resources": true,
     *   "blocking_reason": "PendingPayment",
     *   "message": null
     * }
     * ```
     *
     * @param documentId Stable document identifier.
     * @param assignmentId Stable assignment identifier.
     * @param signerId Stable signer identifier.
     * @return Credit cost, balances, sufficiency, and any itemized breakdown.
     * @throws ValidationException if any identifier is blank.
     */
    suspend fun estimateResendCost(documentId: String, assignmentId: String, signerId: String): CostEstimate {
        val docId = requireId(documentId, "Document ID")
        val asgId = requireId(assignmentId, "Assignment ID")
        val sid = requireId(signerId, "Signer ID")
        return call("Failed to estimate resend cost", CostEstimate::class.java) {
            http.post(
                "/documents/${pathSegment(docId)}/assignments/${pathSegment(asgId)}/signers/${pathSegment(sid)}/estimate-resend-cost",
            )
        }
    }

    private fun normalise(
        request: CreateAssignmentRequest,
        estimate: Boolean = false,
    ): Map<String, Any?> {
        val signers = request.signers.map { ref -> normaliseRef(ref, estimate) }
        return buildMap {
            put("method", requireId(request.method, "Assignment method"))
            put("signers", signers)
            request.entries?.let { put("entries", it) }
            if (!estimate) {
                request.message?.let { put("message", it) }
                request.expiresAt?.let { put("expires_at", it.trim()) }
                request.copyReceivers?.let { put("copy_receivers", it.map { id -> requireId(id, "Copy receiver signer ID") }) }
            }
        }
    }

    private fun normaliseRef(ref: SignerReference, estimate: Boolean): Map<String, Any?> {
        if (!estimate) requireId(ref.id, "Signer ID")
        return buildMap {
            if (!estimate) ref.id?.trim()?.let { put("id", it) }
            ref.verificationMethod?.let { put("verification_method", it) }
            ref.notificationMethods?.let { put("notification_methods", it) }
            if (!estimate) ref.step?.let { put("step", it) }
        }
    }

    private fun validateRequest(request: CreateAssignmentRequest, estimate: Boolean) {
        val method = requireId(request.method, "Assignment method")
        if (method !in METHODS) throw ValidationException("Assignment method must be virtual or collect")
        // Required for both methods: creation needs to know who signs, and an estimate is priced
        // per signer. The contract marks `signers` required only for `virtual`, but the API
        // answers a signer-less body with 400 "Pelo menos um signatários precisa ser informado."
        if (request.signers.isEmpty()) {
            throw ValidationException("At least one signer is required")
        }
        if (method == AssignmentMethod.COLLECT && request.entries.isNullOrEmpty()) {
            throw ValidationException("At least one field-placement entry is required for a collect assignment")
        }
        request.signers.forEach { signer ->
            ApiValidator.requireValidSignerChannels(signer.verificationMethod, signer.notificationMethods)
        }
        if (!estimate) {
            ApiValidator.requireValidSigningSteps(request.signers.map(SignerReference::step))
            ApiValidator.requireDigitalCertificateStepIsolation(
                request.signers.map { it.verificationMethod to it.step },
            )
            request.entries.orEmpty().forEach { entry ->
                requireId(entry.pageId, "Assignment page ID")
                ApiValidator.requireAtLeastOne(entry.fields, "assignment field")
                entry.fields.forEach { field ->
                    requireId(field.signerId, "Assignment field signer ID")
                    requireId(field.fieldId, "Assignment field ID")
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

    /** Assignment-method and channel validation constants. */
    companion object {
        private val RESEND_CHANNELS = setOf("email", "whatsapp")
        private val METHODS = setOf(AssignmentMethod.VIRTUAL, AssignmentMethod.COLLECT)
    }
}
