package com.assinafy.sdk.support

import com.assinafy.sdk.models.WebhookPayload
import com.google.gson.Gson
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Verifies and parses webhook deliveries. [verify] checks an HMAC-SHA256 signature against the raw
 * body using the configured secret (constant-time comparison; accepts an optional `sha256=` prefix
 * and any hex case). [extractEvent] parses the JSON envelope into a [WebhookPayload] regardless of
 * whether a secret is configured. Intended to run on a JVM/server receiver — keep the secret server-side.
 */
class WebhookVerifier(private val webhookSecret: String? = null) {

    private val gson = Gson()

    /** Returns true iff [signature] is a valid HMAC-SHA256 of [payload]; false if no secret is configured. */
    fun verify(payload: ByteArray, signature: String): Boolean {
        if (webhookSecret.isNullOrBlank() || signature.isBlank()) return false
        val expected = computeHmac(payload, webhookSecret)
        val trimmed = signature.trim()
        val provided = if (trimmed.startsWith("sha256=", ignoreCase = true)) {
            trimmed.substringAfter("=")
        } else {
            trimmed
        }.lowercase()
        if (expected.length != provided.length) return false
        return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), provided.toByteArray(Charsets.UTF_8))
    }

    fun verify(payload: String, signature: String): Boolean = verify(payload.toByteArray(Charsets.UTF_8), signature)

    fun extractEvent(payload: ByteArray): WebhookPayload? = extractEvent(payload.toString(Charsets.UTF_8))

    fun extractEvent(payload: String): WebhookPayload? = try {
        gson.fromJson(payload, WebhookPayload::class.java)
    } catch (e: Exception) {
        null
    }

    fun getEventType(event: WebhookPayload?): String? = event?.event ?: event?.type

    fun getEventData(event: WebhookPayload?): Map<String, Any> = event?.payload ?: emptyMap()

    private fun computeHmac(data: ByteArray, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }
}
