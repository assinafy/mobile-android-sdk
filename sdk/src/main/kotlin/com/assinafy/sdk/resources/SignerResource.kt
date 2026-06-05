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
 */
class SignerResource(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
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
        val normalizedEmail = request.email?.let { requireValidEmail(it) }
        val normalizedRequest = request.copy(email = normalizedEmail)
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

    /** Fetches a signer by id (`GET /accounts/{accountId}/signers/{signerId}`). */
    suspend fun get(signerId: String, accountId: String? = null): Signer {
        val id = accountId(accountId)
        val sid = requireId(signerId, "Signer ID")
        return call("Failed to fetch signer", Signer::class.java) {
            http.get("/accounts/${pathSegment(id)}/signers/${pathSegment(sid)}")
        }
    }

    /** Lists signers (`GET /accounts/{accountId}/signers`), supporting `search`/`page`/`per-page`. */
    suspend fun list(params: ListParams = ListParams(), accountId: String? = null): PaginatedResult<Signer> {
        val id = accountId(accountId)
        return callList("Failed to list signers", Signer::class.java) {
            http.get("/accounts/${pathSegment(id)}/signers", params.toQueryMap())
        }
    }

    /** Updates a signer (`PUT /accounts/{accountId}/signers/{signerId}`); the email, if present, is validated. */
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

    /** Deletes a signer (`DELETE /accounts/{accountId}/signers/{signerId}`). */
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

    /** Signer-facing: fetches the calling signer's own record (`GET /signers/self?signer-access-code=`). */
    suspend fun getSelf(signerAccessCode: String): Signer {
        val code = requireId(signerAccessCode, "Signer access code")
        return call("Failed to fetch signer self info", Signer::class.java) {
            http.get("/signers/self", mapOf("signer-access-code" to code))
        }
    }

    /** Signer-facing: records terms acceptance (`PUT /signers/accept-terms`). */
    suspend fun acceptTerms(signerAccessCode: String): Map<String, Any> {
        val code = requireId(signerAccessCode, "Signer access code")
        val body = mapOf("signer-access-code" to code)
        return callMap("Failed to accept terms") {
            http.put("/signers/accept-terms", toJson(body))
        }
    }

    /** Signer-facing: verifies the signer's email/WhatsApp with a code (`POST /verify`). */
    suspend fun verifyEmail(signerAccessCode: String, verificationCode: String): Map<String, Any> {
        val code = requireId(signerAccessCode, "Signer access code")
        val verification = requireId(verificationCode, "Verification code")
        val body = mapOf(
            "signer-access-code" to code,
            "verification-code" to verification,
        )
        return callMap("Failed to verify email") {
            http.post("/verify", toJson(body))
        }
    }

    /**
     * Uploads the signer's signature or initial image.
     *
     * @param type `"signature"` or `"initial"`.
     * @param imageData Raw PNG or JPEG bytes.
     * @param contentType MIME type of [imageData] — `image/png` (default) or `image/jpeg`.
     */
    suspend fun uploadSignature(
        signerAccessCode: String,
        type: String,
        imageData: ByteArray,
        contentType: String = "image/png",
    ) {
        val code = requireId(signerAccessCode, "Signer access code")
        val signatureType = requireId(type, "Signature type")
        if (imageData.isEmpty()) {
            throw ValidationException("Signature image data is empty")
        }
        logger.info("Uploading signature", mapOf("type" to signatureType))
        callVoid("Failed to upload signature") {
            http.postSignature(
                "/signature${queryString("signer-access-code" to code, "type" to signatureType)}",
                imageData,
                contentType,
            )
        }
    }

    suspend fun downloadSignature(signerAccessCode: String, type: String): ByteArray {
        val code = requireId(signerAccessCode, "Signer access code")
        val signatureType = requireId(type, "Signature type")
        return callBinary("Failed to download signature") {
            http.getBinary("/signature/${pathSegment(signatureType)}${queryString("signer-access-code" to code)}")
        }
    }

    private fun normalise(request: CreateSignerRequest): Map<String, Any> = buildMap {
        request.fullName?.trim { it.isWhitespace() }?.takeIf { it.isNotEmpty() }?.let { put("full_name", it) }
        request.email?.trim { it.isWhitespace() }?.takeIf { it.isNotEmpty() }?.let { put("email", it) }
        request.whatsappPhoneNumber?.trim { it.isWhitespace() }?.takeIf { it.isNotEmpty() }?.let { put("whatsapp_phone_number", it) }
        request.cpf?.replace("\\D".toRegex(), "")?.takeIf { it.isNotEmpty() }?.let { put("cpf", it) }
        request.metadata?.let { put("metadata", it) }
    }

    private fun normaliseUpdate(request: UpdateSignerRequest): Map<String, Any?> = buildMap {
        request.fullName?.trim { it.isWhitespace() }?.takeIf { it.isNotEmpty() }?.let { put("full_name", it) }
        request.email?.trim { it.isWhitespace() }?.takeIf { it.isNotEmpty() }?.let { put("email", it) }
        request.whatsappPhoneNumber?.trim { it.isWhitespace() }?.takeIf { it.isNotEmpty() }?.let { put("whatsapp_phone_number", it) }
        request.cpf?.replace("\\D".toRegex(), "")?.takeIf { it.isNotEmpty() }?.let { put("cpf", it) }
    }
}
