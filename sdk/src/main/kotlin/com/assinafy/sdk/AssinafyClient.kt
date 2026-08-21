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
import com.assinafy.sdk.resources.AuthenticationResource
import com.assinafy.sdk.resources.DocumentResource
import com.assinafy.sdk.resources.FieldResource
import com.assinafy.sdk.resources.SignerDocumentResource
import com.assinafy.sdk.resources.SignerResource
import com.assinafy.sdk.resources.TagResource
import com.assinafy.sdk.resources.TemplateResource
import com.assinafy.sdk.resources.UserResource
import com.assinafy.sdk.resources.WebhookResource
import com.assinafy.sdk.resources.WorkspaceResource
import com.assinafy.sdk.support.WebhookVerifier
import com.assinafy.sdk.util.requireValidEmail
import java.net.URI

/**
 * Result of [AssinafyClient.uploadAndRequestSignatures].
 *
 * @property document Current document after assignment creation when the request asks for a refresh,
 * or the initial upload snapshot otherwise.
 * @property assignment Signature assignment created for [signerIds].
 * @property signerIds IDs of the signers resolved or created by the workflow, in request order.
 */
data class UploadAndRequestSignaturesResult(
    val document: DocumentUploadResponse,
    val assignment: Assignment,
    val signerIds: List<String>,
)

/**
 * Entry point to the Assinafy API. Construct it via [AssinafyClient.create]; the resource groups
 * and [webhookVerifier] are exposed as properties. All network methods are `suspend` functions.
 * A credentialless [AssinafyClientConfig] can use login and public signer/document operations;
 * account operations return the API's authentication error until a credentialed client is built.
 *
 * @property authentication Human-user authentication and API-key operations.
 * @property documents Account and public document operations.
 * @property signers Account signer management and legacy signer-facing operations.
 * @property signerDocuments Signer-facing document and signing operations.
 * @property workspaces Account/workspace operations.
 * @property assignments Assignment creation, pricing, notification, and rejection operations.
 * @property fields Account field-definition and validation operations.
 * @property users Authenticated-user profile, statistics, and notification preferences.
 * @property webhooks Webhook subscription and delivery-history operations.
 * @property templates Template read operations.
 * @property tags Account tag operations.
 * @property webhookVerifier Optional local webhook HMAC verification and payload parsing.
 */
