package com.assinafy.sdk.resources

import com.assinafy.sdk.DocumentExpansion
import com.assinafy.sdk.NotificationEvent
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.ConfirmSignerDataRequest
import com.assinafy.sdk.request.ListParams
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class IntegrationExtensionsTest {
    private fun response(data: String) = HttpRawResponse(200, """{"status":200,"data":$data}""", emptyMap())
    private fun check(mock: MockApiHttpClient, method: String, path: String, body: String? = null) {
        assertThat(mock.lastCall().method).isEqualTo(method)
        assertThat(mock.lastCall().path).isEqualTo(path)
        if (body != null) assertThat(JsonParser.parseString(mock.lastCall().body)).isEqualTo(JsonParser.parseString(body))
    }

    @Test
    fun `embed origins and single use sessions retain exact wire shapes and redact URLs`() = runTest {
        val origin = """{"id":"origin","origin":"https://example.test","created_at":"2026-11-11T12:00:00Z"}"""
        val mock = MockApiHttpClient(response(origin))
        val resource = EmbeddedSigningResource(mock, "account")
        mock.enqueue(response("[$origin]"))
        assertThat(resource.listOrigins().single().origin).isEqualTo("https://example.test")
        check(mock, "GET", "/accounts/account/embed-origins")
        resource.addOrigin(" https://example.test ")
        check(mock, "POST", "/accounts/account/embed-origins", """{"origin":"https://example.test"}""")
        resource.updateOrigin("origin", "http://localhost:8080")
        check(mock, "PUT", "/accounts/account/embed-origins/origin", """{"origin":"http://localhost:8080"}""")
        resource.deleteOrigin("origin")
        check(mock, "DELETE", "/accounts/account/embed-origins/origin")
        mock.enqueue(response("""{"url":"https://example.test/secret-token","expires_at":"2026-11-11T12:05:00Z"}"""))
        val session = resource.createSession("doc", "assignment", "signer")
        check(mock, "POST", "/documents/doc/assignments/assignment/signers/signer/embed-sessions")
        assertThat(session.expiresAt).isEqualTo("2026-11-11T12:05:00Z")
        assertThat(session.toString()).doesNotContain("secret-token")
    }

    @Test
    fun `embed origin validation rejects remote cleartext paths credentials and invalid ports`() {
        val mock = MockApiHttpClient()
        val resource = EmbeddedSigningResource(mock, "account")
        listOf("http://example.test", "https://example.test/path", "https://example.test?query", "https://example.test#fragment", "https://user:pass@example.test", "https://example.test:65536", "https://example.test:0", "//example.test", "").forEach {
            assertThatThrownBy { runBlocking { resource.addOrigin(it) } }.isInstanceOf(ValidationException::class.java)
        }
        assertThat(mock.calls).isEmpty()
    }

    @Test
    fun `usage and notification policies preserve unlimited balances and partial updates`() = runTest {
        val mock = MockApiHttpClient()
        val resource = WorkspaceResource(mock)
        mock.enqueue(response("""{"plan":"Example","documents_renewal_date":"2026-12-01","balances":{"credits":{"balance":0.45,"limit":null,"spent":1.50},"documents":{"balance":null,"limit":null,"spent":2}}}"""))
        val usage = resource.getUsage("account")
        check(mock, "GET", "/accounts/account/usage")
        assertThat(usage.balances!!.credits!!.balance).isEqualByComparingTo(BigDecimal("0.45"))
        assertThat(usage.balances.documents!!.balance).isNull()
        val event = NotificationEvent.ALL.first()
        mock.enqueue(response("""{"editable":true,"owners_receive_all":false,"events":{"$event":true}}"""))
        assertThat(resource.getNotificationPolicy("account").events).containsEntry(event, true)
        check(mock, "GET", "/accounts/account/notification-policy")
        mock.enqueue(response("{}"))
        resource.updateNotificationPolicy("account", mapOf(event to false))
        check(mock, "PUT", "/accounts/account/notification-policy", """{"events":{"$event":false}}""")
        resource.updateNotificationPolicy("account", ownersReceiveAll = true)
        check(mock, "PUT", "/accounts/account/notification-policy", """{"owners_receive_all":true}""")
        val calls = mock.callCount()
        assertThatThrownBy { runBlocking { resource.updateNotificationPolicy("account", mapOf("invalid" to true)) } }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { resource.updateNotificationPolicy("account") } }.isInstanceOf(ValidationException::class.java)
        assertThat(mock.callCount()).isEqualTo(calls)
    }

    @Test
    fun `push preferences use direct event map and reject unknown keys`() = runTest {
        val event = NotificationEvent.PUSH.first()
        val mock = MockApiHttpClient(response("""{"$event":true}"""))
        val resource = UserResource(mock)
        assertThat(resource.getPushPreferences()).containsEntry(event, true)
        check(mock, "GET", "/users/self/push-preferences")
        resource.updatePushPreferences(mapOf(event to false))
        check(mock, "PUT", "/users/self/push-preferences", """{"$event":false}""")
        assertThatThrownBy { runBlocking { resource.updatePushPreferences(mapOf("unknown" to true)) } }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { resource.updatePushPreferences(emptyMap()) } }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `signer confirmation sends phone and explicit consent then reads the signer after empty success`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(response("[]"))
        mock.enqueue(response("""{"id":"signer","full_name":"Example"}"""))
        val signer = SignerDocumentResource(mock).confirmData("doc", "code", ConfirmSignerDataRequest(email = "person@example.com", whatsappPhoneNumber = "+5511999999999", hasAcceptedTerms = true))
        assertThat(signer.id).isEqualTo("signer")
        assertThat(mock.calls[0].path).isEqualTo("/documents/doc/signers/confirm-data?signer-access-code=code")
        assertThat(mock.calls[0].method).isEqualTo("PUT")
        assertThat(JsonParser.parseString(mock.calls[0].body)).isEqualTo(JsonParser.parseString("""{"email":"person@example.com","whatsapp_phone_number":"+5511999999999","has_accepted_terms":true}"""))
        check(mock, "GET", "/signers/self?signer-access-code=code")
    }

    @Test
    fun `signer theme downloads and certified copy requests carry only their capability`() = runTest {
        val mock = MockApiHttpClient(response("{}"), byteArrayOf(1))
        val resource = SignerDocumentResource(mock)
        resource.getTheme("code")
        check(mock, "GET", "/sign/theme?signer-access-code=code")
        resource.getLogo("code")
        check(mock, "GET_BINARY", "/sign/logo?signer-access-code=code")
        resource.download("signer", "doc", "original", "code")
        check(mock, "GET_BINARY", "/signers/signer/documents/doc/download/original?signer-access-code=code")
        mock.enqueue(response("""{"available":true,"notification_method":"Email","resends_left":1,"sent_to":"person@example.com"}"""))
        assertThat(resource.resendCertifiedCopy("code", "doc").resendsLeft).isEqualTo(1)
        check(mock, "POST", "/sign/certified-copy?signer-access-code=code", """{"document_id":"doc"}""")
        resource.downloadCertifiedCopy("token", pades = true)
        check(mock, "GET_BINARY", "/signers/downloads/token/pades")
        resource.resendCertifiedCopyByToken("token")
        check(mock, "POST", "/signers/downloads/token/resend")
        val calls = mock.callCount()
        assertThatThrownBy { runBlocking { resource.download("signer", "doc", "certificated", "code") } }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { resource.download("signer", "doc", "original", " ") } }.isInstanceOf(ValidationException::class.java)
        assertThat(mock.callCount()).isEqualTo(calls)
    }

    @Test
    fun `document expansion and envelope filters remain opt in`() = runTest {
        val mock = MockApiHttpClient(response("{}"))
        DocumentResource(mock).details("doc", setOf(DocumentExpansion.ENVELOPE))
        check(mock, "GET", "/documents/doc")
        assertThat(mock.lastCall().queryParams).containsEntry("expand", "envelope")
        assertThat(ListParams(envelopeId = " envelope ").toQueryMap()).containsEntry("envelope_id", "envelope")
        assertThatThrownBy { ListParams(envelopeId = " ").toQueryMap() }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `webhook test response and overlapping secret rotation are supported`() = runTest {
        val mock = MockApiHttpClient(response("{}"))
        val resource = WebhookResource(mock, "account")
        mock.enqueue(response("""{"id":"delivery","duration_ms":123,"event":"test"}"""))
        assertThat(resource.testEndpoint("endpoint").durationMs).isEqualTo(123)
        check(mock, "POST", "/accounts/account/webhooks/endpoints/endpoint/test")
        mock.enqueue(response("""{"secret":"whsec_test","previous_secret_expires_at":"2026-11-12T12:00:00Z"}"""))
        assertThat(resource.rotateEndpointSecret("endpoint", keepPrevious = true).previousSecretExpiresAt).isEqualTo("2026-11-12T12:00:00Z")
        check(mock, "POST", "/accounts/account/webhooks/endpoints/endpoint/secret/rotate", """{"keep_previous":true}""")
    }

    @Test
    fun `workspace features and reduced closed signer projections are safe to read`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(response("""{"id":"account","name":"Example","signing_features":{"DigitalCertificate":true,"VerificationIdDocumentFaceMatchBasic":false,"WhatsappNotification":true,"VerificationBypass":false,"Embed":true}}"""))
        val account = WorkspaceResource(mock).get("account", expandSigningFeatures = true)
        assertThat(account.signingFeatures!!.embed).isTrue()
        assertThat(account.signingFeatures.identityDocumentFaceMatch).isFalse()
        assertThat(mock.lastCall().queryParams).containsExactlyEntriesOf(mapOf("expand" to "signing_features"))
        mock.enqueue(response("""{"id":"doc","name":"Example","status":"certificated","current_signer":{"id":"signer","completed":true,"verification_method":"Email","notification_methods":["Email"]},"is_signer_access_closed":true,"certified_copy":{"available":true,"notification_method":"Email","resends_left":null}}"""))
        val document = SignerDocumentResource(mock).getCurrent("signer", "code", expandEnvelope = true)
        assertThat(document.currentSigner!!.fullName).isNull()
        assertThat(document.currentSigner.completed).isTrue()
        assertThat(document.isSignerAccessClosed).isTrue()
        assertThat(document.certifiedCopy!!.resendsLeft).isNull()
        assertThat(mock.lastCall().path).contains("expand=envelope", "signer-access-code=code")
    }

    @Test
    fun `template credit limits and embedded flags serialize without leaking the idempotency field`() = runTest {
        val mock = MockApiHttpClient(response("{}"))
        val signers = listOf(com.assinafy.sdk.request.TemplateSigner(roleId = "role", id = "signer", embedded = true))
        val options = com.assinafy.sdk.request.CreateDocumentFromTemplateRequest(signers, maxTotalCredits = BigDecimal("0.45"), idempotencyKey = "template-once")
        DocumentResource(mock, "account").createFromTemplate("template", signers, options)
        check(mock, "POST", "/accounts/account/templates/template/documents", """{"signers":[{"role_id":"role","id":"signer","embedded":true}],"max_total_credits":0.45}""")
        assertThat(mock.lastCall().idempotencyKey).isEqualTo("template-once")
        assertThat(mock.lastCall().body).doesNotContain("idempotency")
    }

    @Test
    fun `malformed legacy confirmation stays in the SDK exception hierarchy`() {
        val mock = MockApiHttpClient(response("""{"id":"signer","step":{}}"""))
        assertThatThrownBy {
            runBlocking { SignerDocumentResource(mock).confirmData("doc", "code", ConfirmSignerDataRequest(hasAcceptedTerms = false)) }
        }.isInstanceOf(com.assinafy.sdk.exceptions.AssinafyException::class.java)
        assertThat(mock.callCount()).isEqualTo(1)
    }

    @Test
    fun `assignments enforce signer ownership and serialize credit ceilings`() = runTest {
        val mock = MockApiHttpClient(response("{}"))
        val resource = AssignmentResource(mock)
        val signer = com.assinafy.sdk.request.SignerReference(id = "signer", embedded = true)
        val request = com.assinafy.sdk.request.CreateAssignmentRequest(signers = listOf(signer), maxTotalCredits = BigDecimal("0.45"))
        resource.create("doc", request)
        check(mock, "POST", "/documents/doc/assignments", """{"method":"virtual","signers":[{"id":"signer","embedded":true}],"max_total_credits":0.45}""")
        val calls = mock.callCount()
        assertThatThrownBy { runBlocking { resource.create("doc", request.copy(signers = listOf(signer, signer))) } }.isInstanceOf(ValidationException::class.java)
        val entries = listOf(com.assinafy.sdk.request.AssignmentEntry("page", listOf(com.assinafy.sdk.request.AssignmentFieldPlacement("signer", "field"), com.assinafy.sdk.request.AssignmentFieldPlacement("outsider", "field"))))
        assertThatThrownBy { runBlocking { resource.create("doc", request.copy(method = "collect", entries = entries)) } }.isInstanceOf(ValidationException::class.java)
        assertThat(mock.callCount()).isEqualTo(calls)
    }
}
