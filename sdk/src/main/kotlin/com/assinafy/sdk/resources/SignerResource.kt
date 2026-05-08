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

class SignerResource(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
) : BaseResource(http, defaultAccountId, logger) {

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
            if (e.statusCode == 409 && normalizedEmail != null) {
                val duplicate = findByEmail(normalizedEmail, id)
                if (duplicate != null) {
                    logger.info("Signer already exists, using existing signer", mapOf("signerId" to duplicate.id))
                    return duplicate
                }
            }
            throw e
        }
    }

    suspend fun get(signerId: String, accountId: String? = null): Signer {
        val id = accountId(accountId)
        val sid = requireId(signerId, "Signer ID")
        return call("Failed to fetch signer", Signer::class.java) {
            http.get("/accounts/${pathSegment(id)}/signers/${pathSegment(sid)}")
        }
    }

    suspend fun list(params: ListParams = ListParams(), accountId: String? = null): PaginatedResult<Signer> {
        val id = accountId(accountId)
        return callList("Failed to list signers", Signer::class.java) {
            http.get("/accounts/${pathSegment(id)}/signers", params.toQueryMap())
        }
    }

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

    suspend fun delete(signerId: String, accountId: String? = null) {
        val id = accountId(accountId)
        val sid = requireId(signerId, "Signer ID")
        callVoid("Failed to delete signer") {
            http.delete("/accounts/${pathSegment(id)}/signers/${pathSegment(sid)}")
        }
    }

    suspend fun findByEmail(email: String, accountId: String? = null): Signer? {
        val normalizedEmail = requireValidEmail(email)
        return try {
            val result = list(ListParams(search = normalizedEmail, perPage = 100), accountId)
            result.data.firstOrNull { signer -> signer.email?.equals(normalizedEmail, ignoreCase = true) == true }
        } catch (e: ApiException) {
            if (e.statusCode == 404) null else throw e
        }
    }

    suspend fun getSelf(signerAccessCode: String): Signer {
        val code = requireId(signerAccessCode, "Signer access code")
        return call("Failed to fetch signer self info", Signer::class.java) {
            http.get("/signers/self", mapOf("signer-access-code" to code))
        }
    }

    suspend fun acceptTerms(signerAccessCode: String): Map<String, Any> {
        val code = requireId(signerAccessCode, "Signer access code")
        val body = mapOf("signer-access-code" to code)
        return callMap("Failed to accept terms") {
            http.put("/signers/accept-terms", toJson(body))
        }
    }

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

    suspend fun uploadSignature(signerAccessCode: String, type: String, imageData: ByteArray) {
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

    private fun normalise(request: CreateSignerRequest): Map<String, Any> =
        buildMap {
            request.fullName?.trim { it.isWhitespace() }?.takeIf { it.isNotEmpty() }?.let { put("full_name", it) }
            request.email?.trim { it.isWhitespace() }?.takeIf { it.isNotEmpty() }?.let { put("email", it) }
            request.whatsappPhoneNumber?.trim { it.isWhitespace() }?.takeIf { it.isNotEmpty() }?.let { put("whatsapp_phone_number", it) }
            request.cpf?.replace("\\D".toRegex(), "")?.takeIf { it.isNotEmpty() }?.let { put("cpf", it) }
            request.metadata?.let { put("metadata", it) }
        }

    private fun normaliseUpdate(request: UpdateSignerRequest): Map<String, Any?> =
        buildMap {
            request.fullName?.trim { it.isWhitespace() }?.takeIf { it.isNotEmpty() }?.let { put("full_name", it) }
            request.email?.trim { it.isWhitespace() }?.takeIf { it.isNotEmpty() }?.let { put("email", it) }
            request.whatsappPhoneNumber?.trim { it.isWhitespace() }?.takeIf { it.isNotEmpty() }?.let { put("whatsapp_phone_number", it) }
            request.cpf?.replace("\\D".toRegex(), "")?.takeIf { it.isNotEmpty() }?.let { put("cpf", it) }
        }
}
