package com.assinafy.sdk.resources

import com.assinafy.sdk.AssinafyClient
import com.assinafy.sdk.AssinafyClientConfig
import com.assinafy.sdk.exceptions.AssinafyException
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.http.OkHttpApiClient
import com.assinafy.sdk.models.Assignment
import com.assinafy.sdk.models.DocumentArtifacts
import com.assinafy.sdk.models.DocumentDetails
import com.assinafy.sdk.models.DocumentPage
import com.assinafy.sdk.models.PublicDocumentInfo
import com.assinafy.sdk.models.SigningUrl
import com.assinafy.sdk.models.TemplatePage
import com.assinafy.sdk.models.WhatsappNotificationButton
import com.assinafy.sdk.oauth.OAuthConfig
import com.assinafy.sdk.oauth.OAuthException
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.UpdateNotificationPreferencesRequest
import com.assinafy.sdk.request.UpdateSignerRequest
import com.assinafy.sdk.util.ApiValidator
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ApiContractRegressionTest {
    private fun success(data: String) = HttpRawResponse(200, """{"status":200,"data":$data}""", emptyMap())

    @Test
    fun `login signer and OAuth clients cannot be configured with remote cleartext URLs`() {
        listOf(AssinafyClientConfig(baseUrl = "http://api.example.test/v1"), AssinafyClientConfig(baseUrl = "https://api.example.test:999999/v1")).forEach { config ->
            assertThatThrownBy { AssinafyClient.create(config) }.isInstanceOf(ValidationException::class.java)
        }
        assertThatThrownBy { OkHttpApiClient("http://api.example.test/v1", null, null) }.isInstanceOf(IllegalArgumentException::class.java)
        listOf("localhost", "127.0.0.1", "[::1]").forEach {
            assertThat(AssinafyClient.create(AssinafyClientConfig(baseUrl = "http://$it/v1"))).isNotNull()
        }
    }

    @Test
    fun `transport rejects paths escaping the API root before sending bodies`() {
        val transport = OkHttpApiClient("https://api.example.test/v1", null, null)
        listOf("http://other.example.test/login", "https://other.example.test/login", "../login").forEach {
            assertThatThrownBy { runBlocking { transport.post(it, "{\"password\":\"dummy\"}") } }.isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThatThrownBy { runBlocking { transport.getAbsolute("http://other.example.test/discovery") } }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `WhatsApp notifications accept ISO dates offsets fractions and legacy Unix timestamps`() = runTest {
        listOf("\"2026-10-28T12:00:00Z\"", "\"2026-10-28T09:00:00-03:00\"", "\"2026-10-28T12:00:00.123Z\"", "1793188800").forEach { timestamp ->
            val mock = MockApiHttpClient(success("""[{"sent_at":$timestamp,"body":"Message","buttons":[]}]"""))
            val result = AssignmentResource(mock).listWhatsappNotifications("doc", "assignment")
            assertThat(result.single().sentAt).isEqualTo(1793188800L)
            assertThat(mock.lastCall().method).isEqualTo("GET")
            assertThat(mock.lastCall().path).isEqualTo("/documents/doc/assignments/assignment/whatsapp-notifications")
        }
        assertThatThrownBy {
            runBlocking { AssignmentResource(MockApiHttpClient(success("""[{"sent_at":"not-a-date"}]"""))).listWhatsappNotifications("doc", "assignment") }
        }.isInstanceOf(AssinafyException::class.java)
    }

    @Test
    fun `signer updates clear contacts with empty strings and preserve government ID formatting`() = runTest {
        val mock = MockApiHttpClient(success("""{"id":"signer","full_name":"Example"}"""))
        val signers = SignerResource(mock, "account")
        signers.update("signer", UpdateSignerRequest(email = "", whatsappPhoneNumber = "", governmentId = ""))
        assertThat(mock.lastCall().method).isEqualTo("PUT")
        assertThat(mock.lastCall().path).isEqualTo("/accounts/account/signers/signer")
        assertThat(JsonParser.parseString(mock.lastCall().body)).isEqualTo(JsonParser.parseString("""{"email":"","whatsapp_phone_number":"","government_id":""}"""))
        signers.update("signer", UpdateSignerRequest(governmentId = "AB.123.456/0001-XY"))
        assertThat(mock.lastCall().body).contains("AB.123.456/0001-XY").doesNotContain("email")
    }

    @Test
    fun `document search sorts and activities expose later pages without breaking original entrypoints`() = runTest {
        val mock = MockApiHttpClient(success("[]"))
        val documents = DocumentResource(mock, "account")
        documents.search(ListParams(search = "Contract", sort = "-updated_at", perPage = 10))
        assertThat(mock.lastCall().path).isEqualTo("/accounts/account/documents/search")
        assertThat(mock.lastCall().queryParams).containsExactlyInAnyOrderEntriesOf(mapOf("search" to "Contract", "sort" to "-updated_at", "per-page" to 10))
        documents.activities("doc", ListParams(page = 2, perPage = 50, sort = "-id"))
        assertThat(mock.lastCall().path).isEqualTo("/documents/doc/activities")
        assertThat(mock.lastCall().queryParams).containsExactlyInAnyOrderEntriesOf(mapOf("page" to 2, "per-page" to 50, "sort" to "-id"))
        documents.search(query = "Contract")
        documents.activities("doc")
        assertThat(mock.lastCall().queryParams).isEmpty()
    }

    @Test
    fun `contract owner notifications read and update every setting`() = runTest {
        val mock = MockApiHttpClient(success("""{"ContractRenewalNotice":true,"ContractExpiring":false,"ContractExpired":true,"ContractRenewed":false}"""))
        val result = UserResource(mock).updateNotificationPreferences(UpdateNotificationPreferencesRequest(contractRenewalNotice = true, contractExpiring = false, contractExpired = true, contractRenewed = false))
        assertThat(mock.lastCall().path).isEqualTo("/users/self/notification-preferences")
        assertThat(mock.lastCall().method).isEqualTo("PUT")
        assertThat(JsonParser.parseString(mock.lastCall().body)).isEqualTo(JsonParser.parseString("""{"ContractRenewalNotice":true,"ContractExpiring":false,"ContractExpired":true,"ContractRenewed":false}"""))
        assertThat(result.contractRenewalNotice).isTrue()
        assertThat(result.contractExpiring).isFalse()
        assertThat(result.contractExpired).isTrue()
        assertThat(result.contractRenewed).isFalse()
    }

    @Test
    fun `workspace document assignment and webhook projections retain the new fields`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(success("""{"id":"account","name":"Example","limits":{"webhook_endpoints":3}}"""))
        assertThat(WorkspaceResource(mock).get("account").limits!!.webhookEndpoints).isEqualTo(3)
        mock.enqueue(success("""{"id":"doc","name":"Contract.pdf","status":"certificated","has_contract":true,"verification_code":"verification","verification_url":"https://example.com/verify","assignment":{"id":"assignment","document_id":"doc","created_at":"2026-10-28T12:00:00Z"}}"""))
        val doc = DocumentResource(mock).get("doc")
        assertThat(doc.hasContract).isTrue()
        assertThat(doc.verificationCode).isEqualTo("verification")
        assertThat(doc.verificationUrl).isEqualTo("https://example.com/verify")
        assertThat(doc.assignment!!.documentId).isEqualTo("doc")
        assertThat(doc.assignment.createdAt).isEqualTo("2026-10-28T12:00:00Z")
        mock.enqueue(success("""[{"id":"endpoint","url":"https://example.com/hook","is_held":true,"held_since":1793188800,"resource":"webhook_endpoint"}]"""))
        val endpoint = WebhookResource(mock, "account").listEndpoints().single()
        assertThat(endpoint.isHeld).isTrue()
        assertThat(endpoint.heldSince).isEqualTo(1793188800L)
        assertThat(endpoint.resource).isEqualTo("webhook_endpoint")
        mock.enqueue(success("""[{"id":"dispatch","event":"document_ready","webhook_id":"delivery","attempt":2,"kind":"scheduled","will_retry":true}]"""))
        val dispatch = WebhookResource(mock, "account").listDispatches().data.single()
        assertThat(dispatch.webhookId).isEqualTo("delivery")
        assertThat(dispatch.attempt).isEqualTo(2)
        assertThat(dispatch.kind).isEqualTo("scheduled")
        assertThat(dispatch.willRetry).isTrue()
    }

    @Test
    fun `document rename and decline limits count Unicode characters`() = runTest {
        val mock = MockApiHttpClient(success("""{"id":"doc","name":"Example","status":"uploaded"}"""))
        DocumentResource(mock).rename("doc", "😀".repeat(255))
        assertThat(mock.lastCall().method).isEqualTo("PATCH")
        assertThat(ApiValidator.requireDeclineReason("😀".repeat(2000))).hasSize(4000)
        assertThatThrownBy { ApiValidator.requireDeclineReason("😀".repeat(2001)) }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `capability URLs are redacted directly and inside nested document diagnostics`() {
        val url = "https://example.com/sign?signer-access-code=dummy-secret"
        val assignment = Assignment(id = "assignment", signingUrls = listOf(SigningUrl("signer", url)))
        listOf(
            SigningUrl("signer", url),
            assignment,
            WhatsappNotificationButton("Sign", url),
            DocumentDetails(id = "doc", name = "Example", status = "pending_signature", assignment = assignment, signingUrl = url),
            PublicDocumentInfo("doc", "Example", signingUrl = url),
            DocumentArtifacts(original = url),
            DocumentPage("page", downloadUrl = url),
            TemplatePage("page", downloadUrl = url),
        ).forEach { assertThat(it.toString()).doesNotContain(url, "dummy-secret") }
    }

    @Test
    fun `OAuth challenges use case insensitive headers and discovery rejects HTTP issuers`() {
        val mock = MockApiHttpClient(HttpRawResponse(403, "{}", mapOf("WWW-Authenticate" to "Bearer error=\"insufficient_scope\", scope=\"openid\"")))
        val resource = OAuthResource(mock, mock, OAuthConfig(clientId = "public-client", redirectUri = "https://example.com/callback"))
        assertThatThrownBy { runBlocking { resource.userInfo() } }.isInstanceOfSatisfying(OAuthException::class.java) { assertThat(it.error).isEqualTo("insufficient_scope") }
        val calls = mock.callCount()
        assertThatThrownBy { runBlocking { resource.authorizationServerMetadata("http://example.com") } }.isInstanceOf(ValidationException::class.java)
        assertThat(mock.callCount()).isEqualTo(calls)
        val attempt = resource.authorizationRequest(state = "state")
        assertThatThrownBy { resource.parseCallback("https://example.com/callback?code=code&state=state&iss=https%3A%2F%2Fauth.assinafy.com.br%2F", attempt) }.isInstanceOf(ValidationException::class.java)
    }
}
