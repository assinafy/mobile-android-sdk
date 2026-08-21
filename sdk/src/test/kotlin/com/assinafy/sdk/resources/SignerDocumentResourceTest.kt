package com.assinafy.sdk.resources

import com.assinafy.sdk.DocumentArtifact
import com.assinafy.sdk.SignatureType
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.ConfirmSignerDataRequest
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.SignAssignmentItemRequest
import com.assinafy.sdk.request.VerifySignerEmailRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SignerDocumentResourceTest {

    private val signerJson =
        """{
            "resource":"signer",
            "id":"signer-1",
            "full_name":"Example Signer",
            "email":"signer@example.com",
            "whatsapp_phone_number":null,
            "has_accepted_terms":true
        }
        """.trimIndent()

    private val documentJson =
        """{
            "resource":"document",
            "id":"doc-1",
            "account_id":"account-1",
            "template_id":null,
            "name":"Agreement.pdf",
            "status":"pending_signature",
            "assignment":{
                "resource":"assignment",
                "id":"assignment-1",
                "sender_email":"sender@example.com",
                "method":"collect",
                "expires_at":null,
                "message":"Please sign",
                "signers":[{
                    "id":"signer-1",
                    "full_name":"Example Signer",
                    "email":"signer@example.com",
                    "verification_method":"Email",
                    "notification_methods":["Email"],
                    "step":1,
                    "notified":true
                }],
                "copy_receivers":[],
                "items":[{
                    "id":"item-1",
                    "page":{
                        "id":"page-1",
                        "number":1,
                        "height":1651,
                        "width":1275,
                        "download_url":"https://example.com/page-1.png"
                    },
                    "signer":{
                        "id":"signer-1",
                        "full_name":"Example Signer",
                        "email":"signer@example.com",
                        "whatsapp_phone_number":"+15555550100",
                        "has_accepted_terms":true
                    },
                    "field":{
                        "resource":"field",
                        "id":"field-1",
                        "name":"Approval",
                        "type":"text",
                        "regex":"^[A-Z]+$",
                        "is_pre_defined":false,
                        "is_active":true,
                        "is_required":true,
                        "is_standard":false,
                        "is_read_only":false,
                        "is_visible":true
                    },
                    "display_settings":{
                        "left":69,
                        "top":282,
                        "width":421,
                        "height":45.86,
                        "fontSize":18,
                        "fontFamily":"Arial",
                        "backgroundColor":"#D5EBFF"
                    },
                    "value":"APPROVED",
                    "completed":true
                }],
                "summary":{"signer_count":1,"completed_count":0,"signers":[]},
                "signing_urls":[]
            },
            "artifacts":{"original":"https://example.com/original.pdf"},
            "is_closed":false,
            "signing_url":"https://example.com/sign/doc-1",
            "decline_reason":null,
            "declined_by":null,
            "tags":[],
            "pages":[{
                "id":"page-1",
                "number":1,
                "height":1651,
                "width":1275,
                "download_url":"https://example.com/page-1.png"
            }],
            "created_at":"2026-08-21T12:00:00Z",
            "updated_at":"2026-08-21T12:01:00Z"
        }
        """.trimIndent()

    @Test
    fun `self sends query authentication and parses signature state`() = runTest {
        val http = mockWith(
            """$signerJson""".dropLast(1) +
                ""","has_signature":true,"has_initial":false,"is_signature_reusable":true}""",
        )

        val signer = SignerDocumentResource(http).self("code+/=")

        assertThat(http.lastCall()).isEqualTo(
            MockApiHttpClient.Call("GET", "/signers/self?signer-access-code=code%2B%2F%3D"),
        )
        assertThat(signer.id).isEqualTo("signer-1")
        assertThat(signer.fullName).isEqualTo("Example Signer")
        assertThat(signer.hasSignature).isTrue
        assertThat(signer.hasInitial).isFalse
        assertThat(signer.isSignatureReusable).isTrue
    }

    @Test
    fun `getCurrent encodes path and code and parses the full document`() = runTest {
        val http = mockWith(documentJson)

        val document = SignerDocumentResource(http).getCurrent("signer/1", "code+/=")

        assertThat(http.lastCall().path)
            .isEqualTo("/signers/signer%2F1/document?signer-access-code=code%2B%2F%3D")
        assertThat(document.id).isEqualTo("doc-1")
        assertThat(document.assignment?.id).isEqualTo("assignment-1")
        assertThat(document.assignment?.signers?.single()?.verificationMethod).isEqualTo("Email")
        val item = requireNotNull(document.assignment?.items?.single())
        assertThat(item.id).isEqualTo("item-1")
        assertThat(item.page?.id).isEqualTo("page-1")
        assertThat(item.page?.number).isEqualTo(1)
        assertThat(item.page?.height).isEqualTo(1651)
        assertThat(item.page?.width).isEqualTo(1275)
        assertThat(item.page?.downloadUrl).isEqualTo("https://example.com/page-1.png")
        val itemSigner = requireNotNull(item.signer)
        assertThat(itemSigner.id).isEqualTo("signer-1")
        assertThat(itemSigner.fullName).isEqualTo("Example Signer")
        assertThat(itemSigner.email).isEqualTo("signer@example.com")
        assertThat(itemSigner.whatsappPhoneNumber).isEqualTo("+15555550100")
        assertThat(itemSigner.hasAcceptedTerms).isTrue
        assertThat(item.field?.resource).isEqualTo("field")
        assertThat(item.field?.id).isEqualTo("field-1")
        assertThat(item.field?.name).isEqualTo("Approval")
        assertThat(item.field?.type).isEqualTo("text")
        assertThat(item.field?.regex).isEqualTo("^[A-Z]+$")
        assertThat(item.field?.isPreDefined).isFalse
        assertThat(item.field?.isActive).isTrue
        assertThat(item.field?.isRequired).isTrue
        assertThat(item.field?.isStandard).isFalse
        assertThat(item.field?.isReadOnly).isFalse
        assertThat(item.field?.isVisible).isTrue
        assertThat(item.displaySettings).isInstanceOf(Map::class.java)
        @Suppress("UNCHECKED_CAST")
        val displaySettings = item.displaySettings as Map<String, Any>
        assertThat(displaySettings).containsEntry("left", 69.0)
            .containsEntry("top", 282.0)
            .containsEntry("width", 421.0)
            .containsEntry("height", 45.86)
            .containsEntry("fontSize", 18.0)
            .containsEntry("fontFamily", "Arial")
            .containsEntry("backgroundColor", "#D5EBFF")
        assertThat(item.value).isEqualTo("APPROVED")
        assertThat(item.completed).isTrue
        assertThat(requireNotNull(document.pages).single().downloadUrl).isEqualTo("https://example.com/page-1.png")
    }

    @Test
    fun `getAssignment sends terms query and parses signer-visible assignment`() = runTest {
        val http = mockWith(documentJson)

        val document = SignerDocumentResource(http).getAssignment("code+/=", hasAcceptedTerms = false)

        assertThat(http.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "GET",
                "/sign?signer-access-code=code%2B%2F%3D&has_accepted_terms=false",
            ),
        )
        assertThat(document.status).isEqualTo("pending_signature")
        assertThat(document.assignment?.items).hasSize(1)
    }

    @Test
    fun `sign posts the exact item array and parses the result object`() = runTest {
        val http = mockWith("""{"signed":true,"completed_items":1}""")
        val entries = listOf(
            SignAssignmentItemRequest(
                itemId = "item-1",
                fieldId = "field-1",
                pageId = "page-1",
                value = "Example Signer",
            ),
        )

        val result = SignerDocumentResource(http).sign("doc/1", "assignment 1", "code+/=", entries)

        assertThat(http.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "POST",
                "/documents/doc%2F1/assignments/assignment%201?signer-access-code=code%2B%2F%3D",
                """[{"itemId":"item-1","fieldId":"field-1","pageId":"page-1","value":"Example Signer"}]""",
            ),
        )
        assertThat(result["signed"]).isEqualTo(true)
        assertThat(result["completed_items"]).isEqualTo(1.0)
    }

    @Test
    fun `decline puts the exact reason body with query authentication`() = runTest {
        val http = mockWith("[]")

        SignerDocumentResource(http).decline("doc-1", "assignment-1", "code+/=", "Wrong terms")

        assertThat(http.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "PUT",
                "/documents/doc-1/assignments/assignment-1/reject?signer-access-code=code%2B%2F%3D",
                """{"decline_reason":"Wrong terms"}""",
            ),
        )
    }

    @Test
    fun `signMultiple puts only document ids and query authentication`() = runTest {
        val http = mockWith("[]")

        SignerDocumentResource(http).signMultiple(listOf("doc-1", "doc-2"), "code+/=")

        assertThat(http.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "PUT",
                "/signers/documents/sign-multiple?signer-access-code=code%2B%2F%3D",
                """{"document_ids":["doc-1","doc-2"]}""",
            ),
        )
    }

    @Test
    fun `declineMultiple puts document ids and reason with query authentication`() = runTest {
        val http = mockWith("[]")

        SignerDocumentResource(http).declineMultiple(listOf("doc-1"), "Wrong terms", "code+/=")

        assertThat(http.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "PUT",
                "/signers/documents/decline-multiple?signer-access-code=code%2B%2F%3D",
                """{"document_ids":["doc-1"],"decline_reason":"Wrong terms"}""",
            ),
        )
    }

    @Test
    fun `verifyEmail posts only the exact hyphenated OTP key`() = runTest {
        val http = mockWith("{}")

        SignerDocumentResource(http).verifyEmail("code+/=", VerifySignerEmailRequest("123456"))

        val call = http.lastCall()
        assertThat(call).isEqualTo(
            MockApiHttpClient.Call(
                "POST",
                "/verify?signer-access-code=code%2B%2F%3D",
                """{"verification-code":"123456"}""",
            ),
        )
        assertThat(call.body).doesNotContain("signer-access-code")
    }

    @Test
    fun `confirmData puts official identity fields and parses the signer`() = runTest {
        val http = mockWith(signerJson)

        val signer = SignerDocumentResource(http).confirmData(
            "doc/1",
            "code+/=",
            ConfirmSignerDataRequest(
                fullName = " Example Signer ",
                email = " signer@example.com ",
                governmentId = " 12345678900 ",
            ),
        )

        assertThat(http.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "PUT",
                "/documents/doc%2F1/signers/confirm-data?signer-access-code=code%2B%2F%3D",
                """{"full_name":"Example Signer","email":"signer@example.com","government_id":"12345678900"}""",
            ),
        )
        assertThat(signer.id).isEqualTo("signer-1")
        assertThat(signer.hasAcceptedTerms).isTrue
    }

    @Test
    fun `acceptTerms puts no body and keeps the code in the query`() = runTest {
        val http = mockWith("{}")

        SignerDocumentResource(http).acceptTerms("code+/=")

        assertThat(http.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "PUT",
                "/signers/accept-terms?signer-access-code=code%2B%2F%3D",
                body = null,
            ),
        )
    }

    @Test
    fun `uploadSignature posts the raw PNG with optional type and reuse queries`() = runTest {
        val delegate = mockWith("{}")
        var sentBytes: ByteArray? = null
        var sentContentType: String? = null
        val http = object : ApiHttpClient by delegate {
            override suspend fun postSignature(
                path: String,
                imageData: ByteArray,
                contentType: String,
            ): HttpRawResponse {
                sentBytes = imageData
                sentContentType = contentType
                return delegate.postSignature(path, imageData, contentType)
            }
        }
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)

        SignerDocumentResource(http).uploadSignature(
            signerAccessCode = "code+/=",
            imageData = png,
            type = SignatureType.INITIAL,
            reuse = true,
        )

        assertThat(delegate.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "POST_SIGNATURE",
                "/signature?signer-access-code=code%2B%2F%3D&type=initial&reuse=true",
                "image/png",
            ),
        )
        assertThat(sentBytes).containsExactly(*png)
        assertThat(sentContentType).isEqualTo("image/png")
    }

    @Test
    fun `downloadSignature fetches the selected image with query authentication`() = runTest {
        val png = byteArrayOf(1, 2, 3)
        val http = MockApiHttpClient(binaryResponse = png)

        val result = SignerDocumentResource(http).downloadSignature("code+/=", "drawn initial")

        assertThat(http.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "GET_BINARY",
                "/signature/drawn%20initial?signer-access-code=code%2B%2F%3D",
            ),
        )
        assertThat(result).containsExactly(*png)
    }

    @Test
    fun `list sends only documented pagination and parses documents plus headers`() = runTest {
        val http = MockApiHttpClient()
        http.enqueue(
            envelope(
                "[$documentJson]",
                mapOf(
                    "x-pagination-current-page" to "2",
                    "x-pagination-per-page" to "25",
                    "x-pagination-total-count" to "26",
                    "x-pagination-page-count" to "2",
                ),
            ),
        )

        val result = SignerDocumentResource(http).list(
            "signer/1",
            "code+/=",
            ListParams(page = 2, perPage = 25, search = "must-not-be-sent"),
        )

        assertThat(http.lastCall().path).isEqualTo(
            "/signers/signer%2F1/documents?signer-access-code=code%2B%2F%3D&page=2&per-page=25",
        )
        assertThat(result.data.single().name).isEqualTo("Agreement.pdf")
        assertThat(result.data.single().assignment?.id).isEqualTo("assignment-1")
        assertThat(result.meta?.currentPage).isEqualTo(2)
        assertThat(result.meta?.perPage).isEqualTo(25)
        assertThat(result.meta?.total).isEqualTo(26)
        assertThat(result.meta?.lastPage).isEqualTo(2)
    }

    @Test
    fun `search sends only search and access code and parses documents`() = runTest {
        val http = mockWith("[$documentJson]")

        val result = SignerDocumentResource(http).search("signer/1", "code+/=", " NDA & addendum ")

        assertThat(http.lastCall().path).isEqualTo(
            "/signers/signer%2F1/documents/search?signer-access-code=code%2B%2F%3D&search=NDA%20%26%20addendum",
        )
        assertThat(result.data.single().id).isEqualTo("doc-1")
        assertThat(requireNotNull(result.data.single().pages).single().id).isEqualTo("page-1")
    }

    @Test
    fun `download uses the public artifact route without any credentials or query`() = runTest {
        val pdf = "%PDF".toByteArray()
        val http = MockApiHttpClient(binaryResponse = pdf)

        val result = SignerDocumentResource(http).download("signer/1", "doc 1", DocumentArtifact.CERTIFICATED)

        assertThat(http.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "GET_BINARY",
                "/signers/signer%2F1/documents/doc%201/download/certificated",
            ),
        )
        assertThat(http.lastCall().path).doesNotContain("?").doesNotContain("code")
        assertThat(result).containsExactly(*pdf)
    }

    @Test
    fun `invalid signer requests fail before using the transport`() {
        val http = MockApiHttpClient()
        val resource = SignerDocumentResource(http)

        assertThatThrownBy { runBlocking { resource.self(" ") } }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { resource.signMultiple(emptyList(), "code") } }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { resource.uploadSignature("code", ByteArray(0)) } }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { resource.download("signer", "doc", "unknown") } }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy {
            runBlocking { resource.list("signer", "code", ListParams(page = 0, perPage = 101)) }
        }.isInstanceOf(ValidationException::class.java)
        assertThat(http.callCount()).isZero()
    }

    private fun mockWith(dataJson: String): MockApiHttpClient =
        MockApiHttpClient(defaultResponse = envelope(dataJson))

    private fun envelope(
        dataJson: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpRawResponse = HttpRawResponse(200, """{"status":200,"data":$dataJson}""", headers)
}
