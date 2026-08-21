package com.assinafy.sdk.resources

import com.assinafy.sdk.DocumentArtifact
import com.assinafy.sdk.DocumentStatus
import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.SdkConstants
import com.assinafy.sdk.exceptions.ApiException
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
 */
class DocumentResource internal constructor(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
    private val publicHttp: ApiHttpClient = http,
) : BaseResource(http, defaultAccountId, logger) {

    /**
     * Uploads a PDF (`POST /accounts/{accountId}/documents`, multipart). Validated locally: must be a
     * non-empty `.pdf` ≤ 25 MB.
     *
     * The current OpenAPI request contains only the `file` part. Supplying [metadata] opts into the
     * legacy sandbox extension that also sends `name` and `metadata` form fields.
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
     * Alias for [details].
     *
     * @param documentId Stable document identifier.
     * @return Complete document state.
     */
    suspend fun get(documentId: String): DocumentDetails = details(documentId)

    /**
     * Polls [details] until the document reaches a ready status (`metadata_ready`/`pending_signature`/
     * `certificated`). Throws if it reaches a terminal failure status or [maxWaitMs] elapses.
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
     * Deletes a document (`DELETE /documents/{documentId}`).
     *
     * @param documentId Stable document identifier.
     */
    suspend fun delete(documentId: String) {
        val id = requireId(documentId, "Document ID")
        callVoid("Failed to delete document") { http.delete("/documents/${pathSegment(id)}") }
    }

    /**
     * Renames a document (`PATCH /documents/{documentId}`, body `{"name": ...}`). The name is
     * required and limited to 255 characters. Returns the updated document.
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
        }
        ApiValidator.requireValidSigningSteps(signers.map(TemplateSigner::step))
        logger.info("Creating document from template", mapOf("templateId" to tmplId, "accountId" to accId))
        val body = toJson(options.copy(signers = signers))
        return call("Failed to create document from template", DocumentDetails::class.java) {
            http.post("/accounts/${pathSegment(accId)}/templates/${pathSegment(tmplId)}/documents", body)
        }
    }

    /**
     * Estimates the credit cost of creating a document from a template.
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
     * Fetches non-sensitive public document information without account credentials.
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
     * Sends the public signing token. The OpenAPI request is empty or `{"email":"..."}`. Passing
     * [channel] explicitly uses the deployed compatibility body `{"recipient":"...","channel":
     * "email|whatsapp"}`. The documented email request is retried in that form only when a 400/422
     * response specifically reports a missing `channel` or `recipient`.
     *
     * @param documentId Stable public document identifier.
     * @param email Optional validated destination; omit it to use the document's configured recipient.
     * @param channel Optional deployed-service `email` or `whatsapp` delivery channel.
     * @throws ValidationException for a blank document ID, invalid email/channel, or missing explicit recipient.
     */
    suspend fun sendToken(documentId: String, email: String? = null, channel: String? = null) {
        val id = requireId(documentId, "Document ID")
        val path = "/public/documents/${pathSegment(id)}/send-token"
        val normalizedChannel = channel?.trim()?.lowercase()
        if (normalizedChannel != null) {
            if (normalizedChannel !in SEND_TOKEN_CHANNELS) {
                throw ValidationException("Token channel must be email or whatsapp")
            }
            val recipient = email?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw ValidationException("Token recipient is required when channel is provided")
            val normalizedRecipient = if (normalizedChannel == "email") requireValidEmail(recipient) else recipient
            return callVoid("Failed to send signing token") {
                publicHttp.put(path, toJson(mapOf("recipient" to normalizedRecipient, "channel" to normalizedChannel)))
            }
        }

        val normalizedEmail = email?.let(::requireValidEmail)
        try {
            callVoid("Failed to send signing token") {
                publicHttp.put(path, normalizedEmail?.let { toJson(mapOf("email" to it)) })
            }
        } catch (error: ApiException) {
            if (normalizedEmail == null || !error.isLegacySendTokenValidation()) throw error
            callVoid("Failed to send signing token") {
                publicHttp.put(path, toJson(mapOf("recipient" to normalizedEmail, "channel" to "email")))
            }
        }
    }

    private fun ApiException.isLegacySendTokenValidation(): Boolean {
        if (statusCode != 400 && statusCode != 422) return false
        val detail = "$message $responseData".lowercase()
        return "channel" in detail || "recipient" in detail
    }

    /**
     * Checks whether certification is complete or every assignment signer has completed.
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
     * @return Status identifiers, descriptions, and deletable flags.
     */
    suspend fun getStatuses(): List<DocumentStatusInfo> {
        val result = callList("Failed to fetch document statuses", DocumentStatusInfo::class.java) {
            http.get("/documents/statuses")
        }
        return result.data
    }

    /**
     * Confirms a signer's contact data and terms acceptance using their access code.
     * Body keys: `email`, `whatsapp_phone_number`, `has_accepted_terms`.
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
     * Typed compatibility overload of [confirmSignerData]; unset fields are omitted.
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
     * Lists tags currently attached to a document.
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
     * Detaches a single tag from a document without deleting the tag.
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

    /** PDF upload validation constants. */
    companion object {
        private val SEND_TOKEN_CHANNELS = setOf("email", "whatsapp")
        private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    }
}
