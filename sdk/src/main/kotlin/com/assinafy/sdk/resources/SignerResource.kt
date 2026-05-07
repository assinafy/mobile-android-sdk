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
import com.assinafy.sdk.util.ResponseHandler

private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

class SignerResource(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

    suspend fun create(request: CreateSignerRequest, accountId: String? = null): Signer {
        request.email?.let { assertEmail(it) }
        val id = accountId(accountId)
        if (request.email != null) {
            val existing = findByEmail(request.email, id)
            if (existing != null) {
                logger.info("Using existing signer", mapOf("email" to request.email))
                return existing
            }
        }
        logger.info("Creating signer", mapOf("email" to (request.email ?: "none")))
        return call("Failed to create signer", Signer::class.java) {
            http.post("/accounts/$id/signers", ResponseHandler.GSON.toJson(normalise(request)))
        }
    }

    suspend fun get(signerId: String, accountId: String? = null): Signer {
        val id = accountId(accountId)
        val sid = requireId(signerId, "Signer ID")
        return call("Failed to fetch signer", Signer::class.java) {
            http.get("/accounts/$id/signers/$sid")
        }
    }

    suspend fun list(params: ListParams = ListParams(), accountId: String? = null): PaginatedResult<Signer> {
        val id = accountId(accountId)
        return callList("Failed to list signers", Signer::class.java) {
            http.get("/accounts/$id/signers", params.toQueryMap())
        }
    }

    suspend fun update(signerId: String, request: UpdateSignerRequest, accountId: String? = null): Signer {
        val id = accountId(accountId)
        val sid = requireId(signerId, "Signer ID")
        return call("Failed to update signer", Signer::class.java) {
            http.put("/accounts/$id/signers/$sid", ResponseHandler.GSON.toJson(normaliseUpdate(request)))
        }
    }

    suspend fun delete(signerId: String, accountId: String? = null) {
        val id = accountId(accountId)
        val sid = requireId(signerId, "Signer ID")
        callVoid("Failed to delete signer") {
            http.delete("/accounts/$id/signers/$sid")
        }
    }

    suspend fun findByEmail(email: String, accountId: String? = null): Signer? {
        assertEmail(email)
        return try {
            val result = list(ListParams(search = email, perPage = 100), accountId)
            result.data.firstOrNull { signer -> signer.email?.equals(email, ignoreCase = true) == true }
        } catch (e: ApiException) {
            if (e.statusCode == 404) null else throw e
        }
    }

    private fun assertEmail(email: String) {
        if (email.isBlank() || !EMAIL_REGEX.matches(email)) {
            throw ValidationException("Invalid email address", mapOf("email" to email))
        }
    }

    private fun normaliseSignerInput(
        fullName: String?,
        email: String?,
        whatsappPhoneNumber: String?,
    ): Map<String, Any> = buildMap {
        fullName?.let { put("full_name", it) }
        email?.let { put("email", it) }
        whatsappPhoneNumber?.let { put("whatsapp_phone_number", it) }
    }

    private fun normalise(request: CreateSignerRequest): Map<String, Any> =
        normaliseSignerInput(request.fullName, request.email, request.whatsappPhoneNumber)

    private fun normaliseUpdate(request: UpdateSignerRequest): Map<String, Any?> =
        normaliseSignerInput(request.fullName, request.email, request.whatsappPhoneNumber)
}
