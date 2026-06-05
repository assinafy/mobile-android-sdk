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
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.models.SigningProgress
import com.assinafy.sdk.models.Tag
import com.assinafy.sdk.request.ConfirmSignerDataRequest
import com.assinafy.sdk.request.CreateDocumentFromTemplateRequest
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.TemplateSigner
import kotlinx.coroutines.delay

/**
 * Document operations: upload, list/fetch, readiness polling, downloads, activities, status helpers,
 * template-based creation, signature verification, and per-document tag attachment.
 */
class DocumentResource(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    /**
     * Uploads a PDF (`POST /accounts/{accountId}/documents`, multipart). Validated locally: must be a
     * non-empty `.pdf` ≤ 25 MB.
     *
     * @param metadata optional metadata serialized as a JSON string part.
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
            http.postMultipart(
                "/accounts/${pathSegment(id)}/documents",
                fileName,
                fileData,
                fileName,
                metadata?.let { toJson(it) },
            )
        }
        if (document.id.isBlank()) {
            throw ValidationException("Upload succeeded but no document ID was returned")
        }
        logger.info("Document uploaded", mapOf("documentId" to document.id))
        return document
    }

    /** Lists documents (`GET /accounts/{accountId}/documents`), supporting `status`/`method`/`search`/`tags`/`sort`/`page`/`per-page`. */
    suspend fun list(params: ListParams = ListParams(), accountId: String? = null): PaginatedResult<DocumentListItem> {
        val id = accountId(accountId)
        return callList("Failed to list documents", DocumentListItem::class.java) {
            http.get("/accounts/${pathSegment(id)}/documents", params.toQueryMap())
        }
    }

    /** Fetches full document details including its assignment and pages (`GET /documents/{documentId}`). */
    suspend fun details(documentId: String): DocumentDetails {
        val id = requireId(documentId, "Document ID")
        return call("Failed to fetch document details", DocumentDetails::class.java) {
            http.get("/documents/${pathSegment(id)}")
        }
    }

    /** Alias for [details]. */
    suspend fun get(documentId: String): DocumentDetails = details(documentId)

    /**
     * Polls [details] until the document reaches a ready status (`metadata_ready`/`pending_signature`/
     * `certificated`). Throws if it reaches a terminal failure status or [maxWaitMs] elapses.
     */
    suspend fun waitUntilReady(
        documentId: String,
        maxWaitMs: Long = SdkConstants.DEFAULT_MAX_WAIT_MS,
        pollIntervalMs: Long = SdkConstants.DEFAULT_POLL_INTERVAL_MS,
    ): DocumentDetails {
        val id = requireId(documentId, "Document ID")
        val deadline = System.currentTimeMillis() + maxWaitMs
        var attempts = 0
        logger.info("Waiting for document to be ready", mapOf("documentId" to id, "maxWaitMs" to maxWaitMs))
        while (System.currentTimeMillis() < deadline) {
            attempts++
            try {
                val doc = details(id)
                logger.debug("Document status check", mapOf("attempts" to attempts, "status" to doc.status))
                if (doc.status in DocumentStatus.READY) return doc
                if (doc.status in DocumentStatus.FAILED) {
                    throw ValidationException(
                        "Document processing failed with status: ${doc.status}",
                        mapOf("status" to doc.status),
                    )
                }
            } catch (e: ValidationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Error checking document status", mapOf("error" to (e.message ?: "")))
            }
            delay(pollIntervalMs)
        }
        throw ValidationException(
            "Timeout waiting for document to be ready",
            mapOf("documentId" to id, "attempts" to attempts),
        )
    }

    /**
     * Downloads a document artifact as raw bytes (`GET /documents/{documentId}/download/{artifactName}`).
     * Defaults to the `certificated` artifact, which is only available once the document is completed;
     * use [DocumentArtifact.ORIGINAL] for the uploaded file.
     */
    suspend fun download(documentId: String, artifactName: String = DocumentArtifact.CERTIFICATED): ByteArray {
        val id = requireId(documentId, "Document ID")
        val artifact = requireId(artifactName, "Artifact name")
        return callBinary("Failed to download document") {
            http.getBinary("/documents/${pathSegment(id)}/download/${pathSegment(artifact)}")
        }
    }

    /** Downloads the document thumbnail image as raw bytes (`GET /documents/{documentId}/thumbnail`). */
    suspend fun thumbnail(documentId: String): ByteArray {
        val id = requireId(documentId, "Document ID")
        return callBinary("Failed to download document thumbnail") {
            http.getBinary("/documents/${pathSegment(id)}/thumbnail")
        }
    }

    /** Downloads a single page image as raw bytes (`GET /documents/{documentId}/pages/{pageId}/download`). */
    suspend fun downloadPage(documentId: String, pageId: String): ByteArray {
        val docId = requireId(documentId, "Document ID")
        val pid = requireId(pageId, "Page ID")
        return callBinary("Failed to download page") {
            http.getBinary("/documents/${pathSegment(docId)}/pages/${pathSegment(pid)}/download")
        }
    }

    /** Returns the document's activity/audit log (`GET /documents/{documentId}/activities`). */
    suspend fun activities(documentId: String): List<DocumentActivity> {
        val id = requireId(documentId, "Document ID")
        val result = callList("Failed to fetch document activities", DocumentActivity::class.java) {
            http.get("/documents/${pathSegment(id)}/activities")
        }
        return result.data
    }

    /** Deletes a document (`DELETE /documents/{documentId}`). */
    suspend fun delete(documentId: String) {
        val id = requireId(documentId, "Document ID")
        callVoid("Failed to delete document") { http.delete("/documents/${pathSegment(id)}") }
    }

    /**
     * Creates a document from a template
     * (`POST /accounts/{accountId}/templates/{templateId}/documents`).
     *
     * @param signers role-mapped signers; the same list is sent (the [options] copy is overwritten with it).
     */
    suspend fun createFromTemplate(
        templateId: String,
        signers: List<TemplateSigner>,
        options: CreateDocumentFromTemplateRequest = CreateDocumentFromTemplateRequest(signers = signers),
        accountId: String? = null,
    ): DocumentDetails {
        val tmplId = requireId(templateId, "Template ID")
        val accId = accountId(accountId)
        logger.info("Creating document from template", mapOf("templateId" to tmplId, "accountId" to accId))
        val body = toJson(options.copy(signers = signers))
        return call("Failed to create document from template", DocumentDetails::class.java) {
            http.post("/accounts/${pathSegment(accId)}/templates/${pathSegment(tmplId)}/documents", body)
        }
    }

    /** Estimates the credit cost of creating a document from a template (`POST .../templates/{id}/documents/estimate-cost`). */
    suspend fun estimateCostFromTemplate(
        templateId: String,
        signers: List<TemplateSigner>,
        accountId: String? = null,
    ): Map<String, Any> {
        val tmplId = requireId(templateId, "Template ID")
        val accId = accountId(accountId)
        val body = toJson(mapOf("signers" to signers))
        return callMap("Failed to estimate cost from template") {
            http.post("/accounts/${pathSegment(accId)}/templates/${pathSegment(tmplId)}/documents/estimate-cost", body)
        }
    }

    /** Verifies a signed document by its signature hash (`GET /documents/{hash}/verify`, public/no-auth). */
    suspend fun verify(hash: String): Map<String, Any> {
        val h = requireId(hash, "Signature hash")
        return callMap("Failed to verify document") { http.get("/documents/${pathSegment(h)}/verify") }
    }

    /** True once the document is `certificated` or every signer in its assignment summary has completed. */
    suspend fun isFullySigned(documentId: String): Boolean {
        val doc = details(documentId)
        if (doc.status == DocumentStatus.CERTIFICATED) return true
        val summary = doc.assignment?.summary ?: return false
        return summary.signerCount > 0 && summary.signerCount == summary.completedCount
    }

    /** Returns signing progress (signed/total/pending counts and percentage) derived from the assignment summary. */
    suspend fun getSigningProgress(documentId: String): SigningProgress {
        val doc = details(documentId)
        val summary = doc.assignment?.summary
        val total = summary?.signerCount ?: doc.assignment?.signers?.size ?: 0
        val signed = summary?.completedCount ?: 0
        val pending = maxOf(total - signed, 0)
        val percentage = if (total > 0) Math.round((signed.toDouble() / total) * 10_000.0) / 100.0 else 0.0
        return SigningProgress(signed, total, pending, percentage)
    }

    /** Lists the document status catalog and which statuses are deletable (`GET /documents/statuses`). */
    suspend fun getStatuses(): List<DocumentStatusInfo> {
        val result = callList("Failed to fetch document statuses", DocumentStatusInfo::class.java) {
            http.get("/documents/statuses")
        }
        return result.data
    }

    /**
     * Confirms a signer's contact data and terms acceptance using their access code.
     * Body keys: `email`, `whatsapp_phone_number`, `has_accepted_terms`.
     */
    suspend fun confirmSignerData(
        documentId: String,
        signerAccessCode: String,
        data: Map<String, Any>,
    ) {
        val docId = requireId(documentId, "Document ID")
        val code = requireId(signerAccessCode, "Signer access code")
        callVoid("Failed to confirm signer data") {
            http.put(
                "/documents/${pathSegment(docId)}/signers/confirm-data${queryString("signer-access-code" to code)}",
                toJson(data),
            )
        }
    }

    /** Typed overload of [confirmSignerData]; unset fields are omitted from the request body. */
    suspend fun confirmSignerData(
        documentId: String,
        signerAccessCode: String,
        request: ConfirmSignerDataRequest,
    ) = confirmSignerData(
        documentId,
        signerAccessCode,
        buildMap {
            request.email?.let { put("email", it) }
            request.whatsappPhoneNumber?.let { put("whatsapp_phone_number", it) }
            request.hasAcceptedTerms?.let { put("has_accepted_terms", it) }
        },
    )

    /** Lists the tags currently attached to a document. */
    suspend fun listTags(documentId: String, accountId: String? = null): List<Tag> {
        val accId = accountId(accountId)
        val docId = requireId(documentId, "Document ID")
        val result = callList("Failed to list document tags", Tag::class.java) {
            http.get("/accounts/${pathSegment(accId)}/documents/${pathSegment(docId)}/tags")
        }
        return result.data
    }

    /**
     * Replaces the document's tag set with [tagNames]. Names that don't yet exist are created
     * automatically. An empty list detaches all tags. Returns the resulting tag set.
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
     * Attaches [tagNames] to a document without removing existing tags (idempotent). Unknown names
     * are created automatically. Returns the resulting tag set.
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

    /** Detaches a single tag from a document. The tag itself is not deleted. */
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
    }
}
