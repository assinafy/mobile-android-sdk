package com.assinafy.sdk.resources

import com.assinafy.sdk.DocumentArtifact
import com.assinafy.sdk.DocumentStatus
import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.SdkConstants
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.DocumentActivity
import com.assinafy.sdk.models.DocumentDetails
import com.assinafy.sdk.models.DocumentListItem
import com.assinafy.sdk.models.DocumentStatusInfo
import com.assinafy.sdk.models.DocumentUploadResponse
import com.assinafy.sdk.models.DocumentVerification
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.models.PublicDocumentInfo
import com.assinafy.sdk.models.Signer
import com.assinafy.sdk.models.SigningProgress
import com.assinafy.sdk.models.Tag
import com.assinafy.sdk.request.ConfirmSignerDataRequest
import com.assinafy.sdk.request.CreateDocumentFromTemplateRequest
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.TemplateSigner
import com.assinafy.sdk.util.ApiValidator
import com.assinafy.sdk.util.requireValidEmail
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Document operations: upload, list/fetch, readiness polling, downloads, activities, status helpers,
 * template-based creation, signature verification, and per-document tag attachment.
 * API failures surface as [com.assinafy.sdk.exceptions.ApiException]; transport failures surface as
 * [com.assinafy.sdk.exceptions.NetworkException].
 *
 * ### The document payload
 *
 * Every operation here that returns a document answers with this `data` shape. Methods below
 * document only what they add to or omit from it.
 *
 * ```json
 * {
 *   "resource": "document",
 *   "id": "615601fab04c0a3147bb1246",
 *   "account_id": "d199996981dbd199996981db",
 *   "template_id": null,
 *   "name": "document.pdf",
 *   "status": "metadata_ready",
 *   "artifacts": {
 *     "original": "https://api.assinafy.com.br/v1/documents/doc1/download/original"
 *   },
 *   "is_closed": false,
 *   "signing_url": "https://api.assinafy.com.br/v1/sign/doc1",
 *   "decline_reason": null,
 *   "declined_by": null,
 *   "tags": [{ "id": "tag-1", "name": "Contracts" }],
 *   "assignment": {
 *     "resource": "assignment",
 *     "id": "615606ef81d199996981dbce",
 *     "sender_email": "sender@example.com",
 *     "method": "virtual",
 *     "expires_at": null,
 *     "message": "Please review and sign",
 *     "signers": [{
 *       "resource": "signer", "id": "62d6ee35c7741ca4006b9e11", "full_name": "John Signer",
 *       "email": "john@example.com", "whatsapp_phone_number": "+5548999990000",
 *       "has_accepted_terms": false, "verification_method": "Email",
 *       "notification_methods": ["Email"], "step": 1, "notified": true, "completed": true,
 *       "notification_history": []
 *     }],
 *     "copy_receivers": [],
 *     "items": [{
 *       "id": "item-1", "page": null, "signer": {}, "field": {},
 *       "display_settings": null, "value": null, "completed": true
 *     }],
 *     "summary": { "signer_count": 1, "completed_count": 1, "signers": [] },
 *     "signing_urls": [{
 *       "signer_id": "62d6ee35c7741ca4006b9e11",
 *       "url": "https://api.assinafy.com.br/v1/sign/doc1?email=john@example.com"
 *     }]
 *   },
 *   "pages": [{
 *     "id": "615601faf166d6d1d8e7dc30", "number": 1, "height": 2100, "width": 1275,
 *     "download_url": "https://api.assinafy.com.br/v1/documents/doc1/pages/1a/download"
 *   }],
 *   "created_at": "2026-06-03T03:54:16Z",
 *   "updated_at": "2026-06-03T03:54:16Z"
 * }
 * ```
 *
 * A list, search, or upload projection omits fields it has not computed yet, which is why those
 * members are nullable on the model.
 */