class AssinafyClient internal constructor(
    val authentication: AuthenticationResource,
    val documents: DocumentResource,
    val signers: SignerResource,
    val signerDocuments: SignerDocumentResource,
    val workspaces: WorkspaceResource,
    val assignments: AssignmentResource,
    val fields: FieldResource,
    val users: UserResource,
    val webhooks: WebhookResource,
    val templates: TemplateResource,
    val tags: TagResource,
    val webhookVerifier: WebhookVerifier,
    private val logger: Logger,
) {
    /**
     * High-level workflow: validates every signer, uploads a PDF, waits for metadata processing,
     * reuses-or-creates each signer by email, and creates a `virtual` assignment for them.
     *
     * @param request PDF, signer list, optional assignment settings, and optional account override.
     * @return the uploaded document, the created assignment, and the resolved signer ids.
     * @throws com.assinafy.sdk.exceptions.ValidationException if no signers are given or a signer
     * is missing a name or email.
     */
    suspend fun uploadAndRequestSignatures(request: UploadAndRequestSignaturesRequest): UploadAndRequestSignaturesResult {
        validateUploadRequest(request)
        logger.info("Starting upload + signature workflow", mapOf("signerCount" to request.signers.size))

        val upload = documents.upload(
            request.fileData,
            request.fileName,
            request.metadata,
            request.accountId,
        )
        documents.waitUntilReady(upload.id)

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
            upload.id,
            CreateAssignmentRequest(
                method = AssignmentMethod.VIRTUAL,
                signers = signerIds.map { SignerReference.ofId(it) },
                message = request.message,
                expiresAt = request.expiresAt,
                copyReceivers = request.copyReceivers,
            ),
        )
        val document = if (request.waitForReady) documents.details(upload.id) else upload
        logger.info("Upload + signature workflow completed", mapOf("documentId" to upload.id))

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
            requireValidEmail(signer.email)
        }
    }

    /** Factory methods for validated SDK clients. */
    companion object Factory {
        /**
         * Creates an API-key-authenticated client from individual settings.
         *
         * @param apiKey API key sent only to the configured API origin.
         * @param accountId Default account used by account-scoped resource methods.
         * @param baseUrl API root, including `/v1`; credentials require HTTPS except on loopback.
         * @param webhookSecret Optional secret used only by [WebhookVerifier].
         * @param timeoutMs Positive per-request connect, read, and write timeout in milliseconds.
         * @param logger Optional logging sink; secrets and payload bodies are not logged by the SDK.
         * @return A configured client exposing all SDK resources.
         * @throws ValidationException if the URL, credentials, or timeout are invalid.
         */
        fun create(
            apiKey: String,
            accountId: String,
            baseUrl: String = SdkConstants.DEFAULT_BASE_URL,
            webhookSecret: String? = null,
            timeoutMs: Long = SdkConstants.DEFAULT_TIMEOUT_MS,
            logger: Logger? = null,
        ): AssinafyClient {
            if (apiKey.isBlank()) throw ValidationException("API key is required")
            if (accountId.isBlank()) throw ValidationException("Account ID is required")
            val config = AssinafyClientConfig(
                apiKey = apiKey.trim(),
                accountId = accountId.trim(),
                baseUrl = baseUrl,
                webhookSecret = webhookSecret,
                timeoutMs = timeoutMs,
                logger = logger,
            )
            return create(config)
        }

        /**
         * Builds a client from [config]. Credentials may be omitted for login and public signing
         * routes, but account-scoped calls then fail with the API's authentication response.
         *
         * @param config Complete transport, authentication, account, logging, and webhook settings.
         * @return A configured client exposing all SDK resources.
         * @throws ValidationException if both credential types are set, the URL is invalid or
         * insecure for credentials, or the timeout is not positive.
         */
        fun create(config: AssinafyClientConfig): AssinafyClient {
            // Validate before constructing the HTTP client so an invalid config surfaces as a
            // ValidationException rather than a lower-level OkHttp error (e.g. a negative timeout).
            validateConfig(config)
            val authenticatedHttp = createHttpClient(config, includeCredentials = true)
            val publicHttp = if (config.apiKey.isNullOrBlank() && config.token.isNullOrBlank()) {
                authenticatedHttp
            } else {
                createHttpClient(config, includeCredentials = false)
            }
            return create(config, authenticatedHttp, publicHttp)
        }

        internal fun create(config: AssinafyClientConfig, httpClient: ApiHttpClient): AssinafyClient =
            create(config, httpClient, httpClient)

        internal fun create(
            config: AssinafyClientConfig,
            httpClient: ApiHttpClient,
            publicHttpClient: ApiHttpClient,
        ): AssinafyClient {
            validateConfig(config)
            val logger = config.logger ?: NoOpLogger
            return AssinafyClient(
                authentication = AuthenticationResource(
                    httpClient,
                    config.accountId,
                    logger,
                    publicHttpClient,
                ),
                documents = DocumentResource(httpClient, config.accountId, logger, publicHttpClient),
                signers = SignerResource(httpClient, config.accountId, logger, publicHttpClient),
                signerDocuments = SignerDocumentResource(publicHttpClient),
                workspaces = WorkspaceResource(httpClient, null, logger),
                assignments = AssignmentResource(httpClient, config.accountId, logger, publicHttpClient),
                fields = FieldResource(httpClient, config.accountId, logger),
                users = UserResource(httpClient, config.accountId, logger),
                webhooks = WebhookResource(httpClient, config.accountId, logger),
                templates = TemplateResource(httpClient, config.accountId, logger),
                tags = TagResource(httpClient, config.accountId, logger),
                webhookVerifier = WebhookVerifier(config.webhookSecret),
                logger = logger,
            )
        }

        private fun createHttpClient(config: AssinafyClientConfig, includeCredentials: Boolean): ApiHttpClient = OkHttpApiClient(
            config.baseUrl,
            config.apiKey?.trim()?.takeIf { includeCredentials },
            config.token?.trim()?.takeIf { includeCredentials },
            config.timeoutMs,
        )

        private fun validateConfig(config: AssinafyClientConfig) {
            val hasApiKey = !config.apiKey.isNullOrBlank()
            val hasToken = !config.token.isNullOrBlank()
            if (hasApiKey && hasToken) throw ValidationException("Provide either an API key or a token, not both")
            val uri = try {
                URI(config.baseUrl.trim())
            } catch (e: Exception) {
                throw ValidationException("Base URL must be an absolute HTTP(S) URL")
            }
            val scheme = uri.scheme?.lowercase()
            if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
                throw ValidationException("Base URL must be an absolute HTTP(S) URL")
            }
            if ((hasApiKey || hasToken) && scheme != "https" && uri.host !in LOOPBACK_HOSTS) {
                throw ValidationException("Credentials require an HTTPS base URL")
            }
            if (config.timeoutMs <= 0) {
                throw ValidationException("Timeout must be greater than zero")
            }
        }

        private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1")
    }
}
