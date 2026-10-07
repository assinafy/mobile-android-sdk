package com.assinafy.sdk.support

import com.assinafy.sdk.util.Base64Codec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Standard Webhooks verification, anchored on the specification's published test vector. */
class WebhookSignatureTest {

    private val secret = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw"
    private val id = "msg_p5jXN8AQM9LWM0D4loKWxJek"
    private val timestamp = "1614265330"
    private val body = """{"test": 2432232314}"""
    private val signature = "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE="
    private val now = 1614265330L

    private fun verify(
        verifier: WebhookVerifier = WebhookVerifier(secret),
        payload: String = body,
        webhookId: String? = id,
        webhookTimestamp: String? = timestamp,
        webhookSignature: String? = signature,
        nowEpochSeconds: Long = now,
    ) = verifier.verifySignature(payload, webhookId, webhookTimestamp, webhookSignature, nowEpochSeconds = nowEpochSeconds)

    @Test
    fun `accepts the specification test vector`() {
        assertThat(verify()).isTrue
        assertThat(WebhookVerifier(secret).verifySignature(body.toByteArray(), id, timestamp, signature, nowEpochSeconds = now)).isTrue
    }

    @Test
    fun `accepts a secret without the whsec prefix and any matching entry of several`() {
        assertThat(verify(WebhookVerifier("MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw"))).isTrue
        assertThat(verify(webhookSignature = "v1,bm90LWl0 $signature")).isTrue
    }

    @Test
    fun `rejects altered body, id, timestamp and signature`() {
        assertThat(verify(payload = """{"test": 2432232315}""")).isFalse
        assertThat(verify(webhookId = "msg_other")).isFalse
        assertThat(verify(webhookTimestamp = "1614265331", nowEpochSeconds = 1614265331)).isFalse
        assertThat(verify(webhookSignature = signature.replace("v1,", "v2,"))).isFalse
        assertThat(verify(webhookSignature = "v1,AAAA")).isFalse
    }

    @Test
    fun `rejects stale and future timestamps outside the tolerance`() {
        assertThat(verify(nowEpochSeconds = now + 300)).isTrue
        assertThat(verify(nowEpochSeconds = now + 301)).isFalse
        assertThat(verify(nowEpochSeconds = now - 301)).isFalse
    }

    @Test
    fun `rejects missing headers and missing or malformed secrets`() {
        assertThat(verify(webhookId = null)).isFalse
        assertThat(verify(webhookTimestamp = "not-a-number")).isFalse
        assertThat(verify(webhookSignature = "")).isFalse
        assertThat(verify(WebhookVerifier(null))).isFalse
        assertThat(verify(WebhookVerifier("whsec_!!!"))).isFalse
        assertThat(verify(WebhookVerifier("whsec_"))).isFalse
    }

    @Test
    fun `toString redacts the secret`() {
        assertThat(WebhookVerifier(secret).toString()).doesNotContain("MfKQ").contains("***")
    }

    @Test
    fun `base64 codec round-trips standard and url-safe input`() {
        val bytes = ByteArray(256) { it.toByte() }
        val standard = Base64Codec.encode(bytes)
        assertThat(standard).isEqualTo(java.util.Base64.getEncoder().encodeToString(bytes))
        assertThat(Base64Codec.decode(standard)).isEqualTo(bytes)
        assertThat(Base64Codec.decode(Base64Codec.encodeUrlNoPadding(bytes))).isEqualTo(bytes)
        assertThat(Base64Codec.encode(byteArrayOf(1))).isEqualTo("AQ==")
        assertThat(Base64Codec.decode("A")).isNull()
    }
}
