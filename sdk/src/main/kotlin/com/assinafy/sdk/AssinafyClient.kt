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
import com.assinafy.sdk.resources.TagResource
import com.assinafy.sdk.resources.TemplateResource
import com.assinafy.sdk.resources.WebhookResource
import com.assinafy.sdk.resources.WorkspaceResource
import com.assinafy.sdk.support.WebhookVerifier

/** Result of [AssinafyClient.uploadAndRequestSignatures]: the uploaded document, the created assignment, and the signer ids. */
data class UploadAndRequestSignaturesResult(
    val document: DocumentUploadResponse,
    val assignment: Assignment,
    val signerIds: List<String>,
)

/**
 * Entry point to the Assinafy API. Construct it via [AssinafyClient.create]; the resource groups
 * ([documents], [signers], [assignments], [webhooks], [templates], [tags], [workspaces]) and the
 * [webhookVerifier] are exposed as properties. All network methods are `suspend` functions.
 */
class AssinafyClient internal constructor(
    val documents: DocumentResource,
    val signers: SignerResource,
    val workspaces: WorkspaceResource,
    val assignments: AssignmentResource,
    val webhooks: WebhookResource,
    val templates: TemplateResource,
    val tags: TagResource,
    val webhookVerifier: WebhookVerifier,
    private val logger: Logger,
) {
    /**
     * High-level workflow: uploads a PDF, optionally waits for it to become ready, reuses-or-creates
     * each signer by email, and creates a `virtual` assignment for them.
     *
     * @return the uploaded document, the created assignment, and the resolved signer ids.
     * @throws com.assinafy.sdk.exceptions.ValidationException if no signers are given or a signer is missing name/email.
     */
    suspend fun uploadAndRequestSignatures(request: UploadAndRequestSignaturesRequest): UploadAndRequestSignaturesResult {
        validateUploadRequest(request)
        logger.info("Starting upload + signature workflow", mapOf("signerCount" to request.signers.size))

        val document = documents.upload(
            request.fileData,
            request.fileName,
            request.metadata,
            request.accountId,
        )

        if (request.waitForReady) {
            documents.waitUntilReady(document.id)
        }

        val signerIds = request.signers.map { signer ->
            val created = signers.create(
                CreateSignerRequest(
                    fullName = signer.name.trim(),
                    email = signer.email.trim(),
                    whatsappPhoneNumber = signer.whatsappPhoneNumber,
                    cpf = signer.cpf,
                    metadata = signer.metadata,
                ),
                request.accountId,
            )
            created.id
        }

        val assignment = assignments.create(
            document.id,
            CreateAssignmentRequest(
                method = AssignmentMethod.VIRTUAL,
                signers = signerIds.map { SignerReference.ofId(it) },
                message = request.message,
                expiresAt = request.expiresAt,
                copyReceivers = request.copyReceivers,
            ),
        )
        logger.info("Upload + signature workflow completed", mapOf("documentId" to document.id))

        return UploadAndRequestSignaturesResult(document, assignment, signerIds)
    }

    private fun validateUploadRequest(request: UploadAndRequestSignaturesRequest) {
        if (request.signers.isEmpty()) {
            throw ValidationException("At least one signer is required")
        }
        request.signers.forEachIndexed { index, signer ->
            if (signer.name.isBlank()) {
                throw ValidationException("Signer name is required", mapOf("index" to index))
            }
            if (signer.email.isBlank()) {
                throw ValidationException("Signer email is required", mapOf("index" to index))
            }
        }
    }

    companion object Factory {
        /** Convenience factory from individual parameters. See [AssinafyClientConfig] for details. */
        fun create(
            apiKey: String,
            accountId: String,
            baseUrl: String = SdkConstants.DEFAULT_BASE_URL,
            webhookSecret: String? = null,
            timeoutMs: Long = SdkConstants.DEFAULT_TIMEOUT_MS,
            logger: Logger? = null,
        ): AssinafyClient {
            val config = AssinafyClientConfig(
                apiKey = apiKey,
                accountId = accountId,
                baseUrl = baseUrl,
                webhookSecret = webhookSecret,
                timeoutMs = timeoutMs,
                logger = logger,
            )
            return create(config)
        }

        /** Builds a client from a [AssinafyClientConfig]; validates the config and wires the resources. */
        fun create(config: AssinafyClientConfig): AssinafyClient {
            // Validate before constructing the HTTP client so an invalid config surfaces as a
            // ValidationException rather than a lower-level OkHttp error (e.g. a negative timeout).
            validateConfig(config)
            return create(config, createHttpClient(config))
        }

        internal fun create(config: AssinafyClientConfig, httpClient: ApiHttpClient): AssinafyClient {
            validateConfig(config)
            val logger = config.logger ?: NoOpLogger
            return AssinafyClient(
                documents = DocumentResource(httpClient, config.accountId, logger),
                signers = SignerResource(httpClient, config.accountId, logger),
                workspaces = WorkspaceResource(httpClient, null, logger),
                assignments = AssignmentResource(httpClient, config.accountId, logger),
                webhooks = WebhookResource(httpClient, config.accountId, logger),
                templates = TemplateResource(httpClient, config.accountId, logger),
                tags = TagResource(httpClient, config.accountId, logger),
                webhookVerifier = WebhookVerifier(config.webhookSecret),
                logger = logger,
            )
        }

        private fun createHttpClient(config: AssinafyClientConfig): ApiHttpClient = OkHttpApiClient(
            config.baseUrl,
            config.apiKey,
            config.token,
            config.timeoutMs,
        )

        private fun validateConfig(config: AssinafyClientConfig) {
            if (config.apiKey.isNullOrBlank() && config.token.isNullOrBlank()) {
                throw ValidationException(
                    "An API key (config.apiKey) or legacy access token (config.token) is required.",
                )
            }
            if (config.baseUrl.isBlank()) {
                throw ValidationException("Base URL is required")
            }
            if (config.timeoutMs <= 0) {
                throw ValidationException("Timeout must be greater than zero")
            }
        }
    }
}
