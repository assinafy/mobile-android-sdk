package com.assinafy.sdk.support

import com.assinafy.sdk.models.WebhookPayload
import com.assinafy.sdk.util.Base64Codec
import com.google.gson.Gson
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Verifies and parses webhook deliveries on a JVM/server receiver. Keep the secret server-side.
 *
 * [verifySignature] checks the [Standard Webhooks](https://www.standardwebhooks.com) signature that
 * Assinafy attaches when an endpoint has `signing_enabled: true`: HMAC-SHA256 over
 * `{webhook-id}.{webhook-timestamp}.{raw body}`, keyed with the base64 part of the endpoint's
 * `whsec_` secret (see [com.assinafy.sdk.resources.WebhookResource.getEndpointSecret]).
 * [extractEvent] parses the JSON body into a [WebhookPayload] without checking anything.
 *
 * ```kotlin
 * val verifier = WebhookVerifier(endpointSecret) // "whsec_..."
 * val ok = verifier.verifySignature(
 *     payload = rawBody,
 *     webhookId = request.header(WebhookVerifier.HEADER_ID),
 *     webhookTimestamp = request.header(WebhookVerifier.HEADER_TIMESTAMP),
 *     webhookSignature = request.header(WebhookVerifier.HEADER_SIGNATURE),
 * )
 * if (!ok) return respond(401)
 * val event = verifier.extractEvent(rawBody)
 * ```
 *
 * @param webhookSecret The endpoint's signing secret, `whsec_` followed by base64; the prefix is optional.
 */
class WebhookVerifier(private val webhookSecret: String? = null) {

    private val gson = Gson()

    /**
     * Checks a signature against the exact raw delivery bytes using constant-time comparison.
     *
     * Assinafy deliveries carry no hex HMAC; signed deliveries use the Standard Webhooks headers
     * checked by [verifySignature].
     *
     * @param payload Unmodified request body bytes.
     * @param signature Hex HMAC, optionally prefixed with `sha256=`.
     * @return `true` only for a valid HMAC-SHA256; `false` for malformed signatures or no secret.
     */
    @Deprecated("Assinafy signs deliveries with Standard Webhooks headers", ReplaceWith("verifySignature(payload, webhookId, webhookTimestamp, webhookSignature)"))
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
    @Deprecated("Assinafy signs deliveries with Standard Webhooks headers", ReplaceWith("verifySignature(payload, webhookId, webhookTimestamp, webhookSignature)"))
    @Suppress("DEPRECATION")
    fun verify(payload: String, signature: String): Boolean = verify(payload.toByteArray(Charsets.UTF_8), signature)

    /**
     * Verifies a signed delivery against its `webhook-id`, `webhook-timestamp` and
     * `webhook-signature` headers.
     *
     * Accepts the delivery when any space-separated `v1,<base64>` entry of [webhookSignature]
     * matches (constant-time), and [webhookTimestamp] is within [toleranceSeconds] of
     * [nowEpochSeconds] so a captured delivery cannot be replayed later.
     *
     * @param payload Raw request body bytes, exactly as received; never re-serialized JSON.
     * @param webhookId Value of the `webhook-id` header; also the key to deduplicate retries.
     * @param webhookTimestamp Value of the `webhook-timestamp` header, Unix seconds.
     * @param webhookSignature Value of the `webhook-signature` header.
     * @param toleranceSeconds Largest accepted clock distance; defaults to five minutes.
     * @param nowEpochSeconds Current Unix time in seconds; override only in tests.
     * @return `true` only for an authentic, fresh delivery; `false` for missing headers, a missing
     *   or malformed secret, a stale timestamp, or no matching signature.
     */
    fun verifySignature(
        payload: ByteArray,
        webhookId: String?,
        webhookTimestamp: String?,
        webhookSignature: String?,
        toleranceSeconds: Long = DEFAULT_TOLERANCE_SECONDS,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ): Boolean {
        if (webhookId.isNullOrBlank() || webhookSignature.isNullOrBlank()) return false
        val timestampText = webhookTimestamp?.trim().orEmpty()
        val timestamp = timestampText.toLongOrNull() ?: return false
        if (abs(nowEpochSeconds - timestamp) > toleranceSeconds) return false
        val key = webhookSecret?.trim()?.removePrefix(SECRET_PREFIX)?.let(Base64Codec::decode)
            ?.takeIf { it.isNotEmpty() } ?: return false
        val signed = "$webhookId.$timestampText.".toByteArray(Charsets.UTF_8) + payload
        val expected = (SIGNATURE_VERSION + Base64Codec.encode(hmacSha256(key, signed))).toByteArray(Charsets.UTF_8)
        return webhookSignature.split(' ').any { MessageDigest.isEqual(expected, it.toByteArray(Charsets.UTF_8)) }
    }

    /**
     * [verifySignature] for a body already decoded as UTF-8. Prefer the byte overload when the
     * HTTP framework exposes raw bytes, so decoding cannot change the signed content.
     *
     * @param payload UTF-8 webhook body.
     * @param webhookId Value of the `webhook-id` header.
     * @param webhookTimestamp Value of the `webhook-timestamp` header, Unix seconds.
     * @param webhookSignature Value of the `webhook-signature` header.
     * @param toleranceSeconds Largest accepted clock distance; defaults to five minutes.
     * @param nowEpochSeconds Current Unix time in seconds; override only in tests.
     * @return `true` only for an authentic, fresh delivery.
     */
    fun verifySignature(
        payload: String,
        webhookId: String?,
        webhookTimestamp: String?,
        webhookSignature: String?,
        toleranceSeconds: Long = DEFAULT_TOLERANCE_SECONDS,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ): Boolean = verifySignature(
        payload.toByteArray(Charsets.UTF_8),
        webhookId,
        webhookTimestamp,
        webhookSignature,
        toleranceSeconds,
        nowEpochSeconds,
    )

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

    private fun computeHmac(data: ByteArray, secret: String): String =
        hmacSha256(secret.toByteArray(Charsets.UTF_8), data).joinToString("") { "%02x".format(it) }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /** Header names and defaults of the Standard Webhooks delivery contract. */
    companion object {
        /** Message ID, identical on every attempt of the same event to the same endpoint. */
        const val HEADER_ID = "webhook-id"

        /** Unix timestamp (seconds) of the delivery attempt. */
        const val HEADER_TIMESTAMP = "webhook-timestamp"

        /** Space-separated `v1,<base64>` signatures; present only when signing is enabled. */
        const val HEADER_SIGNATURE = "webhook-signature"

        /** Default replay window: deliveries older or newer than five minutes are rejected. */
        const val DEFAULT_TOLERANCE_SECONDS = 300L

        private const val SECRET_PREFIX = "whsec_"
        private const val SIGNATURE_VERSION = "v1,"
    }

    /** Returns a diagnostic representation with the secret redacted. */
    override fun toString(): String = "WebhookVerifier(webhookSecret=${if (webhookSecret == null) "null" else "***"})"
}