class DocumentResource internal constructor(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
    private val publicHttp: ApiHttpClient = http,
) : BaseResource(http, defaultAccountId, logger) {

    /**
     * Uploads a PDF (`POST /accounts/{accountId}/documents`, multipart). Validated locally: must be a
     * non-empty `.pdf` ≤ 25 MB. The service accepts at most 2,000 PDF pages.
     *
     * Request body: `multipart/form-data` with one `file` part. When [metadata] is supplied the
     * request also carries `name` and a JSON-string `metadata` part, a deployed-service extension
     * beyond the OpenAPI `file`-only body. Response `data` is the document payload documented on this class, in
     * `uploaded` or `metadata_processing` status with `assignment` and `pages` not yet populated.
     *
     * The current OpenAPI request contains only the `file` part. Supplying [metadata] opts into the
     * legacy extension that also sends `name` and `metadata` form fields.
     *
     * @param fileData Complete PDF bytes, beginning with the `%PDF-` signature.
     * @param fileName Non-blank `.pdf` multipart file name.
     * @param metadata Optional legacy metadata serialized as a JSON string part.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Uploaded document identity and initial processing state.
     * @throws com.assinafy.sdk.exceptions.ValidationException on a non-PDF, empty, or oversized file.
     */
    suspend fun upload(
        fileData: ByteArray,
        fileName: String,
        metadata: Map<String, Any>? = null,
        accountId: String? = null,
    ): DocumentUploadResponse {
        validateUpload(fileData, fileName)
        val id = accountId(accountId)
        logger.info("Uploading document", mapOf("fileName" to fileName, "size" to fileData.size))
        val document = call("Document upload failed", DocumentUploadResponse::class.java) {
            val path = "/accounts/${pathSegment(id)}/documents"
            if (metadata == null) {
                http.postMultipartFile(path, "file", fileName, fileData, "application/pdf")
            } else {
                http.postMultipart(path, fileName, fileData, fileName, toJson(metadata))
            }
        }
        if (document.id.isBlank()) {
            throw ValidationException("Upload succeeded but no document ID was returned")
        }
        logger.info("Document uploaded", mapOf("documentId" to document.id))
        return document
    }

    /**
     * Lists documents (`GET /accounts/{accountId}/documents`).
     *
     * Request body: none. Query: `status`, `method`, `search`, `tags`, `sort`, `page`, `per-page`.
     * Response `data` is an array of the document payload documented on this class, and `X-Pagination-*` headers are
     * exposed through [PaginatedResult.meta].
     *
     * @param params Status, method, search, tag-ID, sort, and pagination filters.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Matching document summaries and optional pagination-header metadata.
     */
    suspend fun list(params: ListParams = ListParams(), accountId: String? = null): PaginatedResult<DocumentListItem> {
        val id = accountId(accountId)
        return callList("Failed to list documents", DocumentListItem::class.java) {
            http.get("/accounts/${pathSegment(id)}/documents", params.toQueryMap())
        }
    }

    /**
     * Lightweight document search (`GET /accounts/{accountId}/documents/search`). Returns the same
     * [DocumentListItem] shape as [list] but only supports `search`/`status`/`page`/`per-page`.
     *
     * Request body: none. Response `data` is an array of the document payload documented on this class.
     *
     * @param query Optional partial document-name search.
     * @param status Optional exact status filter.
     * @param page Optional one-based results page.
     * @param perPage Optional records-per-page limit.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Matching document summaries and optional pagination-header metadata.
     */
    suspend fun search(
        query: String? = null,
        status: String? = null,
        page: Int? = null,
        perPage: Int? = null,
        accountId: String? = null,
    ): PaginatedResult<DocumentListItem> {
        val id = accountId(accountId)
        val params = ListParams(
            page = page,
            perPage = perPage,
            search = query?.takeIf { it.isNotBlank() },
            status = status?.takeIf { it.isNotBlank() },
        ).toQueryMap()
        return callList("Failed to search documents", DocumentListItem::class.java) {
            http.get("/accounts/${pathSegment(id)}/documents/search", params)
        }
    }

    /**
     * Fetches full document details, including assignment and pages (`GET /documents/{documentId}`).
     *
     * Request body: none. Response `data` is the document payload documented on this class, fully populated.
     *
     * @param documentId Stable document identifier.
     * @return Complete document state.
     * @throws ValidationException if [documentId] is blank.
     */
    suspend fun details(documentId: String): DocumentDetails {
        val id = requireId(documentId, "Document ID")
        return call("Failed to fetch document details", DocumentDetails::class.java) {
            http.get("/documents/${pathSegment(id)}")
        }
    }

    /**
     * Alias for [details]; same request and response.
     *
     * @param documentId Stable document identifier.
     * @return Complete document state.
     */
    suspend fun get(documentId: String): DocumentDetails = details(documentId)

    /**
     * Polls [details] until the document reaches a ready status (`metadata_ready`/`pending_signature`/
     * `certificated`). Throws if it reaches a terminal failure status or [maxWaitMs] elapses.
     *
     * Sends no request of its own: repeats `GET /documents/{documentId}` every [pollIntervalMs] and
     * returns the document payload documented on this class from the first ready response. Its budget is independent of
     * the client's per-request timeout because readiness spans several round trips plus server-side
     * PDF processing.
     *
     * @param documentId Stable document identifier.
     * @param maxWaitMs Positive total polling budget in milliseconds.
     * @param pollIntervalMs Positive delay between API requests in milliseconds.
     * @return First document response whose status is in [DocumentStatus.READY].
     * @throws ValidationException for invalid timing values, a terminal failure status, or timeout.
     */
    suspend fun waitUntilReady(
        documentId: String,
        maxWaitMs: Long = SdkConstants.DEFAULT_MAX_WAIT_MS,
        pollIntervalMs: Long = SdkConstants.DEFAULT_POLL_INTERVAL_MS,
    ): DocumentDetails {
        val id = requireId(documentId, "Document ID")
        if (maxWaitMs <= 0) throw ValidationException("Maximum wait must be greater than zero")
        if (pollIntervalMs <= 0) throw ValidationException("Poll interval must be greater than zero")
        var attempts = 0
        logger.info("Waiting for document to be ready", mapOf("documentId" to id, "maxWaitMs" to maxWaitMs))
        val ready = withTimeoutOrNull(maxWaitMs) {
            var document: DocumentDetails
            do {
                attempts++
                document = details(id)
                logger.debug("Document status check", mapOf("attempts" to attempts, "status" to document.status))
                if (document.status in DocumentStatus.FAILED) {
                    throw ValidationException(
                        "Document processing failed with status: ${document.status}",
                        mapOf("status" to document.status),
                    )
                }
                if (document.status !in DocumentStatus.READY) delay(pollIntervalMs)
            } while (document.status !in DocumentStatus.READY)
            document
        }
        if (ready != null) return ready
        throw ValidationException(
            "Timeout waiting for document to be ready",
            mapOf("documentId" to id, "attempts" to attempts),
        )
    }

    /**
     * Downloads a document artifact as raw bytes (`GET /documents/{documentId}/download/{artifactName}`).
     * Defaults to the `certificated` artifact, which is only available once the document is completed;
     * use [DocumentArtifact.ORIGINAL] for the uploaded file.
     *
     * Request body: none. The response is the raw artifact, not the JSON envelope, and its bytes
     * are returned unchanged. An artifact that does not exist yet answers `404`.
     *
     * @param documentId Stable document identifier.
     * @param artifactName Artifact wire value from [DocumentArtifact].
     * @return Unmodified PDF or ZIP response bytes.
     * @throws ValidationException if either identifier is blank.
     */
    suspend fun download(documentId: String, artifactName: String = DocumentArtifact.CERTIFICATED): ByteArray {
        val id = requireId(documentId, "Document ID")
        val artifact = requireId(artifactName, "Artifact name")
        return callBinary("Failed to download document") {
            http.getBinary("/documents/${pathSegment(id)}/download/${pathSegment(artifact)}")
        }
    }

    /**
     * Downloads the document thumbnail (`GET /documents/{documentId}/thumbnail`).
     *
     * Request body: none. The response is the raw image, not the JSON envelope; its bytes are
     * returned unchanged.
     *
     * @param documentId Stable document identifier.
     * @return Unmodified thumbnail image bytes.
     */
    suspend fun thumbnail(documentId: String): ByteArray {
        val id = requireId(documentId, "Document ID")
        return callBinary("Failed to download document thumbnail") {
            http.getBinary("/documents/${pathSegment(id)}/thumbnail")
        }
    }

    /**
     * Downloads a page image (`GET /documents/{documentId}/pages/{pageId}/download`).
     *
     * Request body: none. The response is the raw 150-DPI page image, not the JSON envelope, which
     * is the coordinate space `display_settings` positions collect fields in.
     *
     * @param documentId Stable document identifier.
     * @param pageId Page identifier from [DocumentDetails.pages].
     * @return Unmodified page image bytes.
     */
    suspend fun downloadPage(documentId: String, pageId: String): ByteArray {
        val docId = requireId(documentId, "Document ID")
        val pid = requireId(pageId, "Page ID")
        return callBinary("Failed to download page") {
            http.getBinary("/documents/${pathSegment(docId)}/pages/${pathSegment(pid)}/download")
        }
    }

    /**
     * Returns the document's activity log (`GET /documents/{documentId}/activities`).
     *
     * Request body: none. Response `data` is one entry per recorded event, each carrying a snapshot
     * of the event `payload` and the requesting `origin`:
     * ```json
     * [{
     *   "resource": "activity", "id": "act-1", "event": "signer_signed_document",
     *   "payload": {}, "origin": { "ip": "203.0.113.10", "user-agent": "Mozilla/5.0" },
     *   "created_at": "2026-06-03T03:54:16Z"
     * }]
     * ```
     *
     * @param documentId Stable document identifier.
     * @return Activities in the order returned by the API.
     */
    suspend fun activities(documentId: String): List<DocumentActivity> {
        val id = requireId(documentId, "Document ID")
        val result = callList("Failed to fetch document activities", DocumentActivity::class.java) {
            http.get("/documents/${pathSegment(id)}/activities")
        }
        return result.data
    }

    /**
     * Request body: none. Response `data` is an empty JSON array.
     *
     * Deletes a document (`DELETE /documents/{documentId}`). The service permits deletion only in
     * states whose `GET /documents/statuses` entry advertises `deletable=true`.
     *
     * @param documentId Stable document identifier.
     */
    suspend fun delete(documentId: String) {
        val id = requireId(documentId, "Document ID")
        callVoid("Failed to delete document") { http.delete("/documents/${pathSegment(id)}") }
    }

    /**
     * Renames a document (`PATCH /documents/{documentId}`, body `{"name": ...}`). The service
     * allows rename before assignment while status is `uploaded` or `metadata_ready`, and
     * normalizes diacritics and unsupported characters. The name is required and limited to 255
     * characters. Returns the updated document.
     *
     * Request body: `{"name":"Service agreement.pdf"}`. Response `data` is the document payload documented on this class.
     *
     * @param documentId Stable document identifier.
     * @param name Non-blank replacement name of at most [SdkConstants.MAX_DOCUMENT_NAME_LENGTH] characters.
     * @return Complete updated document.
     * @throws ValidationException if an identifier/name is blank or the name is too long.
     */
    suspend fun rename(documentId: String, name: String): DocumentDetails {
        val id = requireId(documentId, "Document ID")
        val newName = requireId(name, "Document name")
        if (newName.length > SdkConstants.MAX_DOCUMENT_NAME_LENGTH) {
            throw ValidationException(
                "Document name exceeds maximum length (${SdkConstants.MAX_DOCUMENT_NAME_LENGTH})",
                mapOf("length" to newName.length),
            )
        }
        return call("Failed to rename document", DocumentDetails::class.java) {
            http.patch("/documents/${pathSegment(id)}", toJson(mapOf("name" to newName)))
        }
    }

    /**
     * Creates a document from a template
     * (`POST /accounts/{accountId}/templates/{templateId}/documents`).
     *
     * Request body maps one signer to each template role and carries the optional document
     * settings:
     * ```json
     * {
     *   "signers": [{ "role_id": "role-1", "id": "62d6ee35c7741ca4006b9e11" }],
     *   "name": "Agreement for Ada.pdf",
     *   "message": "Please review and sign",
     *   "expires_at": "2026-12-31T23:59:59Z",
     *   "editor_fields": [{ "field_id": "field-1", "value": "Example value" }]
     * }
     * ```
     * Response `data` is the document payload documented on this class, already carrying its assignment.
     *
     * @param templateId Existing template identifier.
     * @param signers Non-empty role-mapped signers; this list replaces [options]' signer list.
     * @param options Optional generated name, message, expiration, editor fields, and tags.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Created document with its generated assignment.
     * @throws ValidationException for blank IDs, missing signers, missing signer IDs, or invalid steps.
     */
    suspend fun createFromTemplate(
        templateId: String,
        signers: List<TemplateSigner>,
        options: CreateDocumentFromTemplateRequest = CreateDocumentFromTemplateRequest(signers = signers),
        accountId: String? = null,
    ): DocumentDetails {
        val tmplId = requireId(templateId, "Template ID")
        val accId = accountId(accountId)
        ApiValidator.requireAtLeastOne(signers, "template signer")
        signers.forEach {
            requireId(it.roleId, "Template role ID")
            requireId(it.id, "Template signer ID")
            ApiValidator.requireValidSignerChannels(it.verificationMethod, it.notificationMethods)
        }
        ApiValidator.requireValidSigningSteps(signers.map(TemplateSigner::step))
        logger.info("Creating document from template", mapOf("templateId" to tmplId, "accountId" to accId))
        val body = toJson(options.copy(signers = signers))
        return call("Failed to create document from template", DocumentDetails::class.java) {
            http.post("/accounts/${pathSegment(accId)}/templates/${pathSegment(tmplId)}/documents", body)
        }
    }

    /**
     * Estimates the credit cost of creating a document from a template
     * (`POST /accounts/{accountId}/templates/{templateId}/documents/estimate-cost`).
     *
     * Request body carries only what affects pricing — each role mapping's verification and
     * notification channels — so signer IDs and steps are omitted. Response `data`:
     * ```json
     * {
     *   "total_credits": 0.45, "document_balance": 12, "credit_balance": 30.5,
     *   "has_sufficient_resources": true, "blocking_reason": null, "message": "",
     *   "breakdown": [{ "code": "NotificationWhatsapp", "quantity": 1, "credits": 0.45 }]
     * }
     * ```
     *
     * @param templateId Existing template identifier.
     * @param signers Non-empty role mappings; signer IDs and signing steps are not sent for pricing.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Typed credit, balance, resource-sufficiency, and pricing-breakdown response.
     * @throws ValidationException for a blank account/template/role ID or an empty signer list.
     */
    suspend fun estimateCostFromTemplate(
        templateId: String,
        signers: List<TemplateSigner>,
        accountId: String? = null,
    ): com.assinafy.sdk.models.CostEstimate {
        val tmplId = requireId(templateId, "Template ID")
        val accId = accountId(accountId)
        ApiValidator.requireAtLeastOne(signers, "template signer")
        val costSigners = signers.map { signer ->
            ApiValidator.requireValidSignerChannels(signer.verificationMethod, signer.notificationMethods)
            buildMap<String, Any> {
                put("role_id", requireId(signer.roleId, "Template role ID"))
                signer.verificationMethod?.let { put("verification_method", it) }
                signer.notificationMethods?.let { put("notification_methods", it) }
            }
        }
        val body = toJson(mapOf("signers" to costSigners))
        return call("Failed to estimate cost from template", com.assinafy.sdk.models.CostEstimate::class.java) {
            http.post("/accounts/${pathSegment(accId)}/templates/${pathSegment(tmplId)}/documents/estimate-cost", body)
        }
    }

    /**
     * Verifies a signed document by signature hash (`GET /documents/{hash}/verify`, public/no-auth).
     *
     * Request body: none. This call always answers HTTP `200`; an unknown hash or unsigned document
     * sets `is_valid` to false, leaves the details null, and explains why in `message`.
     * Response `data`:
     * ```json
     * {
     *   "hash": "FE32EDDADE7CBDDCBB934E7402047450B0E59C02", "id": "63ddb172402799bfc991d10d",
     *   "status": "certificated", "page_count": "1", "signer_count": "1", "completed_count": 1,
     *   "completed_at": "2026-01-27T19:27:44Z", "verified_at": "2026-01-27T19:27:46Z",
     *   "is_valid": true, "message": ""
     * }
     * ```
     *
     * @param hash Verification hash printed in the signed document.
     * @return Server validation result and matching document details when available.
     */
    suspend fun verify(hash: String): DocumentVerification {
        val h = requireId(hash, "Signature hash")
        return call("Failed to verify document", DocumentVerification::class.java) {
            publicHttp.get("/documents/${pathSegment(h)}/verify")
        }
    }

    /**
     * Fetches non-sensitive public document information without account credentials
     * (`GET /public/documents/{documentId}`).
     *
     * Request body: none, and no credential is sent — this uses the client's credential-free
     * transport. Response `data` carries only signer-safe fields:
     * ```json
     * {
     *   "id": "615601fab04c0a3147bb1246", "name": "document.pdf", "status": "pending_signature",
     *   "account_name": "Acme Inc.", "signer_count": 1, "completed_count": 0
     * }
     * ```
     *
     * @param documentId Stable public document identifier.
     * @return Public document identity, status, and signer-safe fields exposed by the API.
     */
    suspend fun getPublic(documentId: String): PublicDocumentInfo {
        val id = requireId(documentId, "Document ID")
        return call("Failed to fetch public document", PublicDocumentInfo::class.java) {
            publicHttp.get("/public/documents/${pathSegment(id)}")
        }
    }

    /**
     * Sends a one-time access token for a public document.
     *
     * Wire request: `PUT /public/documents/{documentId}/send-token` with no credential and the
     * complete body `{"recipient":"signer@example.com","channel":"email"}`. The service requires
     * both keys and answers `400` when either is missing, so the SDK always sends both. The
     * response is the bare success envelope `{"status":200,"message":"..."}`, returned as [Unit].
     *
     * @param documentId Stable public document identifier.
     * @param email Destination for [channel]: an email address for `email`, or the signer's phone
     * number for `whatsapp`. Required; only the `email` channel is format-validated.
     * @param channel Delivery channel, `email` (default) or `whatsapp`.
     * @throws ValidationException for a blank document ID or recipient, an unsupported channel, or
     * a malformed address on the `email` channel.
     */
    suspend fun sendToken(documentId: String, email: String? = null, channel: String? = null) {
        val id = requireId(documentId, "Document ID")
        val tokenChannel = requireId(channel ?: DEFAULT_SEND_TOKEN_CHANNEL, "Token channel").lowercase()
        if (tokenChannel !in SEND_TOKEN_CHANNELS) {
            throw ValidationException("Token channel must be email or whatsapp")
        }
        val recipient = requireId(email, "Token recipient")
        val normalizedRecipient = if (tokenChannel == DEFAULT_SEND_TOKEN_CHANNEL) requireValidEmail(recipient) else recipient
        callVoid("Failed to send signing token") {
            publicHttp.put(
                "/public/documents/${pathSegment(id)}/send-token",
                toJson(mapOf("recipient" to normalizedRecipient, "channel" to tokenChannel)),
            )
        }
    }

    /**
     * Sends no request of its own: derived from [details], so it costs one
     * `GET /documents/{documentId}`.
     *
     * Checks whether every assignment signer has completed, including a certificated document.
     * A `true` result can precede certification artifact readiness while status is `certificating`.
     *
     * @param documentId Stable document identifier.
     * @return `true` for `certificated` status or a non-empty fully completed assignment summary.
     */
    suspend fun isFullySigned(documentId: String): Boolean {
        val doc = details(documentId)
        if (doc.status == DocumentStatus.CERTIFICATED) return true
        val summary = doc.assignment?.summary ?: return false
        return summary.signerCount > 0 && summary.signerCount == summary.completedCount
    }

    /**
     * Derives signing counts and percentage from the current assignment summary.
     *
     * Sends no request of its own: derived from [details], so it costs one
     * `GET /documents/{documentId}` and reads its `assignment.summary`.
     *
     * @param documentId Stable document identifier.
     * @return Signed, total, pending, and percentage values; all zero when no assignment exists.
     */
    suspend fun getSigningProgress(documentId: String): SigningProgress {
        val doc = details(documentId)
        val summary = doc.assignment?.summary
        val total = summary?.signerCount ?: doc.assignment?.signers?.size ?: 0
        val signed = summary?.completedCount ?: 0
        val pending = maxOf(total - signed, 0)
        val percentage = if (total > 0) Math.round((signed.toDouble() / total) * 10_000.0) / 100.0 else 0.0
        return SigningProgress(signed, total, pending, percentage)
    }

    /**
     * Lists the document status catalog (`GET /documents/statuses`).
     *
     * Request body/query: none. Response `data`:
     * `[{"code":"metadata_ready","deletable":true}]`.
     *
     * @return Status identifiers and optional deletable flags.
     */
    suspend fun getStatuses(): List<DocumentStatusInfo> {
        val result = callList("Failed to fetch document statuses", DocumentStatusInfo::class.java) {
            http.get("/documents/statuses")
        }
        return result.data
    }

    /**
     * Sends a legacy signer-data confirmation using the signer's access code. The supplied
     * non-empty map is encoded unchanged; prefer the typed signer-facing resource for the current
     * `full_name`, `email`, and `government_id` schema.
     *
     * Request: `PUT /documents/{documentId}/signers/confirm-data?signer-access-code={code}` with the
     * supplied map as the JSON body. Response `data` is the updated signer record.
     *
     * @param documentId Stable document identifier.
     * @param signerAccessCode One-time signer code sent only in the query string.
     * @param data Non-empty legacy identity map sent unchanged as JSON.
     * @return Updated signer.
     * @throws ValidationException if an identifier is blank or [data] is empty.
     */
    @Deprecated("Use AssinafyClient.signerDocuments.confirmData")
    suspend fun confirmSignerData(
        documentId: String,
        signerAccessCode: String,
        data: Map<String, Any>,
    ): Signer {
        val docId = requireId(documentId, "Document ID")
        val code = requireId(signerAccessCode, "Signer access code")
        if (data.isEmpty()) throw ValidationException("At least one signer identity field is required")
        return call("Failed to confirm signer data", Signer::class.java) {
            publicHttp.put(
                "/documents/${pathSegment(docId)}/signers/confirm-data${queryString("signer-access-code" to code)}",
                toJson(data),
            )
        }
    }

    /**
     * Typed compatibility overload of [confirmSignerData]; unset official fields are omitted and
     * deprecated compatibility properties are ignored.
     *
     * @param documentId Stable document identifier.
     * @param signerAccessCode One-time signer code sent only in the query string.
     * @param request Supported signer identity changes.
     * @return Updated signer.
     */
    @Deprecated("Use AssinafyClient.signerDocuments.confirmData")
    suspend fun confirmSignerData(
        documentId: String,
        signerAccessCode: String,
        request: ConfirmSignerDataRequest,
    ): Signer = SignerDocumentResource(publicHttp).confirmData(documentId, signerAccessCode, request)

    /**
     * Lists tags currently attached to a document
     * (`GET /accounts/{accountId}/documents/{documentId}/tags`).
     *
     * Request body: none. Response `data`:
     * ```json
     * [{
     *   "resource": "tag", "id": "fa8c09f3e709a8a1c82d69b1454", "name": "Contracts",
     *   "color": "ff8800", "created_at": "2026-05-14T12:00:00Z",
     *   "updated_at": "2026-05-14T12:00:00Z"
     * }]
     * ```
     *
     * @param documentId Stable document identifier.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Attached tags.
     */
    suspend fun listTags(documentId: String, accountId: String? = null): List<Tag> {
        val accId = accountId(accountId)
        val docId = requireId(documentId, "Document ID")
        val result = callList("Failed to list document tags", Tag::class.java) {
            http.get("/accounts/${pathSegment(accId)}/documents/${pathSegment(docId)}/tags")
        }
        return result.data
    }

    /**
     * Replaces the document's tag set with [tagNames], whose values are tag IDs in the current
     * OpenAPI. The parameter name is retained because older deployments require names on the same
     * route. Values are sent unchanged; an empty list detaches all tags.
     *
     * Request: `PUT /accounts/{accountId}/documents/{documentId}/tags` with body
     * `{"tags":["fa8c09f3e709a8a1c82d69b1454"]}`. Response `data` is the resulting tag array, in
     * the shape documented by [listTags].
     *
     * @param documentId Stable document identifier.
     * @param tagNames Complete tag-ID set; despite the legacy parameter name these are IDs, not names.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Resulting complete tag set.
     */
    suspend fun replaceTags(documentId: String, tagNames: List<String>, accountId: String? = null): List<Tag> {
        val accId = accountId(accountId)
        val docId = requireId(documentId, "Document ID")
        val result = callList("Failed to replace document tags", Tag::class.java) {
            http.put(
                "/accounts/${pathSegment(accId)}/documents/${pathSegment(docId)}/tags",
                toJson(mapOf("tags" to tagNames)),
            )
        }
        return result.data
    }

    /**
     * Attaches [tagNames] (tag IDs in the current OpenAPI) without removing existing tags. The
     * parameter name is retained because older deployments require names on the same route. Values
     * are sent unchanged; the API returns the resulting tag set.
     *
     * Request: `POST /accounts/{accountId}/documents/{documentId}/tags` with body
     * `{"tags":["fa8c09f3e709a8a1c82d69b1454"]}`. Response `data` is the resulting tag array, in
     * the shape documented by [listTags].
     *
     * @param documentId Stable document identifier.
     * @param tagNames Tag IDs to add; despite the legacy parameter name these are IDs, not names.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Resulting complete tag set.
     */
    suspend fun addTags(documentId: String, tagNames: List<String>, accountId: String? = null): List<Tag> {
        val accId = accountId(accountId)
        val docId = requireId(documentId, "Document ID")
        val result = callList("Failed to attach document tags", Tag::class.java) {
            http.post(
                "/accounts/${pathSegment(accId)}/documents/${pathSegment(docId)}/tags",
                toJson(mapOf("tags" to tagNames)),
            )
        }
        return result.data
    }

    /**
     * Detaches a single tag from a document without deleting the tag
     * (`DELETE /accounts/{accountId}/documents/{documentId}/tags/{tagId}`).
     *
     * Request body: none. Response carries no `data` payload. The tag itself survives; use
     * [com.assinafy.sdk.resources.TagResource.delete] to remove it from the workspace.
     *
     * @param documentId Stable document identifier.
     * @param tagId Stable tag identifier.
     * @param accountId Account override; otherwise the client's default account is used.
     */
    suspend fun detachTag(documentId: String, tagId: String, accountId: String? = null) {
        val accId = accountId(accountId)
        val docId = requireId(documentId, "Document ID")
        val tag = requireId(tagId, "Tag ID")
        callVoid("Failed to detach document tag") {
            http.delete("/accounts/${pathSegment(accId)}/documents/${pathSegment(docId)}/tags/${pathSegment(tag)}")
        }
    }

    private fun validateUpload(fileData: ByteArray, fileName: String) {
        if (fileData.isEmpty()) throw ValidationException("File data is empty", mapOf("fileName" to fileName))
        if (!fileName.lowercase().endsWith(".pdf")) {
            throw ValidationException("Only PDF files are supported", mapOf("fileName" to fileName))
        }
        if (fileData.size > SdkConstants.MAX_UPLOAD_BYTES) {
            throw ValidationException(
                "File size exceeds maximum allowed (25MB)",
                mapOf("fileSize" to fileData.size, "maxSize" to SdkConstants.MAX_UPLOAD_BYTES),
            )
        }
        if (fileData.size < PDF_MAGIC.size || PDF_MAGIC.indices.any { fileData[it] != PDF_MAGIC[it] }) {
            throw ValidationException("File content is not a PDF", mapOf("fileName" to fileName))
        }
    }

    /** PDF upload and token-delivery validation constants. */
    companion object {
        private const val DEFAULT_SEND_TOKEN_CHANNEL = "email"
        private val SEND_TOKEN_CHANNELS = setOf(DEFAULT_SEND_TOKEN_CHANNEL, "whatsapp")
        private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    }
}
