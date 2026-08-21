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

    private val pdf = "%PDF-1.7\n".toByteArray()
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
        val result = DocumentResource(mock, "acc").upload(pdf, "contract.pdf")

        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/documents")
        assertThat(mock.lastCall().method).isEqualTo("POST_MULTIPART_FILE")
        assertThat(result.id).isEqualTo("doc-1")
    }

    @Test
    fun `upload throws when no account ID is available`() {
        assertThatThrownBy {
            runBlocking { DocumentResource(MockApiHttpClient()).upload(pdf, "test.pdf") }
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
    fun `list gets account documents and parses the full document`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse("[$docDetailsJson]"))

        val result = DocumentResource(mock, "acc").list()

        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/documents")
        assertThat(result.data.single().accountId).isEqualTo("acc")
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
        val mock = MockApiHttpClient(defaultResponse = successResponse("""{"id":"s1"}"""))

        DocumentResource(mock, "acc").confirmSignerData("doc-1", "access+/=", mapOf("full_name" to "Signer"))

        assertThat(mock.lastCall().path)
            .isEqualTo("/documents/doc-1/signers/confirm-data?signer-access-code=access%2B%2F%3D")
    }

    @Test
    fun `confirmSignerData typed overload serializes contract body keys`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = successResponse("""{"id":"s1"}"""))

        DocumentResource(mock, "acc").confirmSignerData(
            "doc-1",
            "code",
            ConfirmSignerDataRequest(fullName = "Signer", email = "signer@example.com", governmentId = "123"),
        )

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("PUT")
        assertThat(call.body)
            .contains("\"full_name\":\"Signer\"")
            .contains("\"email\":\"signer@example.com\"")
            .contains("\"government_id\":\"123\"")
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
    fun `thumbnail and page download use their binary endpoints`() = runTest {
        val mock = MockApiHttpClient(binaryResponse = byteArrayOf(1, 2, 3))
        val resource = DocumentResource(mock, "acc")

        assertThat(resource.thumbnail("doc-1")).containsExactly(1, 2, 3)
        assertThat(mock.lastCall().path).isEqualTo("/documents/doc-1/thumbnail")

        assertThat(resource.downloadPage("doc-1", "page-1")).containsExactly(1, 2, 3)
        assertThat(mock.lastCall().path).isEqualTo("/documents/doc-1/pages/page-1/download")
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
    fun `estimate template cost posts the documented signer projection and parses cost`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            successResponse(
                """{"documents":1,"credits":2,"total_credits":2,"breakdown":[],"has_sufficient_resources":true}""",
            ),
        )

        val result = DocumentResource(mock, "acc").estimateCostFromTemplate(
            "tpl-1",
            listOf(TemplateSigner(roleId = "r1", id = "not-sent", verificationMethod = "Email")),
        )

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("POST")
        assertThat(call.path).isEqualTo("/accounts/acc/templates/tpl-1/documents/estimate-cost")
        assertThat(call.body).contains("\"role_id\":\"r1\"").doesNotContain("not-sent")
        assertThat(result.hasSufficientResources).isTrue()
    }

    @Test
    fun `verify gets the public verify endpoint`() = runTest {
        val authenticated = MockApiHttpClient()
        val public = MockApiHttpClient()
        public.enqueue(
            successResponse(
                """{"hash":"hash-123","id":"doc-1","status":"certificated","page_count":"1","signer_count":"1","completed_count":1,"completed_at":"2026-01-01T00:00:00Z","verified_at":"2026-01-01T00:00:01Z","is_valid":true,"message":""}""",
            ),
        )

        val result = DocumentResource(authenticated, "acc", publicHttp = public).verify("hash-123")

        assertThat(authenticated.calls).isEmpty()
        assertThat(public.lastCall().path).isEqualTo("/documents/hash-123/verify")
        assertThat(result.isValid).isTrue
    }

    @Test
    fun `public document operations use the credentialless transport`() = runTest {
        val authenticated = MockApiHttpClient()
        val public = MockApiHttpClient()
        public.enqueue(successResponse("""{"id":"doc-1","name":"Agreement.pdf","page_count":1}"""))
        public.enqueue(HttpRawResponse(200, """{"status":200,"message":"sent"}""", emptyMap()))
        val resource = DocumentResource(authenticated, "acc", publicHttp = public)

        assertThat(resource.getPublic("doc-1").pageCount?.toInt()).isEqualTo(1)
        resource.sendToken("doc-1", "recipient@example.com")

        assertThat(authenticated.calls).isEmpty()
        assertThat(public.calls.map { it.path }).containsExactly(
            "/public/documents/doc-1",
            "/public/documents/doc-1/send-token",
        )
        assertThat(public.lastCall().body).isEqualTo("""{"email":"recipient@example.com"}""")
    }

    @Test
    fun `send token omits the optional email body`() = runTest {
        val public = MockApiHttpClient(defaultResponse = HttpRawResponse(200, """{"status":200,"message":"sent"}""", emptyMap()))

        DocumentResource(MockApiHttpClient(), "acc", publicHttp = public).sendToken("doc-1")

        assertThat(public.lastCall().body).isNull()
    }

    @Test
    fun `send token supports explicit deployed channels`() = runTest {
        val public = MockApiHttpClient(defaultResponse = HttpRawResponse(200, """{"status":200}""", emptyMap()))
        val resource = DocumentResource(MockApiHttpClient(), "acc", publicHttp = public)

        resource.sendToken("doc-1", "recipient@example.com", "email")
        resource.sendToken("doc-1", "+5511999998888", "whatsapp")

        assertThat(public.calls.map { it.body }).containsExactly(
            """{"recipient":"recipient@example.com","channel":"email"}""",
            """{"recipient":"+5511999998888","channel":"whatsapp"}""",
        )
    }

    @Test
    fun `send token retries only legacy channel validation`() = runTest {
        val public = MockApiHttpClient()
        public.enqueue(
            HttpRawResponse(
                422,
                """{"status":422,"message":"channel is required","errors":{"channel":["required"]}}""",
                emptyMap(),
            ),
        )
        public.enqueue(HttpRawResponse(200, """{"status":200}""", emptyMap()))

        DocumentResource(MockApiHttpClient(), "acc", publicHttp = public)
            .sendToken("doc-1", "recipient@example.com")

        assertThat(public.calls.map { it.body }).containsExactly(
            """{"email":"recipient@example.com"}""",
            """{"recipient":"recipient@example.com","channel":"email"}""",
        )
    }

    @Test
    fun `send token sees validation details in an error envelope`() = runTest {
        val public = MockApiHttpClient()
        public.enqueue(
            HttpRawResponse(
                200,
                """{"status":422,"message":"Validation failed","errors":{"channel":["required"]}}""",
                emptyMap(),
            ),
        )
        public.enqueue(HttpRawResponse(200, """{"status":200}""", emptyMap()))

        DocumentResource(MockApiHttpClient(), "acc", publicHttp = public)
            .sendToken("doc-1", "recipient@example.com")

        assertThat(public.calls.map { it.body }).containsExactly(
            """{"email":"recipient@example.com"}""",
            """{"recipient":"recipient@example.com","channel":"email"}""",
        )
    }

    @Test
    fun `rename patches the document with the new name`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse(docDetailsJson))

        val doc = DocumentResource(mock, "acc").rename("doc-1", "Service agreement.pdf")

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("PATCH")
        assertThat(call.path).isEqualTo("/documents/doc-1")
        assertThat(call.body).isEqualTo("""{"name":"Service agreement.pdf"}""")
        assertThat(doc.id).isEqualTo("doc-1")
    }

    @Test
    fun `rename throws on a blank name`() {
        assertThatThrownBy {
            runBlocking { DocumentResource(MockApiHttpClient(), "acc").rename("doc-1", "  ") }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `rename throws when the name exceeds 255 characters`() {
        assertThatThrownBy {
            runBlocking { DocumentResource(MockApiHttpClient(), "acc").rename("doc-1", "a".repeat(256)) }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `search hits the search endpoint with query and status`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse("""[{"id":"doc-1","name":"audit.pdf","status":"metadata_ready"}]"""))

        val result = DocumentResource(mock, "acc").search(query = "audit", status = "metadata_ready", perPage = 10)

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("GET")
        assertThat(call.path).isEqualTo("/accounts/acc/documents/search")
        assertThat(call.queryParams["search"]).isEqualTo("audit")
        assertThat(call.queryParams["status"]).isEqualTo("metadata_ready")
        assertThat(call.queryParams["per-page"]).isEqualTo(10)
        assertThat(result.data).hasSize(1)
        assertThat(result.data[0].id).isEqualTo("doc-1")
        assertThat(result.data[0].accountId).isNull()
        assertThat(result.data[0].tags).isNull()
        assertThat(result.data[0].pages).isNull()
        assertThat(result.data[0].isClosed).isNull()
    }

    @Test
    fun `search rejects pagination outside API bounds`() {
        val resource = DocumentResource(MockApiHttpClient(), "acc")

        assertThatThrownBy { runBlocking { resource.search(page = 0) } }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { resource.search(perPage = 101) } }
            .isInstanceOf(ValidationException::class.java)
    }

    private fun docStatus(status: String) =
        """{"id":"doc-1","account_id":"acc","name":"t.pdf","status":"$status","created_at":"x","updated_at":"x","is_closed":false}"""
}
