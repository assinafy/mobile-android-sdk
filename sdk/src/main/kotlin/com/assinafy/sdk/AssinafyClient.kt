package com.assinafy.sdk

import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.http.OkHttpApiClient
import com.assinafy.sdk.models.Assignment
import com.assinafy.sdk.models.DocumentUploadResponse
import com.assinafy.sdk.request.CreateAssignmentRequest
import com.assinafy.sdk.request.CreateSignerRequest
import com.assinafy.sdk.request.SignerReference
import com.assinafy.sdk.request.UploadAndRequestSignaturesRequest
import com.assinafy.sdk.resources.AssignmentResource
import com.assinafy.sdk.resources.DocumentResource
import com.assinafy.sdk.resources.SignerResource
import com.assinafy.sdk.resources.TemplateResource
import com.assinafy.sdk.resources.WebhookResource
import com.assinafy.sdk.resources.WorkspaceResource
import com.assinafy.sdk.support.WebhookVerifier

data class UploadAndRequestSignaturesResult(
    val document: DocumentUploadResponse,
    val assignment: Assignment,
    val signerIds: List<String>,
)

class AssinafyClient private constructor(
    private val logger: Logger,
    val documents: DocumentResource,
    val signers: SignerResource,
    val workspaces: WorkspaceResource,
    val assignments: AssignmentResource,
    val webhooks: WebhookResource,
    val templates: TemplateResource,
    val webhookVerifier: WebhookVerifier,
) {
    constructor(config: AssinafyClientConfig) : this(
        config = config,
        http = OkHttpApiClient(
            normaliseBaseUrl(config.baseUrl),
            config.apiKey,
            config.token,
            config.timeoutMs,
        ),
    )

    internal constructor(config: AssinafyClientConfig, http: ApiHttpClient) : this(
        logger = config.logger ?: NoOpLogger,
        documents = DocumentResource(http, config.accountId, config.logger ?: NoOpLogger),
        signers = SignerResource(http, config.accountId, config.logger ?: NoOpLogger),
        workspaces = WorkspaceResource(http, null, config.logger ?: NoOpLogger),
        assignments = AssignmentResource(http, config.accountId, config.logger ?: NoOpLogger),
        webhooks = WebhookResource(http, config.accountId, config.logger ?: NoOpLogger),
        templates = TemplateResource(http, config.accountId, config.logger ?: NoOpLogger),
        webhookVerifier = WebhookVerifier(config.webhookSecret),
    ) {
        if (config.apiKey.isNullOrBlank() && config.token.isNullOrBlank()) {
            throw ValidationException(
                "An API key (config.apiKey) or legacy access token (config.token) is required.",
            )
        }
    }

    suspend fun uploadAndRequestSignatures(request: UploadAndRequestSignaturesRequest): UploadAndRequestSignaturesResult {
        if (request.signers.isEmpty()) {
            throw ValidationException("At least one signer is required")
        }
        logger.info("Starting upload + signature workflow", mapOf("signerCount" to request.signers.size))

        val document: DocumentUploadResponse = documents.upload(
            request.fileData,
            request.fileName,
            request.metadata,
            request.accountId,
        )

        if (request.waitForReady) {
            documents.waitUntilReady(document.id)
        }

        val signerIds = mutableListOf<String>()
        for (entry in request.signers) {
            val created = signers.create(
                CreateSignerRequest(
                    fullName = entry.name,
                    email = entry.email,
                    whatsappPhoneNumber = entry.whatsappPhoneNumber,
                ),
                request.accountId,
            )
            signerIds.add(created.id)
        }

        val assignment = assignments.create(
            document.id,
            CreateAssignmentRequest(
                method = "virtual",
                signers = signerIds.map { SignerReference.ofId(it) },
                message = request.message,
                expiresAt = request.expiresAt,
                copyReceivers = request.copyReceivers,
            ),
        )
        logger.info("Upload + signature workflow completed", mapOf("documentId" to document.id))

        return UploadAndRequestSignaturesResult(document, assignment, signerIds)
    }

    companion object {
        fun create(
            apiKey: String,
            accountId: String,
            config: AssinafyClientConfig = AssinafyClientConfig(),
        ): AssinafyClient = AssinafyClient(config.copy(apiKey = apiKey, accountId = accountId))

        internal fun normaliseBaseUrl(url: String): String =
            if (url.endsWith("/")) url.dropLast(1) else url
    }
}
