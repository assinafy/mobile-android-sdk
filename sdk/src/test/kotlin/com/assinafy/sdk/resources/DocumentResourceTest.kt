package com.assinafy.sdk.resources

import com.assinafy.sdk.DocumentArtifact
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.ConfirmSignerDataRequest
import com.assinafy.sdk.request.TemplateSigner
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DocumentResourceTest {

    private val docUploadJson = """{"id":"doc-1","account_id":"acc","name":"test.pdf","status":"metadata_ready","created_at":"2024-01-01","updated_at":"2024-01-01","is_closed":false,"decline_reason":null,"declined_by":null}"""
    private val docDetailsJson = """{"id":"doc-1","account_id":"acc","name":"test.pdf","status":"metadata_ready","created_at":"2024-01-01","updated_at":"2024-01-01","is_closed":false}"""

    private fun successResponse(data: String) = HttpRawResponse(200, """{"status":200,"data":$data}""", emptyMap())

    @Test
    fun `upload validates file extension`() {
        assertThatThrownBy {
            runBlocking { DocumentResource(MockApiHttpClient(), "acc").upload(ByteArray(100), "file.txt") }
        }.isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("PDF")
    }

    @Test
    fun `upload validates file is not empty`() {
        assertThatThrownBy {
            runBlocking { DocumentResource(MockApiHttpClient(), "acc").upload(ByteArray(0), "file.pdf") }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `upload validates file size limit`() {
        assertThatThrownBy {
            runBlocking { DocumentResource(MockApiHttpClient(), "acc").upload(ByteArray(26 * 1024 * 1024), "big.pdf") }
        }.isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("25MB")
    }

    @Test
    fun `upload posts to correct endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse(docUploadJson))
        val result = DocumentResource(mock, "acc").upload(ByteArray(100), "contract.pdf")

        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/documents")
        assertThat(result.id).isEqualTo("doc-1")
    }

    @Test
    fun `upload throws when no account ID is available`() {
        assertThatThrownBy {
            runBlocking { DocumentResource(MockApiHttpClient()).upload(ByteArray(100), "test.pdf") }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `details fetches correct endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse(docDetailsJson))
        DocumentResource(mock, "acc").details("doc-1")

        assertThat(mock.lastCall().path).isEqualTo("/documents/doc-1")
    }

    @Test
    fun `delete calls correct endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(HttpRawResponse(204, null, emptyMap()))
        DocumentResource(mock, "acc").delete("doc-1")

        assertThat(mock.lastCall().method).isEqualTo("DELETE")
        assertThat(mock.lastCall().path).isEqualTo("/documents/doc-1")
    }

    @Test
    fun `isFullySigned returns true when status is certificated`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            successResponse("""{"id":"doc-1","account_id":"acc","name":"test.pdf","status":"certificated","created_at":"2024-01-01","updated_at":"2024-01-01","is_closed":true}"""),
        )
        assertThat(DocumentResource(mock, "acc").isFullySigned("doc-1")).isTrue
    }

    @Test
    fun `isFullySigned is false while document is only metadata_ready`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse(docDetailsJson)) // status = metadata_ready, no assignment
        assertThat(DocumentResource(mock, "acc").isFullySigned("doc-1")).isFalse
    }

    @Test
    fun `isFullySigned is true when assignment summary is complete`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            successResponse(
                """{"id":"doc-1","account_id":"acc","name":"t.pdf","status":"pending_signature","created_at":"x","updated_at":"x","is_closed":false,"assignment":{"id":"a","method":"virtual","signers":[],"summary":{"signer_count":2,"completed_count":2,"signers":[]}}}""",
            ),
        )
        assertThat(DocumentResource(mock, "acc").isFullySigned("doc-1")).isTrue
    }

    @Test
    fun `listTags hits account-scoped endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse("""[{"id":"t1","name":"Contracts","color":"ff8800"}]"""))

        val tags = DocumentResource(mock, "acc").listTags("doc-1")

        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/documents/doc-1/tags")
        assertThat(tags).hasSize(1)
        assertThat(tags[0].name).isEqualTo("Contracts")
    }

    @Test
    fun `addTags posts tag names and returns resulting set`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse("""[{"id":"t1","name":"Urgent","color":null}]"""))

        val tags = DocumentResource(mock, "acc").addTags("doc-1", listOf("Urgent"))

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("POST")
        assertThat(call.path).isEqualTo("/accounts/acc/documents/doc-1/tags")
        assertThat(call.body).contains("Urgent")
        assertThat(tags[0].name).isEqualTo("Urgent")
    }

    @Test
    fun `replaceTags puts the full tag set`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse("[]"))

        DocumentResource(mock, "acc").replaceTags("doc-1", emptyList())

        assertThat(mock.lastCall().method).isEqualTo("PUT")
        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/documents/doc-1/tags")
    }

    @Test
    fun `detachTag deletes the doc-tag link`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = HttpRawResponse(200, """{"status":200,"data":{"detached":true}}""", emptyMap()))

        DocumentResource(mock, "acc").detachTag("doc-1", "t1")

        assertThat(mock.lastCall().method).isEqualTo("DELETE")
        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/documents/doc-1/tags/t1")
    }

    @Test
    fun `getSigningProgress calculates percentage correctly`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            successResponse(
                """{"id":"doc-1","account_id":"acc","name":"test.pdf","status":"pending_signature","created_at":"2024-01-01","updated_at":"2024-01-01","is_closed":false,"assignment":{"id":"asg-1","method":"virtual","signers":[],"summary":{"signer_count":3,"completed_count":1,"signers":[]}}}""",
            ),
        )
        val progress = DocumentResource(mock, "acc").getSigningProgress("doc-1")

        assertThat(progress.total).isEqualTo(3)
        assertThat(progress.signed).isEqualTo(1)
        assertThat(progress.pending).isEqualTo(2)
        assertThat(progress.percentage).isEqualTo(33.33)
    }

    @Test
    fun `activities parses payload, origin object, and ISO timestamp`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            successResponse(
                """[{"id":42,"event":"document_uploaded","message":"Documento criado.","payload":[],"origin":{"ip":"1.2.3.4","user-agent":"assinafy-android-sdk/1.0"},"created_at":"2026-05-11T23:58:21Z"}]""",
            ),
        )

        val activities = DocumentResource(mock, "acc").activities("doc-1")

        assertThat(activities).hasSize(1)
        assertThat(activities[0].id).isEqualTo(42L)
        assertThat(activities[0].createdAt).isEqualTo("2026-05-11T23:58:21Z")
        assertThat(activities[0].origin?.get("ip")).isEqualTo("1.2.3.4")
    }

    @Test
    fun `confirmSignerData encodes signer access code`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = HttpRawResponse(204, null, emptyMap()))

        DocumentResource(mock, "acc").confirmSignerData("doc-1", "access+/=", mapOf("cpf" to "123"))

        assertThat(mock.lastCall().path)
            .isEqualTo("/documents/doc-1/signers/confirm-data?signer-access-code=access%2B%2F%3D")
    }

    @Test
    fun `confirmSignerData typed overload serializes contract body keys`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = HttpRawResponse(204, null, emptyMap()))

        DocumentResource(mock, "acc").confirmSignerData(
            "doc-1",
            "code",
            ConfirmSignerDataRequest(email = "a@b.com", hasAcceptedTerms = true),
        )

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("PUT")
        assertThat(call.body).contains("\"email\":\"a@b.com\"").contains("\"has_accepted_terms\":true")
    }

    @Test
    fun `download defaults to the certificated artifact and honors an explicit one`() = runTest {
        val mock = MockApiHttpClient(binaryResponse = byteArrayOf(1, 2, 3))

        DocumentResource(mock, "acc").download("doc-1")
        assertThat(mock.lastCall().path).isEqualTo("/documents/doc-1/download/certificated")

        DocumentResource(mock, "acc").download("doc-1", DocumentArtifact.ORIGINAL)
        assertThat(mock.lastCall().path).isEqualTo("/documents/doc-1/download/original")
    }

    @Test
    fun `getStatuses parses the statuses catalog`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            successResponse("""[{"code":"metadata_ready","deletable":true},{"code":"uploading","deletable":false}]"""),
        )

        val statuses = DocumentResource(mock, "acc").getStatuses()

        assertThat(mock.lastCall().path).isEqualTo("/documents/statuses")
        assertThat(statuses).hasSize(2)
        assertThat(statuses[0].code).isEqualTo("metadata_ready")
        assertThat(statuses[0].deletable).isTrue
    }

    @Test
    fun `waitUntilReady returns the document once it reaches a ready status`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse(docStatus("uploading")))
        mock.enqueue(successResponse(docStatus("metadata_processing")))
        mock.enqueue(successResponse(docStatus("metadata_ready")))

        val doc = DocumentResource(mock, "acc").waitUntilReady("doc-1", pollIntervalMs = 1L)

        assertThat(doc.status).isEqualTo("metadata_ready")
        assertThat(mock.callCount()).isEqualTo(3)
    }

    @Test
    fun `waitUntilReady throws when the document enters a failed status`() {
        val mock = MockApiHttpClient()
        mock.enqueue(HttpRawResponse(200, """{"status":200,"data":${docStatus("failed")}}""", emptyMap()))
        assertThatThrownBy {
            runBlocking { DocumentResource(mock, "acc").waitUntilReady("doc-1", pollIntervalMs = 1L) }
        }.isInstanceOf(ValidationException::class.java).hasMessageContaining("failed")
    }

    @Test
    fun `waitUntilReady throws a timeout after polling a never-ready document`() {
        val mock = MockApiHttpClient(defaultResponse = HttpRawResponse(200, """{"status":200,"data":${docStatus("uploading")}}""", emptyMap()))
        assertThatThrownBy {
            runBlocking { DocumentResource(mock, "acc").waitUntilReady("doc-1", maxWaitMs = 40L, pollIntervalMs = 10L) }
        }.isInstanceOf(ValidationException::class.java).hasMessageContaining("Timeout")
        // Proves it actually polled the never-ready document before timing out.
        assertThat(mock.callCount()).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `createFromTemplate posts to the template documents endpoint with signers`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse(docDetailsJson))

        DocumentResource(mock, "acc").createFromTemplate(
            "tpl-1",
            listOf(TemplateSigner(roleId = "r1", id = "s1", verificationMethod = "Email")),
        )

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("POST")
        assertThat(call.path).isEqualTo("/accounts/acc/templates/tpl-1/documents")
        assertThat(call.body).contains("\"role_id\":\"r1\"").contains("\"id\":\"s1\"")
    }

    @Test
    fun `verify gets the public verify endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse("""{"valid":true}"""))

        val result = DocumentResource(mock, "acc").verify("hash-123")

        assertThat(mock.lastCall().path).isEqualTo("/documents/hash-123/verify")
        assertThat(result["valid"]).isEqualTo(true)
    }

    private fun docStatus(status: String) =
        """{"id":"doc-1","account_id":"acc","name":"t.pdf","status":"$status","created_at":"x","updated_at":"x","is_closed":false}"""
}
