package com.assinafy.sdk.resources

import com.assinafy.sdk.AssinafyClient
import com.assinafy.sdk.AssinafyClientConfig
import com.assinafy.sdk.IdentityReviewDecision
import com.assinafy.sdk.IdentityReviewImage
import com.assinafy.sdk.IdentityVerificationStatus
import com.assinafy.sdk.VerificationMethod
import com.assinafy.sdk.exceptions.ApiException
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.CreateDocumentFromTemplateRequest
import com.assinafy.sdk.request.TemplateSigner
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class IdentityVerificationTest {
    private val review = """{"id":"verification-placeholder","status":"InReview","provider":"AssinafyVerify","verification_method":"IdDocumentFaceMatchBasic","reason_codes":[{"code":"NAME_MISMATCH","description":"Registered and captured names differ"}],"evidence_available":true,"expected":{"name":"Example Signer","cpf":"00000000000"},"document":{"type":"CNH","name":"Example Signer","cpf":"00000000000"},"matches":{"cpf":true,"name":0.52,"birth_date":null},"checks":[{"name":"name","result":"review","score":0.52,"reasons":["NAME_MISMATCH"]}],"images":["document_front","document_back","face"],"ruleset_version":"example","evidence_sha256":"digest-placeholder","reviewed_by":{"id":"reviewer-placeholder","name":"Example Reviewer"},"reviewed_at":null,"review_note":null,"completed_at":null}"""
    private fun ok(data: String) = HttpRawResponse(200, """{"status":200,"message":"OK","data":$data}""", emptyMap())
    private val path = "/documents/doc%201/assignments/asg%2F1/signers/signer%2B1/identity-verification"

    @Test
    fun `signer verification uses only the public transport and retains lifecycle fields`() = runTest {
        val authenticated = MockApiHttpClient()
        val public = MockApiHttpClient().apply {
            enqueue(ok("""{"id":"signer","verification_method":"IdDocumentFaceMatchBasic"}"""))
            enqueue(ok("""{"can_start":false,"status":"InReview","url":"https://example.com/capture#t=secret-state-token","expires_at":"2026-10-16T13:00:00Z","completed_at":null}"""))
            enqueue(ok("""{"id":"verification","url":"https://example.com/capture#t=secret-capture-token","expires_at":null}"""))
        }
        val sdk = AssinafyClient.create(AssinafyClientConfig(apiKey = "test", accountId = "acc"), authenticated, public)
        assertThat(sdk.signerDocuments.self("code").verificationMethod).isEqualTo(VerificationMethod.ID_DOCUMENT_FACE_MATCH_BASIC)
        val status = sdk.signerDocuments.getIdentityVerification("code+/=")
        assertThat(status.canStart).isFalse
        assertThat(status.status).isEqualTo(IdentityVerificationStatus.IN_REVIEW)
        assertThat(status.expiresAt).isEqualTo("2026-10-16T13:00:00Z")
        assertThat(status.completedAt).isNull()
        assertThat(status.url).contains("secret-state-token")
        assertThat(status.toString()).doesNotContain("secret-state-token")
        assertThat(public.lastCall().method).isEqualTo("GET")
        assertThat(public.lastCall().path).isEqualTo("/signers/identity-verification?signer-access-code=code%2B%2F%3D")
        val session = sdk.signerDocuments.startIdentityVerification("code+/=", consent = true)
        assertThat(public.lastCall().method).isEqualTo("POST")
        assertThat(public.lastCall().path).isEqualTo("/signers/identity-verification?signer-access-code=code%2B%2F%3D")
        assertThat(public.lastCall().body).isEqualTo("""{"consent":true}""")
        assertThat(session.id).isEqualTo("verification")
        assertThat(session.expiresAt).isNull()
        assertThat(session.url).contains("secret-capture-token")
        assertThat(session.toString()).doesNotContain("secret-capture-token")
        assertThat(authenticated.calls).isEmpty()
    }

    @Test
    fun `signer cannot start without explicit consent or a nonblank access code`() {
        val mock = MockApiHttpClient()
        val resource = SignerDocumentResource(mock)
        listOf<suspend () -> Unit>(
            { resource.startIdentityVerification("code", consent = false) },
            { resource.startIdentityVerification(" ", consent = true) },
            { resource.getIdentityVerification(" ") },
        ).forEach { action ->
            assertThatThrownBy { runBlocking { action() } }.isInstanceOf(ValidationException::class.java)
        }
        assertThat(mock.calls).isEmpty()
    }

    @Test
    fun `account review parses complete provider evidence without using the signer transport`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = ok(review))
        val public = MockApiHttpClient()
        val sdk = AssinafyClient.create(AssinafyClientConfig(accountId = "acc"), mock, public)
        val result = sdk.assignments.getIdentityVerification("doc 1", "asg/1", "signer+1")
        assertThat(mock.lastCall().method).isEqualTo("GET")
        assertThat(mock.lastCall().path).isEqualTo(path)
        assertThat(mock.lastCall().queryParams).isEmpty()
        assertThat(result.id).isEqualTo("verification-placeholder")
        assertThat(result.status).isEqualTo(IdentityVerificationStatus.IN_REVIEW)
        assertThat(result.provider).isEqualTo("AssinafyVerify")
        assertThat(result.verificationMethod).isEqualTo(VerificationMethod.ID_DOCUMENT_FACE_MATCH_BASIC)
        assertThat(result.reasonCodes.single().code).isEqualTo("NAME_MISMATCH")
        assertThat(result.evidenceAvailable).isTrue
        assertThat(result.expected?.name).isEqualTo("Example Signer")
        assertThat(result.document?.type).isEqualTo("CNH")
        assertThat(result.matches?.cpf).isTrue()
        assertThat(result.matches?.name).isEqualTo(0.52)
        assertThat(result.checks.single().reasons).containsExactly("NAME_MISMATCH")
        assertThat(result.images).containsExactly(IdentityReviewImage.DOCUMENT_FRONT, IdentityReviewImage.DOCUMENT_BACK, IdentityReviewImage.FACE)
        assertThat(result.rulesetVersion).isEqualTo("example")
        assertThat(result.evidenceSha256).isEqualTo("digest-placeholder")
        assertThat(result.reviewedBy?.name).isEqualTo("Example Reviewer")
        assertThat(result.reviewedAt).isNull()
        assertThat(result.reviewNote).isNull()
        assertThat(result.completedAt).isNull()
        assertThat(public.calls).isEmpty()
    }

    @Test
    fun `review decisions send only the decision and optional unmodified note`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = ok(review))
        val resource = AssignmentResource(mock)
        listOf(IdentityReviewDecision.APPROVED, IdentityReviewDecision.REJECTED).forEach { decision ->
            val note = "Reviewed " + "\uD83D\uDE00".repeat(1990)
            resource.reviewIdentityVerification("doc 1", "asg/1", "signer+1", decision, note)
            assertThat(mock.lastCall().method).isEqualTo("POST")
            assertThat(mock.lastCall().path).isEqualTo("$path/review")
            val body = JsonParser.parseString(mock.lastCall().body).asJsonObject
            assertThat(body.keySet()).containsExactlyInAnyOrder("decision", "note")
            assertThat(body["decision"].asString).isEqualTo(decision)
            assertThat(body["note"].asString).isEqualTo(note)
        }
        resource.reviewIdentityVerification("doc 1", "asg/1", "signer+1", IdentityReviewDecision.APPROVED)
        assertThat(mock.lastCall().body).isEqualTo("""{"decision":"Approved"}""")
    }

    @Test
    fun `evidence downloads validate names and preserve binary bytes`() = runTest {
        val bytes = "%PDF-test".toByteArray()
        val mock = MockApiHttpClient(binaryResponse = bytes)
        val resource = AssignmentResource(mock)
        IdentityReviewImage.ALL.forEach { name ->
            assertThat(resource.downloadIdentityVerificationImage("doc 1", "asg/1", "signer+1", name)).isEqualTo(bytes)
            assertThat(mock.lastCall().method).isEqualTo("GET_BINARY")
            assertThat(mock.lastCall().path).isEqualTo("$path/images/$name")
        }
        assertThatThrownBy { runBlocking { resource.downloadIdentityVerificationImage("doc", "asg", "signer", "../secret") } }
            .isInstanceOf(ValidationException::class.java)
        assertThat(mock.callCount()).isEqualTo(IdentityReviewImage.ALL.size)
        mock.binaryError = ApiException("Review closed", 404)
        assertThatThrownBy { runBlocking { resource.downloadIdentityVerificationImage("doc", "asg", "signer", IdentityReviewImage.FACE) } }
            .isInstanceOf(ApiException::class.java).hasFieldOrPropertyWithValue("statusCode", 404)
    }

    @Test
    fun `review arguments fail before the round trip`() {
        val mock = MockApiHttpClient()
        val resource = AssignmentResource(mock)
        listOf<suspend () -> Unit>(
            { resource.getIdentityVerification(" ", "asg", "signer") },
            { resource.getIdentityVerification("doc", " ", "signer") },
            { resource.getIdentityVerification("doc", "asg", " ") },
            { resource.reviewIdentityVerification("doc", "asg", "signer", "approved") },
            { resource.reviewIdentityVerification("doc", "asg", "signer", IdentityReviewDecision.APPROVED, "x".repeat(2001)) },
        ).forEach { action ->
            assertThatThrownBy { runBlocking { action() } }.isInstanceOf(ValidationException::class.java)
        }
        assertThat(mock.calls).isEmpty()
    }

    @Test
    fun `biometric template requests preserve channels and response signer state`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = ok("""{"id":"doc","name":"Example","status":"pending_identity_review","assignment":{"id":"asg","signers":[{"id":"signer","identity_verification_status":"InReview"}]}}"""))
        val resource = DocumentResource(mock, "acc")
        listOf("Email", "Whatsapp").forEach { channel ->
            val signers = listOf(TemplateSigner("role", "signer", VerificationMethod.ID_DOCUMENT_FACE_MATCH_BASIC, listOf(channel)))
            val document = resource.createFromTemplate("template", signers, CreateDocumentFromTemplateRequest(signers))
            assertThat(document.assignment?.signers?.single()?.identityVerificationStatus).isEqualTo(IdentityVerificationStatus.IN_REVIEW)
            assertThat(mock.lastCall().body).contains("\"verification_method\":\"IdDocumentFaceMatchBasic\"", "\"notification_methods\":[\"$channel\"]")
            resource.estimateCostFromTemplate("template", signers)
            val entry = JsonParser.parseString(mock.lastCall().body).asJsonObject["signers"].asJsonArray.single().asJsonObject
            assertThat(entry.keySet()).containsExactlyInAnyOrder("role_id", "verification_method", "notification_methods")
        }
    }
}
