package com.assinafy.sdk.support

import com.google.gson.Gson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WebhookVerifierTest {

    private val secret = "super-secret"
    private val payload = Gson().toJson(mapOf("event" to "document_ready", "payload" to mapOf("document_id" to "doc-1")))
    private val signature = computeHmac(payload.toByteArray(Charsets.UTF_8), secret)

    @Test
    fun `verify returns true for a matching HMAC-SHA256 signature`() {
        val verifier = WebhookVerifier(secret)
        assertThat(verifier.verify(payload, signature)).isTrue
    }

    @Test
    fun `verify returns false for mismatched signature`() {
        val verifier = WebhookVerifier(secret)
        assertThat(verifier.verify(payload, "deadbeef")).isFalse
    }

    @Test
    fun `verify returns false when no secret is configured`() {
        val verifier = WebhookVerifier(null)
        assertThat(verifier.verify(payload, signature)).isFalse
    }

    @Test
    fun `verify works with ByteArray payload`() {
        val verifier = WebhookVerifier(secret)
        assertThat(verifier.verify(payload.toByteArray(Charsets.UTF_8), signature)).isTrue
    }

    @Test
    fun `extractEvent parses JSON payloads`() {
        val verifier = WebhookVerifier(secret)
        val event = verifier.extractEvent(payload)
        assertThat(event).isNotNull
        assertThat(event?.event).isEqualTo("document_ready")
    }

    @Test
    fun `extractEvent returns null on malformed payload`() {
        val verifier = WebhookVerifier(secret)
        assertThat(verifier.extractEvent("{not json")).isNull()
    }

    @Test
    fun `getEventType and getEventData unwrap the envelope`() {
        val verifier = WebhookVerifier(secret)
        val event = verifier.extractEvent(payload)
        assertThat(verifier.getEventType(event)).isEqualTo("document_ready")
        val data = verifier.getEventData(event)
        assertThat(data["document_id"]).isEqualTo("doc-1")
    }

    @Test
    fun `getEventType returns null for null event`() {
        val verifier = WebhookVerifier(secret)
        assertThat(verifier.getEventType(null)).isNull()
    }

    @Test
    fun `getEventData returns empty map for null event`() {
        val verifier = WebhookVerifier(secret)
        assertThat(verifier.getEventData(null)).isEmpty()
    }

    private fun computeHmac(data: ByteArray, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }
}
