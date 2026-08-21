package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.ApiException
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.PaginatedResult
import com.assinafy.sdk.models.Signer
import com.assinafy.sdk.request.CreateSignerRequest
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.UpdateSignerRequest
import com.assinafy.sdk.util.requireValidEmail

private val DUPLICATE_STATUS_CODES = setOf(400, 409)

/**
 * Signer management. Account-scoped CRUD ([create], [get], [list], [update], [delete], [findByEmail])
 * is authenticated by the API key; the signer-facing flows ([getSelf], [acceptTerms], [verifyEmail],
 * [uploadSignature], [downloadSignature]) are authenticated by a per-signer access code.
 * API failures surface as [ApiException]; transport failures surface as
 * [com.assinafy.sdk.exceptions.NetworkException].
 */
class SignerResource internal constructor(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
    private val publicHttp: ApiHttpClient = http,
) : BaseResource(http, defaultAccountId, logger) {

    /**
     * Creates a signer (`POST /accounts/{accountId}/signers`). Idempotent by email: if a signer with
     * the same email already exists in the workspace it is returned instead of creating a duplicate,
     * and the API's duplicate-email error (HTTP 400) is recovered by re-fetching the existing signer.
     *
     * @param request signer details; [CreateSignerRequest.email] is validated and normalized.
     * @param accountId overrides the client's default account.
     * @return the created or pre-existing [Signer].
     * @throws com.assinafy.sdk.exceptions.ValidationException on an invalid email or missing account.
     */
    suspend fun create(request: CreateSignerRequest, accountId: String? = null): Signer {
        val fullName = requireId(request.fullName, "Signer full name")
        val normalizedEmail = request.email?.let { requireValidEmail(it) }
        val normalizedRequest = request.copy(fullName = fullName, email = normalizedEmail)
        val id = accountId(accountId)
        if (normalizedEmail != null) {
            val existing = findByEmail(normalizedEmail, id)
            if (existing != null) {
                logger.info("Using existing signer", mapOf("signerId" to existing.id))
                return existing
            }
        }
        logger.info("Creating signer", mapOf("hasEmail" to (normalizedEmail != null)))
        return try {
            call("Failed to create signer", Signer::class.java) {
                http.post("/accounts/${pathSegment(id)}/signers", toJson(normalise(normalizedRequest)))
            }
        } catch (e: ApiException) {
            // The API rejects a duplicate email with 400 (and historically 409). If we lost a race
            // with a concurrent create, fall back to the existing signer instead of failing.
            if (e.statusCode in DUPLICATE_STATUS_CODES && normalizedEmail != null) {
                val duplicate = findByEmail(normalizedEmail, id)
                if (duplicate != null) {
                    logger.info("Signer already exists, using existing signer", mapOf("signerId" to duplicate.id))
                    return duplicate
                }
            }
            throw e
        }
    }

    /**
     * Fetches a signer by ID (`GET /accounts/{accountId}/signers/{signerId}`).
     *
     * @param signerId Stable signer identifier.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Complete account signer record.
     * @throws ValidationException if an account or signer ID is blank.
     */
    suspend fun get(signerId: String, accountId: String? = null): Signer {
        val id = accountId(accountId)
        val sid = requireId(signerId, "Signer ID")
        return call("Failed to fetch signer", Signer::class.java) {
            http.get("/accounts/${pathSegment(id)}/signers/${pathSegment(sid)}")
        }
    }

    /**
     * Lists signers (`GET /accounts/{accountId}/signers`).
     *
     * @param params Search and pagination values; unsupported common filters are ignored.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Matching signer records and optional pagination-header metadata.
     */
    suspend fun list(params: ListParams = ListParams(), accountId: String? = null): PaginatedResult<Signer> {
        val id = accountId(accountId)
        val query = buildMap<String, Any> {
            params.search?.let { put("search", it) }
            params.page?.let { put("page", it) }
            params.perPage?.let { put("per-page", it) }
        }
        return callList("Failed to list signers", Signer::class.java) {
            http.get("/accounts/${pathSegment(id)}/signers", query)
        }
    }

    /**
     * Updates a signer (`PUT /accounts/{accountId}/signers/{signerId}`).
     *
     * @param signerId Stable signer identifier.
     * @param request Mutable identity/contact fields; null fields are omitted.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Complete updated signer record.
     * @throws ValidationException if an ID is blank or a supplied email is invalid.
     */
    suspend fun update(signerId: String, request: UpdateSignerRequest, accountId: String? = null): Signer {
        val id = accountId(accountId)
        val sid = requireId(signerId, "Signer ID")
        val normalizedRequest = request.copy(email = request.email?.let { requireValidEmail(it) })
        return call("Failed to update signer", Signer::class.java) {
            http.put(
                "/accounts/${pathSegment(id)}/signers/${pathSegment(sid)}",
                toJson(normaliseUpdate(normalizedRequest)),
            )
        }
    }

    /**
     * Deletes a signer (`DELETE /accounts/{accountId}/signers/{signerId}`).
     *
     * @param signerId Stable signer identifier.
     * @param accountId Account override; otherwise the client's default account is used.
     */
    suspend fun delete(signerId: String, accountId: String? = null) {
        val id = accountId(accountId)
        val sid = requireId(signerId, "Signer ID")
        callVoid("Failed to delete signer") {
            http.delete("/accounts/${pathSegment(id)}/signers/${pathSegment(sid)}")
        }
    }

    /**
     * Finds a signer by exact (case-insensitive) email, paging through the `search` results.
     * Returns `null` if none matches.
     *
     * @param email Valid email to match after trimming.
     * @param accountId Account override; otherwise the client's default account is used.
     * @return Exact signer match, or `null` when no page contains one.
     * @throws ValidationException if the email or account is invalid.
     */
    suspend fun findByEmail(email: String, accountId: String? = null): Signer? {
        val normalizedEmail = requireValidEmail(email)
        var page = 1
        while (true) {
            val result = try {
                list(ListParams(search = normalizedEmail, perPage = 100, page = page), accountId)
            } catch (e: ApiException) {
                if (e.statusCode == 404) return null else throw e
            }
            val match = result.data.firstOrNull { it.email?.equals(normalizedEmail, ignoreCase = true) == true }
            if (match != null) return match
            val lastPage = result.meta?.lastPage
            // Stop when pagination meta is absent (single page), the last page is reached, or the page is empty.
            if (lastPage == null || page >= lastPage || result.data.isEmpty()) return null
            page++
        }
    }

    /**
     * Fetches the current signer through a public signing-link access code.
     *
     * @param signerAccessCode One-time signer code sent only in the query string.
     * @return Current signer identity and signature state returned by the API.
     */
    @Deprecated("Use AssinafyClient.signerDocuments.self")
    suspend fun getSelf(signerAccessCode: String): Signer = SignerDocumentResource(publicHttp).self(signerAccessCode).let {
        Signer(
            id = it.id,
            fullName = it.fullName,
            email = it.email,
            whatsappPhoneNumber = it.whatsappPhoneNumber,
            hasAcceptedTerms = it.hasAcceptedTerms,
            hasSignature = it.hasSignature,
            hasInitial = it.hasInitial,
            isSignatureReusable = it.isSignatureReusable,
        )
    }

    /**
     * Records signer terms acceptance (`PUT /signers/accept-terms`).
     *
     * @param signerAccessCode One-time signer code sent only in the query string.
     * @return API success data normalized to a map.
     */
    @Deprecated("Use AssinafyClient.signerDocuments.acceptTerms")
    suspend fun acceptTerms(signerAccessCode: String): Map<String, Any> {
        val code = requireId(signerAccessCode, "Signer access code")
        return callMap("Failed to accept terms") {
            publicHttp.put("/signers/accept-terms${queryString("signer-access-code" to code)}")
        }
    }

    /**
     * Verifies the signer's email or WhatsApp one-time password (`POST /verify`).
     *
     * @param signerAccessCode One-time signer code sent only in the query string.
     * @param verificationCode Verification value sent using the exact `verification-code` JSON key.
     * @return API success data normalized to a map.
     */
    @Deprecated("Use AssinafyClient.signerDocuments.verifyEmail")
    suspend fun verifyEmail(signerAccessCode: String, verificationCode: String): Map<String, Any> {
        val code = requireId(signerAccessCode, "Signer access code")
        val verification = requireId(verificationCode, "Verification code")
        val body = mapOf("verification-code" to verification)
        return callMap("Failed to verify email") {
            publicHttp.post("/verify${queryString("signer-access-code" to code)}", toJson(body))
        }
    }

    /**
     * Uploads the signer's signature or initial image.
     *
     * @param type `"signature"` or `"initial"`.
     * @param imageData Raw PNG or legacy JPEG bytes.
     * @param contentType MIME type of [imageData]. The current API documents `image/png`; JPEG is
     * retained for compatibility with older deployments.
     * @param reuse Optional instruction to update whether the image can be reused.
     * @throws ValidationException if the access code/type is blank, bytes are empty, or MIME type is unsupported.
     */
    @Deprecated("Use AssinafyClient.signerDocuments.uploadSignature; JPEG is a legacy extension")
    suspend fun uploadSignature(
        signerAccessCode: String,
        type: String,
        imageData: ByteArray,
        contentType: String = "image/png",
        reuse: Boolean? = null,
    ) {
        val code = requireId(signerAccessCode, "Signer access code")
        val signatureType = requireId(type, "Signature type")
        if (imageData.isEmpty()) {
            throw ValidationException("Signature image data is empty")
        }
        if (contentType !in setOf("image/png", "image/jpeg")) {
            throw ValidationException("Signature content type must be image/png or image/jpeg")
        }
        logger.info("Uploading signature", mapOf("type" to signatureType))
        if (contentType == "image/png") {
            return SignerDocumentResource(publicHttp).uploadSignature(code, imageData, signatureType, reuse)
        }
        callVoid("Failed to upload signature") {
            publicHttp.postSignature(
                "/signature${queryString("signer-access-code" to code, "type" to signatureType, "reuse" to reuse)}",
                imageData,
                contentType,
            )
        }
    }

    /**
     * Downloads a signer's stored signature or initials as raw image bytes.
     *
     * @param signerAccessCode One-time signer code sent only in the query string.
     * @param type Stored image kind, normally [com.assinafy.sdk.SignatureType.SIGNATURE] or
     * [com.assinafy.sdk.SignatureType.INITIAL].
     * @return Raw PNG or legacy JPEG response bytes.
     * @throws ValidationException if the signer access code or image type is blank.
     */
    @Deprecated("Use AssinafyClient.signerDocuments.downloadSignature")
    suspend fun downloadSignature(signerAccessCode: String, type: String): ByteArray =
        SignerDocumentResource(publicHttp).downloadSignature(signerAccessCode, type)

    private fun normalise(request: CreateSignerRequest): Map<String, Any> = buildMap {
        request.fullName?.trim()?.takeIf { it.isNotEmpty() }?.let { put("full_name", it) }
        request.email?.trim()?.takeIf { it.isNotEmpty() }?.let { put("email", it) }
        request.whatsappPhoneNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { put("whatsapp_phone_number", it) }
        @Suppress("DEPRECATION")
        request.cpf?.replace("\\D".toRegex(), "")?.takeIf { it.isNotEmpty() }?.let { put("cpf", it) }
        @Suppress("DEPRECATION")
        request.metadata?.let { put("metadata", it) }
    }

    private fun normaliseUpdate(request: UpdateSignerRequest): Map<String, Any?> = buildMap {
        request.fullName?.trim()?.takeIf { it.isNotEmpty() }?.let { put("full_name", it) }
        request.email?.trim()?.takeIf { it.isNotEmpty() }?.let { put("email", it) }
        request.whatsappPhoneNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { put("whatsapp_phone_number", it) }
        request.governmentId?.replace("\\D".toRegex(), "")?.takeIf { it.isNotEmpty() }?.let { put("government_id", it) }
        @Suppress("DEPRECATION")
        request.cpf?.replace("\\D".toRegex(), "")?.takeIf { it.isNotEmpty() }?.let { put("cpf", it) }
    }
}
