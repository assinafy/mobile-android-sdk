package com.assinafy.sdk.resources

import com.assinafy.sdk.AssignmentMethod
import com.assinafy.sdk.DocumentArtifact
import com.assinafy.sdk.EnvelopeStatusFilter
import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.CostEstimate
import com.assinafy.sdk.models.DocumentActivity
import com.assinafy.sdk.models.DocumentEnvelope
import com.assinafy.sdk.models.EnvelopeCancellation
import com.assinafy.sdk.models.EnvelopeReminder
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.request.CreateAssignmentRequest
import com.assinafy.sdk.request.CreateEnvelopeRequest
import com.assinafy.sdk.request.EnvelopeDocumentRequest
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.SignerReference
import com.assinafy.sdk.request.UpdateEnvelopeRequest
import com.assinafy.sdk.util.ApiValidator
import com.assinafy.sdk.util.AssignmentValidation
import java.math.BigDecimal

/** Multiple documents sent together; requires the workspace's Envelopes entitlement. */
class EnvelopeResource internal constructor(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {
    /**
     * List envelopes. Query: page, per-page, search and status=open|closed. Newest first.
     *
     * Wire operation: `GET /v1/accounts/{accountId}/envelopes`.
     *
     * Optional query/header parameters: `page` (query), `per-page` (query), `search` (query), `status` (query).
     *
     * Request body: none.
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * [
     *   {
     *     "resource": "envelope",
     *     "id": "id-placeholder",
     *     "name": "Contrato de prestação de serviços",
     *     "message": "Segue o contrato e seus anexos.",
     *     "documents": [
     *       {
     *         "id": "id-placeholder",
     *         "position": 1,
     *         "name": "contrato.pdf",
     *         "status": "pending_signature",
     *         "artifacts": {
     *           "original": "https://example.com/resource",
     *           "thumbnail": "https://example.com/resource"
     *         },
     *         "signers": [
     *           {
     *             "id": "id-placeholder",
     *             "full_name": "Example Person",
     *             "email": "person@example.com",
     *             "whatsapp_phone_number": "+5548999990001",
     *             "completed": false,
     *             "signed_at": "2026-11-06T14:30:00Z"
     *           }
     *         ]
     *       }
     *     ],
     *     "counts": {
     *       "certificated": 1,
     *       "pending_signature": 2
     *     },
     *     "signing_urls": [
     *       {
     *         "signer_id": "example",
     *         "url": "https://example.com/resource",
     *         "embedded": false
     *       }
     *     ],
     *     "created_at": "2026-11-04T12:00:00Z"
     *   }
     * ]
     * ```
     */
    suspend fun list(params: ListParams = ListParams(), accountId: String? = null): PaginatedResult<DocumentEnvelope> {
        val path = envelopesPath(accountId)
        if (params.status != null && params.status !in EnvelopeStatusFilter.ALL) throw ValidationException("Envelope status filter must be open or closed")
        return callList("Failed to list envelopes", DocumentEnvelope::class.java) {
            http.get(path, params.toQueryMap().filterKeys { it in setOf("page", "per-page", "search", "status") })
        }
    }

    /**
     * Create envelope. Upload every PDF and wait for metadata first. A successful creation does not guarantee invitation delivery. The optional Idempotency-Key header prevents duplicate creation on explicit retries.
     *
     * Wire operation: `POST /v1/accounts/{accountId}/envelopes`.
     *
     * Optional query/header parameters: `Idempotency-Key` (header).
     *
     * Request body (`application/json`; optional fields may be omitted):
     * ```json
     * {
     *   "name": "Contrato de prestação de serviços",
     *   "message": "Segue o contrato e seus anexos.",
     *   "max_total_credits": 1.5,
     *   "signers": [
     *     {
     *       "id": "id-placeholder",
     *       "verification_method": "Email",
     *       "notification_methods": [
     *         "Email"
     *       ],
     *       "embedded": true
     *     }
     *   ],
     *   "documents": [
     *     {
     *       "id": "id-placeholder",
     *       "method": "virtual",
     *       "entries": [
     *         {
     *           "page_id": "example",
     *           "fields": [
     *             {
     *               "signer_id": "example",
     *               "field_id": "example",
     *               "display_settings": {
     *                 "left": 69,
     *                 "top": 282,
     *                 "width": 421,
     *                 "height": 45.86,
     *                 "fontFamily": "Arial",
     *                 "fontSize": 22,
     *                 "backgroundColor": "#D5EBFF"
     *               }
     *             }
     *           ]
     *         }
     *       ],
     *       "signer_ids": [
     *         "example"
     *       ],
     *       "expires_at": "example",
     *       "copy_receiver_ids": [
     *         "example"
     *       ]
     *     }
     *   ]
     * }
     * ```
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * {
     *   "resource": "envelope",
     *   "id": "id-placeholder",
     *   "name": "Contrato de prestação de serviços",
     *   "message": "Segue o contrato e seus anexos.",
     *   "documents": [
     *     {
     *       "id": "id-placeholder",
     *       "position": 1,
     *       "name": "contrato.pdf",
     *       "status": "pending_signature",
     *       "artifacts": {
     *         "original": "https://example.com/resource",
     *         "thumbnail": "https://example.com/resource"
     *       },
     *       "signers": [
     *         {
     *           "id": "id-placeholder",
     *           "full_name": "Example Person",
     *           "email": "person@example.com",
     *           "whatsapp_phone_number": "+5548999990001",
     *           "completed": false,
     *           "signed_at": "2026-11-06T14:30:00Z"
     *         }
     *       ]
     *     }
     *   ],
     *   "counts": {
     *     "certificated": 1,
     *     "pending_signature": 2
     *   },
     *   "signing_urls": [
     *     {
     *       "signer_id": "example",
     *       "url": "https://example.com/resource",
     *       "embedded": false
     *     }
     *   ],
     *   "created_at": "2026-11-04T12:00:00Z"
     * }
     * ```
     */
    suspend fun create(request: CreateEnvelopeRequest, accountId: String? = null): DocumentEnvelope {
        val path = envelopesPath(accountId)
        val body = createBody(request)
        return call("Failed to create envelope", DocumentEnvelope::class.java) { http.postIdempotent(path, toJson(body), request.idempotencyKey) }
    }

    /**
     * Estimate envelope cost. Creates and charges nothing. One document allowance is charged per envelope; certificate verification is charged per document.
     *
     * Wire operation: `POST /v1/accounts/{accountId}/envelopes/estimate-cost`.
     *
     * Request body (`application/json`; optional fields may be omitted):
     * ```json
     * {
     *   "name": "Contrato de prestação de serviços",
     *   "message": "Segue o contrato e seus anexos.",
     *   "max_total_credits": 1.5,
     *   "signers": [
     *     {
     *       "id": "id-placeholder",
     *       "verification_method": "Email",
     *       "notification_methods": [
     *         "Email"
     *       ],
     *       "embedded": true
     *     }
     *   ],
     *   "documents": [
     *     {
     *       "id": "id-placeholder",
     *       "method": "virtual",
     *       "entries": [
     *         {
     *           "page_id": "example",
     *           "fields": [
     *             {
     *               "signer_id": "example",
     *               "field_id": "example",
     *               "display_settings": {
     *                 "left": 69,
     *                 "top": 282,
     *                 "width": 421,
     *                 "height": 45.86,
     *                 "fontFamily": "Arial",
     *                 "fontSize": 22,
     *                 "backgroundColor": "#D5EBFF"
     *               }
     *             }
     *           ]
     *         }
     *       ],
     *       "signer_ids": [
     *         "example"
     *       ],
     *       "expires_at": "example",
     *       "copy_receiver_ids": [
     *         "example"
     *       ]
     *     }
     *   ]
     * }
     * ```
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * {
     *   "documents": 1,
     *   "credits": 0.9,
     *   "needs_extra_document": true,
     *   "extra_document_cost": 1,
     *   "total_credits": 1.9,
     *   "breakdown": [
     *     {
     *       "code": "NotificationWhatsapp",
     *       "name": "Whatsapp Notification",
     *       "cost": 0.9,
     *       "quantity": 2,
     *       "unit_cost": 0.45
     *     }
     *   ],
     *   "document_balance": 0,
     *   "credit_balance": 10,
     *   "has_sufficient_resources": true,
     *   "blocking_reason": null,
     *   "message": "example"
     * }
     * ```
     */
    suspend fun estimateCost(request: CreateEnvelopeRequest, accountId: String? = null): CostEstimate {
        val path = envelopesPath(accountId)
        val body = createBody(request)
        return call("Failed to estimate envelope cost", CostEstimate::class.java) { http.post("$path/estimate-cost", toJson(body)) }
    }

    /**
     * Get envelope.
     *
     * Wire operation: `GET /v1/accounts/{accountId}/envelopes/{envelopeId}`.
     *
     * Request body: none.
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * {
     *   "resource": "envelope",
     *   "id": "id-placeholder",
     *   "name": "Contrato de prestação de serviços",
     *   "message": "Segue o contrato e seus anexos.",
     *   "documents": [
     *     {
     *       "id": "id-placeholder",
     *       "position": 1,
     *       "name": "contrato.pdf",
     *       "status": "pending_signature",
     *       "artifacts": {
     *         "original": "https://example.com/resource",
     *         "thumbnail": "https://example.com/resource"
     *       },
     *       "signers": [
     *         {
     *           "id": "id-placeholder",
     *           "full_name": "Example Person",
     *           "email": "person@example.com",
     *           "whatsapp_phone_number": "+5548999990001",
     *           "completed": false,
     *           "signed_at": "2026-11-06T14:30:00Z"
     *         }
     *       ]
     *     }
     *   ],
     *   "counts": {
     *     "certificated": 1,
     *     "pending_signature": 2
     *   },
     *   "signing_urls": [
     *     {
     *       "signer_id": "example",
     *       "url": "https://example.com/resource",
     *       "embedded": false
     *     }
     *   ],
     *   "created_at": "2026-11-04T12:00:00Z"
     * }
     * ```
     */
    suspend fun get(envelopeId: String, accountId: String? = null): DocumentEnvelope {
        val path = envelopePath(envelopeId, accountId)
        return call("Failed to read envelope", DocumentEnvelope::class.java) { http.get(path) }
    }

    /**
     * Update envelope. Omitted fields remain unchanged; clearMessage sends explicit null. Reordering requires every current document exactly once.
     *
     * Wire operation: `PATCH /v1/accounts/{accountId}/envelopes/{envelopeId}`.
     *
     * Request body (`application/json`; optional fields may be omitted):
     * ```json
     * {
     *   "name": "Contrato de prestação de serviços",
     *   "message": "Segue o contrato e seus anexos.",
     *   "document_ids": [
     *     "example"
     *   ]
     * }
     * ```
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * {
     *   "resource": "envelope",
     *   "id": "id-placeholder",
     *   "name": "Contrato de prestação de serviços",
     *   "message": "Segue o contrato e seus anexos.",
     *   "documents": [
     *     {
     *       "id": "id-placeholder",
     *       "position": 1,
     *       "name": "contrato.pdf",
     *       "status": "pending_signature",
     *       "artifacts": {
     *         "original": "https://example.com/resource",
     *         "thumbnail": "https://example.com/resource"
     *       },
     *       "signers": [
     *         {
     *           "id": "id-placeholder",
     *           "full_name": "Example Person",
     *           "email": "person@example.com",
     *           "whatsapp_phone_number": "+5548999990001",
     *           "completed": false,
     *           "signed_at": "2026-11-06T14:30:00Z"
     *         }
     *       ]
     *     }
     *   ],
     *   "counts": {
     *     "certificated": 1,
     *     "pending_signature": 2
     *   },
     *   "signing_urls": [
     *     {
     *       "signer_id": "example",
     *       "url": "https://example.com/resource",
     *       "embedded": false
     *     }
     *   ],
     *   "created_at": "2026-11-04T12:00:00Z"
     * }
     * ```
     */
    suspend fun update(envelopeId: String, request: UpdateEnvelopeRequest, accountId: String? = null): DocumentEnvelope {
        val path = envelopePath(envelopeId, accountId)
        if (request.clearMessage && request.message != null) throw ValidationException("Set message or clearMessage, not both")
        val body = buildMap<String, Any?> {
            request.name?.let { put("name", envelopeName(it)) }
            if (request.clearMessage) put("message", null) else request.message?.let { put("message", it) }
            request.documentIds?.let { put("document_ids", distinctIds(it, "Document ID")) }
        }
        if (body.isEmpty()) throw ValidationException("At least one envelope change is required")
        return call("Failed to update envelope", DocumentEnvelope::class.java) { http.patch(path, toJsonAllowNulls(body)) }
    }

    /**
     * Delete envelope. Every document must be closed first. Documents, certificates and downloads are preserved.
     *
     * Wire operation: `DELETE /v1/accounts/{accountId}/envelopes/{envelopeId}`.
     *
     * Request body: none.
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * []
     * ```
     */
    suspend fun delete(envelopeId: String, accountId: String? = null) {
        val path = envelopePath(envelopeId, accountId)
        callVoid("Failed to delete envelope") { http.delete(path) }
    }

    /**
     * List envelope activities. Query: page, per-page and sort=id|-id. Includes documents removed from the envelope, except deleted documents.
     *
     * Wire operation: `GET /v1/accounts/{accountId}/envelopes/{envelopeId}/activities`.
     *
     * Optional query/header parameters: `sort` (query), `page` (query), `per-page` (query).
     *
     * Request body: none.
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * [
     *   {
     *     "id": 4,
     *     "event": "assignment_created",
     *     "message": "Assignment created by John Smith.",
     *     "payload": [],
     *     "origin": {
     *       "ip": "172.19.0.1",
     *       "user-agent": "example"
     *     },
     *     "created_at": "2022-07-19T19:28:13Z",
     *     "document_id": "document-id-placeholder",
     *     "document_name": "Contrato.pdf"
     *   }
     * ]
     * ```
     */
    suspend fun activities(envelopeId: String, params: ListParams = ListParams(), accountId: String? = null): PaginatedResult<DocumentActivity> {
        val path = envelopePath(envelopeId, accountId)
        return callList("Failed to read envelope activities", DocumentActivity::class.java) {
            http.get("$path/activities", params.toQueryMap().filterKeys { it in setOf("page", "per-page", "sort") })
        }
    }

    /**
     * Download envelope artifacts. ZIP of available document artifacts, in envelope order; 404 when no document has the artifact.
     *
     * Wire operation: `GET /v1/accounts/{accountId}/envelopes/{envelopeId}/download/{artifactName}`.
     *
     * Request body: none.
     *
     * Response: binary bytes or an empty successful response, as the operation specifies.
     */
    suspend fun download(envelopeId: String, artifactName: String = DocumentArtifact.CERTIFICATED, accountId: String? = null): ByteArray {
        val path = envelopePath(envelopeId, accountId)
        if (artifactName !in setOf(DocumentArtifact.ORIGINAL, DocumentArtifact.CERTIFICATED, DocumentArtifact.CERTIFICATE_PAGE, DocumentArtifact.PADES, DocumentArtifact.BUNDLE)) {
            throw ValidationException("Unsupported envelope artifact")
        }
        return callBinary("Failed to download envelope") { http.getBinary("$path/download/${pathSegment(artifactName)}") }
    }

    /**
     * Cancel envelope. Cancels eligible open documents and notifies their participants. Closed or certification-queued documents remain unchanged.
     *
     * Wire operation: `PUT /v1/accounts/{accountId}/envelopes/{envelopeId}/cancel`.
     *
     * Request body: none.
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * [
     *   {
     *     "id": "id-placeholder",
     *     "status": "rejected_by_user",
     *     "cancelled": true
     *   }
     * ]
     * ```
     */
    suspend fun cancel(envelopeId: String, accountId: String? = null): List<EnvelopeCancellation> {
        val path = envelopePath(envelopeId, accountId)
        return callList("Failed to cancel envelope", EnvelopeCancellation::class.java) { http.put("$path/cancel") }.data
    }

    /**
     * Remind envelope signer. Sends one reminder for the whole envelope. Paid notification channels are charged again.
     *
     * Wire operation: `PUT /v1/accounts/{accountId}/envelopes/{envelopeId}/signers/{signerId}/resend`.
     *
     * Request body: none.
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * {
     *   "is_sent": true,
     *   "envelope_id": "example",
     *   "signer_id": "example"
     * }
     * ```
     */
    suspend fun resendNotification(envelopeId: String, signerId: String, accountId: String? = null): EnvelopeReminder {
        val path = envelopePath(envelopeId, accountId) + "/signers/${pathSegment(requireId(signerId, "Signer ID"))}/resend"
        return call("Failed to remind envelope signer", EnvelopeReminder::class.java) { http.put(path) }
    }

    /**
     * Add document to envelope. Only existing envelope signers may be selected; the server validates their channels and certificate isolation. Sends invitations for the added document.
     *
     * Wire operation: `POST /v1/accounts/{accountId}/envelopes/{envelopeId}/documents`.
     *
     * Request body (`application/json`; optional fields may be omitted):
     * ```json
     * {
     *   "id": "id-placeholder",
     *   "method": "virtual",
     *   "entries": [
     *     {
     *       "page_id": "example",
     *       "fields": [
     *         {
     *           "signer_id": "example",
     *           "field_id": "example",
     *           "display_settings": {
     *             "left": 69,
     *             "top": 282,
     *             "width": 421,
     *             "height": 45.86,
     *             "fontFamily": "Arial",
     *             "fontSize": 22,
     *             "backgroundColor": "#D5EBFF"
     *           }
     *         }
     *       ]
     *     }
     *   ],
     *   "signer_ids": [
     *     "example"
     *   ],
     *   "expires_at": "example",
     *   "copy_receiver_ids": [
     *     "example"
     *   ],
     *   "max_total_credits": 1.5
     * }
     * ```
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * {
     *   "resource": "envelope",
     *   "id": "id-placeholder",
     *   "name": "Contrato de prestação de serviços",
     *   "message": "Segue o contrato e seus anexos.",
     *   "documents": [
     *     {
     *       "id": "id-placeholder",
     *       "position": 1,
     *       "name": "contrato.pdf",
     *       "status": "pending_signature",
     *       "artifacts": {
     *         "original": "https://example.com/resource",
     *         "thumbnail": "https://example.com/resource"
     *       },
     *       "signers": [
     *         {
     *           "id": "id-placeholder",
     *           "full_name": "Example Person",
     *           "email": "person@example.com",
     *           "whatsapp_phone_number": "+5548999990001",
     *           "completed": false,
     *           "signed_at": "2026-11-06T14:30:00Z"
     *         }
     *       ]
     *     }
     *   ],
     *   "counts": {
     *     "certificated": 1,
     *     "pending_signature": 2
     *   },
     *   "signing_urls": [
     *     {
     *       "signer_id": "example",
     *       "url": "https://example.com/resource",
     *       "embedded": false
     *     }
     *   ],
     *   "created_at": "2026-11-04T12:00:00Z"
     * }
     * ```
     */
    suspend fun addDocument(envelopeId: String, request: EnvelopeDocumentRequest, maxTotalCredits: BigDecimal? = null, accountId: String? = null): DocumentEnvelope {
        val path = envelopePath(envelopeId, accountId)
        ApiValidator.requireCreditCeiling(maxTotalCredits)
        val body = documentBody(request, request.signerIds.map { SignerReference.ofId(it) }) + mapOf("max_total_credits" to maxTotalCredits)
        return call("Failed to add envelope document", DocumentEnvelope::class.java) { http.post("$path/documents", toJson(body)) }
    }

    /**
     * Take document out of envelope. Cancels and removes an open document; the other documents continue. Participants are notified.
     *
     * Wire operation: `DELETE /v1/accounts/{accountId}/envelopes/{envelopeId}/documents/{documentId}`.
     *
     * Request body: none.
     *
     * Response `data` inside `{status,message,data}`:
     * ```json
     * {
     *   "resource": "envelope",
     *   "id": "id-placeholder",
     *   "name": "Contrato de prestação de serviços",
     *   "message": "Segue o contrato e seus anexos.",
     *   "documents": [
     *     {
     *       "id": "id-placeholder",
     *       "position": 1,
     *       "name": "contrato.pdf",
     *       "status": "pending_signature",
     *       "artifacts": {
     *         "original": "https://example.com/resource",
     *         "thumbnail": "https://example.com/resource"
     *       },
     *       "signers": [
     *         {
     *           "id": "id-placeholder",
     *           "full_name": "Example Person",
     *           "email": "person@example.com",
     *           "whatsapp_phone_number": "+5548999990001",
     *           "completed": false,
     *           "signed_at": "2026-11-06T14:30:00Z"
     *         }
     *       ]
     *     }
     *   ],
     *   "counts": {
     *     "certificated": 1,
     *     "pending_signature": 2
     *   },
     *   "signing_urls": [
     *     {
     *       "signer_id": "example",
     *       "url": "https://example.com/resource",
     *       "embedded": false
     *     }
     *   ],
     *   "created_at": "2026-11-04T12:00:00Z"
     * }
     * ```
     */
    suspend fun removeDocument(envelopeId: String, documentId: String, accountId: String? = null): DocumentEnvelope {
        val path = envelopePath(envelopeId, accountId) + "/documents/${pathSegment(requireId(documentId, "Document ID"))}"
        return call("Failed to remove envelope document", DocumentEnvelope::class.java) { http.delete(path) }
    }

    private fun envelopesPath(accountId: String?): String = "/accounts/${pathSegment(accountId(accountId))}/envelopes"
    private fun envelopePath(envelopeId: String, accountId: String?): String = envelopesPath(accountId) + "/${pathSegment(requireId(envelopeId, "Envelope ID"))}"

    private fun envelopeName(value: String): String = requireId(value, "Envelope name").also {
        if (it.codePointCount(0, it.length) > 255 || '<' in it) throw ValidationException("Envelope name must be at most 255 characters without <")
    }

    private fun distinctIds(values: List<String>, label: String): List<String> {
        val ids = values.map { requireId(it, label) }
        if (ids.isEmpty() || ids.distinct().size != ids.size) throw ValidationException("$label list must be nonempty and distinct")
        return ids
    }

    private fun createBody(request: CreateEnvelopeRequest): Map<String, Any?> {
        val name = envelopeName(request.name)
        ApiValidator.requireCreditCeiling(request.maxTotalCredits)
        ApiValidator.requireIdempotencyKey(request.idempotencyKey)
        if (request.signers.size !in 1..20 || request.documents.size !in 1..20) throw ValidationException("An envelope requires 1..20 signers and documents")
        val ids = distinctIds(request.signers.map { requireId(it.id, "Signer ID") }, "Signer ID")
        distinctIds(request.documents.map { it.id }, "Document ID")
        if (request.signers.any { it.step != null }) throw ValidationException("Envelope signers cannot have signing steps")
        val signers = request.signers.map { signer ->
            ApiValidator.requireValidSignerChannels(signer.verificationMethod, signer.notificationMethods)
            ApiValidator.requireEmbeddedMethod(signer.embedded, signer.verificationMethod)
            signer.copy(id = requireId(signer.id, "Signer ID"))
        }
        val usedIds = request.documents.flatMap { it.signerIds }.map(String::trim).toSet()
        if (usedIds != ids.toSet()) throw ValidationException("Every envelope signer must belong to a document and every document signer must belong to the envelope")
        return mapOf(
            "name" to name,
            "message" to request.message,
            "signers" to signers,
            "documents" to request.documents.map { documentBody(it, signers) },
            "max_total_credits" to request.maxTotalCredits,
        )
    }

    private fun documentBody(document: EnvelopeDocumentRequest, signers: List<SignerReference>): Map<String, Any?> {
        val id = requireId(document.id, "Document ID")
        val ids = distinctIds(document.signerIds, "Signer ID")
        val method = requireId(document.method, "Assignment method")
        val request = CreateAssignmentRequest(method = method, signers = signers.filter { it.id?.trim() in ids }, entries = document.entries)
        AssignmentValidation.validateRequest(request, estimate = false)
        if (method == AssignmentMethod.COLLECT && document.entries.orEmpty().flatMap { it.fields }.any { it.displaySettings == null }) {
            throw ValidationException("Envelope collect fields require display settings")
        }
        return mapOf(
            "id" to id,
            "signer_ids" to ids,
            "method" to method,
            "entries" to AssignmentValidation.normalise(request)["entries"],
            "expires_at" to document.expiresAt,
            "copy_receiver_ids" to document.copyReceiverIds?.map { requireId(it, "Copy receiver ID") },
        )
    }
}
