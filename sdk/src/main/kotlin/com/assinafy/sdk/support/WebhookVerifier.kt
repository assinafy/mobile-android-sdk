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
 *
 * @param webhookSecret Optional shared secret used for HMAC verification. This extension is not
 * part of the public REST schema; leave it unset unless the webhook sender is configured to sign.
 */
class WebhookVerifier(private val webhookSecret: String? = null) {

    private val gson = Gson()

    /**
     * Checks a signature against the exact raw delivery bytes using constant-time comparison.
     *
     * @param payload Unmodified request body bytes.
     * @param signature Hex HMAC, optionally prefixed with `sha256=`.
     * @return `true` only for a valid HMAC-SHA256; `false` for malformed signatures or no secret.
     */
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

    /**
     * Checks a signature against the UTF-8 bytes of [payload]. Use the byte overload when the HTTP
     * framework exposes raw bytes so decoding cannot change the signed content.
     *
     * @param payload UTF-8 webhook body.
     * @param signature Hex HMAC, optionally prefixed with `sha256=`.
     * @return `true` only for a valid HMAC-SHA256.
     */
    fun verify(payload: String, signature: String): Boolean = verify(payload.toByteArray(Charsets.UTF_8), signature)

    /**
     * Parses raw UTF-8 webhook bytes without performing signature verification.
     *
     * @param payload Complete webhook request body.
     * @return Parsed envelope, or `null` when the body is not valid for [WebhookPayload].
     */
    fun extractEvent(payload: ByteArray): WebhookPayload? = extractEvent(payload.toString(Charsets.UTF_8))

    /**
     * Parses a webhook JSON string without performing signature verification.
     *
     * @param payload Complete webhook JSON body.
     * @return Parsed envelope, or `null` when JSON decoding fails.
     */
    fun extractEvent(payload: String): WebhookPayload? = try {
        gson.fromJson(payload, WebhookPayload::class.java)
    } catch (e: Exception) {
        null
    }

    /**
     * Resolves the current `event` field with the legacy `type` field as fallback.
     *
     * @param event Parsed webhook envelope, or `null`.
     * @return Event identifier, or `null` when neither field is present.
     */
    fun getEventType(event: WebhookPayload?): String? = event?.event ?: event?.type

    /**
     * Returns the event-specific `payload` object.
     *
     * @param event Parsed webhook envelope, or `null`.
     * @return Payload map, or an empty map when absent.
     */
    fun getEventData(event: WebhookPayload?): Map<String, Any> = event?.payload ?: emptyMap()

    private fun computeHmac(data: ByteArray, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }
}
