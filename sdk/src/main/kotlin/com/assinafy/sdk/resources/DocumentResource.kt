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
import com.assinafy.sdk.request.CreateDocumentFromTemplateRequest
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.TemplateSigner
import kotlinx.coroutines.delay

class DocumentResource(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

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

    suspend fun list(params: ListParams = ListParams(), accountId: String? = null): PaginatedResult<DocumentListItem> {
        val id = accountId(accountId)
        return callList("Failed to list documents", DocumentListItem::class.java) {
            http.get("/accounts/${pathSegment(id)}/documents", params.toQueryMap())
        }
    }

    suspend fun details(documentId: String): DocumentDetails {
        val id = requireId(documentId, "Document ID")
        return call("Failed to fetch document details", DocumentDetails::class.java) {
            http.get("/documents/${pathSegment(id)}")
        }
    }

    suspend fun get(documentId: String): DocumentDetails = details(documentId)

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

    suspend fun download(documentId: String, artifactName: String = DocumentArtifact.CERTIFICATED): ByteArray {
        val id = requireId(documentId, "Document ID")
        val artifact = requireId(artifactName, "Artifact name")
        return callBinary("Failed to download document") {
            http.getBinary("/documents/${pathSegment(id)}/download/${pathSegment(artifact)}")
        }
    }

    suspend fun thumbnail(documentId: String): ByteArray {
        val id = requireId(documentId, "Document ID")
        return callBinary("Failed to download document thumbnail") {
            http.getBinary("/documents/${pathSegment(id)}/thumbnail")
        }
    }

    suspend fun downloadPage(documentId: String, pageId: String): ByteArray {
        val docId = requireId(documentId, "Document ID")
        val pid = requireId(pageId, "Page ID")
        return callBinary("Failed to download page") {
            http.getBinary("/documents/${pathSegment(docId)}/pages/${pathSegment(pid)}/download")
        }
    }

    suspend fun activities(documentId: String): List<DocumentActivity> {
        val id = requireId(documentId, "Document ID")
        val result = callList("Failed to fetch document activities", DocumentActivity::class.java) {
            http.get("/documents/${pathSegment(id)}/activities")
        }
        return result.data
    }

    suspend fun delete(documentId: String) {
        val id = requireId(documentId, "Document ID")
        callVoid("Failed to delete document") { http.delete("/documents/${pathSegment(id)}") }
    }

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

    suspend fun verify(hash: String): Map<String, Any> {
        val h = requireId(hash, "Signature hash")
        return callMap("Failed to verify document") { http.get("/documents/${pathSegment(h)}/verify") }
    }

    suspend fun isFullySigned(documentId: String): Boolean {
        val doc = details(documentId)
        if (doc.status in DocumentStatus.READY) return true
        val summary = doc.assignment?.summary ?: return false
        return summary.signerCount > 0 && summary.signerCount == summary.completedCount
    }

    suspend fun getSigningProgress(documentId: String): SigningProgress {
        val doc = details(documentId)
        val summary = doc.assignment?.summary
        val total = summary?.signerCount ?: doc.assignment?.signers?.size ?: 0
        val signed = summary?.completedCount ?: 0
        val pending = maxOf(total - signed, 0)
        val percentage = if (total > 0) Math.round((signed.toDouble() / total) * 10_000.0) / 100.0 else 0.0
        return SigningProgress(signed, total, pending, percentage)
    }

    suspend fun getStatuses(): List<DocumentStatusInfo> {
        val result = callList("Failed to fetch document statuses", DocumentStatusInfo::class.java) {
            http.get("/documents/statuses")
        }
        return result.data
    }

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