package com.assinafy.sdk.resources

import com.assinafy.sdk.AssignmentMethod
import com.assinafy.sdk.VerificationMethod
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.CreateEnvelopeRequest
import com.assinafy.sdk.request.EnvelopeDocumentRequest
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.SignerReference
import com.assinafy.sdk.request.UpdateEnvelopeRequest
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class EnvelopeResourceTest {
    private fun response(data: String) = HttpRawResponse(200, """{"status":200,"data":$data}""", emptyMap())
    private val envelope = """{"id":"envelope","name":"Example","documents":[],"signing_urls":[{"signer_id":"signer","url":null,"embedded":true}]}"""
    private fun request() = CreateEnvelopeRequest(" Example ", listOf(SignerReference(id = " signer ", embedded = true)), listOf(EnvelopeDocumentRequest(" doc ", listOf(" signer "))))
    private fun assertCall(mock: MockApiHttpClient, method: String, path: String, body: String? = null) {
        assertThat(mock.lastCall().method).isEqualTo(method)
        assertThat(mock.lastCall().path).isEqualTo(path)
        if (body != null) assertThat(JsonParser.parseString(mock.lastCall().body)).isEqualTo(JsonParser.parseString(body))
    }

    @Test
    fun `creation normalizes IDs and sends exact spending limit and idempotency header`() = runTest {
        val mock = MockApiHttpClient(response(envelope))
        val result = EnvelopeResource(mock, "account").create(request().copy(maxTotalCredits = BigDecimal("0.50"), idempotencyKey = "create-once"))
        assertCall(mock, "POST", "/accounts/account/envelopes", """{"name":"Example","signers":[{"id":"signer","embedded":true}],"documents":[{"id":"doc","signer_ids":["signer"],"method":"virtual"}],"max_total_credits":0.50}""")
        assertThat(mock.lastCall().idempotencyKey).isEqualTo("create-once")
        assertThat(result.signingUrls!!.single().url).isNull()
        assertThat(result.signingUrls.single().embedded).isTrue()
    }

    @Test
    fun `list get update activities and deletion use account scope and wire fields`() = runTest {
        val mock = MockApiHttpClient()
        val resource = EnvelopeResource(mock, "account")
        mock.enqueue(response("[$envelope]"))
        assertThat(resource.list(ListParams(page = 2, perPage = 3, status = "open", search = "Example")).data).hasSize(1)
        assertCall(mock, "GET", "/accounts/account/envelopes")
        assertThat(mock.lastCall().queryParams).containsExactlyInAnyOrderEntriesOf(mapOf("page" to 2, "per-page" to 3, "status" to "open", "search" to "Example"))
        mock.enqueue(response(envelope))
        resource.get("env/id", "override")
        assertCall(mock, "GET", "/accounts/override/envelopes/env%2Fid")
        mock.enqueue(response(envelope))
        resource.update("envelope", UpdateEnvelopeRequest(clearMessage = true, documentIds = listOf(" doc ")))
        assertCall(mock, "PATCH", "/accounts/account/envelopes/envelope", """{"message":null,"document_ids":["doc"]}""")
        mock.enqueue(HttpRawResponse(200, """{"status":200,"data":[{"id":1,"document_id":"doc","document_name":"Example"}]}""", mapOf("X-Pagination-Total-Count" to "8")))
        val activities = resource.activities("envelope", ListParams(page = 2, perPage = 4, sort = "-id"))
        assertCall(mock, "GET", "/accounts/account/envelopes/envelope/activities")
        assertThat(mock.lastCall().queryParams).containsExactlyInAnyOrderEntriesOf(mapOf("page" to 2, "per-page" to 4, "sort" to "-id"))
        assertThat(activities.data.single().documentId).isEqualTo("doc")
        mock.enqueue(response("[]"))
        resource.delete("envelope")
        assertCall(mock, "DELETE", "/accounts/account/envelopes/envelope")
    }

    @Test
    fun `estimation download cancellation reminder and document mutations use their documented routes`() = runTest {
        val mock = MockApiHttpClient(binaryResponse = byteArrayOf(1, 2))
        val resource = EnvelopeResource(mock, "account")
        mock.enqueue(response("{}"))
        resource.estimateCost(request())
        assertCall(mock, "POST", "/accounts/account/envelopes/estimate-cost")
        assertThat(mock.lastCall().idempotencyKey).isNull()
        assertThat(resource.download("envelope", "original")).containsExactly(1, 2)
        assertCall(mock, "GET_BINARY", "/accounts/account/envelopes/envelope/download/original")
        mock.enqueue(response("""[{"id":"doc","status":"cancelled","cancelled":true}]"""))
        assertThat(resource.cancel("envelope").single().cancelled).isTrue()
        assertCall(mock, "PUT", "/accounts/account/envelopes/envelope/cancel")
        mock.enqueue(response("""{"is_sent":true,"envelope_id":"envelope","signer_id":"signer"}"""))
        assertThat(resource.resendNotification("envelope", "signer").isSent).isTrue()
        assertCall(mock, "PUT", "/accounts/account/envelopes/envelope/signers/signer/resend")
        mock.enqueue(response(envelope))
        resource.addDocument("envelope", EnvelopeDocumentRequest(" doc ", listOf(" signer "), method = " virtual "), BigDecimal.ZERO)
        assertCall(mock, "POST", "/accounts/account/envelopes/envelope/documents", """{"id":"doc","signer_ids":["signer"],"method":"virtual","max_total_credits":0}""")
        mock.enqueue(response(envelope))
        resource.removeDocument("envelope", "doc")
        assertCall(mock, "DELETE", "/accounts/account/envelopes/envelope/documents/doc")
    }

    @Test
    fun `invalid envelopes fail before any request`() {
        val mock = MockApiHttpClient()
        val resource = EnvelopeResource(mock, "account")
        val valid = request()
        val invalid = listOf(
            valid.copy(name = "<script>"), valid.copy(name = "😀".repeat(256)),
            valid.copy(signers = emptyList()), valid.copy(documents = emptyList()),
            valid.copy(signers = valid.signers + valid.signers), valid.copy(documents = valid.documents + valid.documents),
            valid.copy(signers = listOf(SignerReference(id = "unused"))),
            valid.copy(signers = listOf(SignerReference(id = "signer", step = 1))),
            valid.copy(signers = listOf(SignerReference(id = "signer", embedded = true, verificationMethod = VerificationMethod.DIGITAL_CERTIFICATE))),
            valid.copy(documents = listOf(EnvelopeDocumentRequest("doc", listOf("signer"), method = AssignmentMethod.COLLECT))),
            valid.copy(maxTotalCredits = BigDecimal("-0.01")), valid.copy(idempotencyKey = "bad\nheader"),
        )
        invalid.forEach { assertThatThrownBy { runBlocking { resource.create(it) } }.isInstanceOf(ValidationException::class.java) }
        assertThatThrownBy { runBlocking { resource.update("envelope", UpdateEnvelopeRequest()) } }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { resource.update("envelope", UpdateEnvelopeRequest(message = "x", clearMessage = true)) } }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { resource.download("envelope", "unknown") } }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { resource.list(ListParams(status = "cancelled")) } }.isInstanceOf(ValidationException::class.java)
        assertThat(mock.calls).isEmpty()
    }

    @Test
    fun `legacy transports remain usable without a key and refuse to silently drop one`() = runTest {
        val mock = MockApiHttpClient(response(envelope)).apply { legacyPostIdempotent = true }
        val resource = EnvelopeResource(mock, "account")
        resource.create(request().copy(name = "😀".repeat(255)))
        assertCall(mock, "POST", "/accounts/account/envelopes")
        assertThatThrownBy { runBlocking { resource.create(request().copy(idempotencyKey = "once")) } }
            .isInstanceOf(com.assinafy.sdk.exceptions.AssinafyException::class.java)
        assertThat(mock.callCount()).isEqualTo(1)
    }
}
