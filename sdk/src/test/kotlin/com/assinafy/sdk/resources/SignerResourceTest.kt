package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.CreateSignerRequest
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.UpdateSignerRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SignerResourceTest {

    private val emptyListJson = """{"status":200,"data":[]}"""
    private val signerJson = """{"id":"s1","full_name":"John Doe","email":"john@example.com"}"""

    @Test
    fun `throws when updating without signer ID`() {
        assertThatThrownBy {
            runBlocking {
                SignerResource(MockApiHttpClient(), "acc").update("", UpdateSignerRequest(fullName = "Test"))
            }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `throws when deleting without signer ID`() {
        assertThatThrownBy {
            runBlocking { SignerResource(MockApiHttpClient(), "acc").delete("") }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `throws when no account ID is available`() {
        assertThatThrownBy {
            runBlocking { SignerResource(MockApiHttpClient()).create(CreateSignerRequest("Test", "test@test.com")) }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `rejects invalid email`() {
        assertThatThrownBy {
            runBlocking { SignerResource(MockApiHttpClient(), "acc").create(CreateSignerRequest("Test", "not-an-email")) }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `uses custom accountId when provided`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(HttpRawResponse(200, emptyListJson, emptyMap()))
        mock.enqueue(HttpRawResponse(200, """{"status":200,"data":$signerJson}""", emptyMap()))

        SignerResource(mock, "default-account").create(CreateSignerRequest("Test", "test@test.com"), "custom-account")

        assertThat(mock.calls.first { it.method == "POST" }.path).isEqualTo("/accounts/custom-account/signers")
    }

    @Test
    fun `uses default accountId when custom not provided`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(HttpRawResponse(200, emptyListJson, emptyMap()))
        mock.enqueue(HttpRawResponse(200, """{"status":200,"data":$signerJson}""", emptyMap()))

        SignerResource(mock, "default-account").create(CreateSignerRequest("Test", "test@test.com"))

        assertThat(mock.calls.first { it.method == "POST" }.path).isEqualTo("/accounts/default-account/signers")
    }

    @Test
    fun `list passes search via params`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = HttpRawResponse(200, emptyListJson, emptyMap()))
        SignerResource(mock, "acc").list(ListParams(search = "john@example.com"))

        assertThat(mock.calls.first { it.method == "GET" }.queryParams["search"]).isEqualTo("john@example.com")
    }

    @Test
    fun `list passes per-page using hyphenated query key`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = HttpRawResponse(200, emptyListJson, emptyMap()))
        SignerResource(mock, "acc").list(ListParams(perPage = 25))

        val call = mock.calls.first { it.method == "GET" }
        assertThat(call.queryParams["per-page"]).isEqualTo(25)
        assertThat(call.queryParams.containsKey("per_page")).isFalse
    }

    @Test
    fun `list returns meta parsed from X-Pagination headers`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            HttpRawResponse(
                200,
                emptyListJson,
                mapOf(
                    "x-pagination-current-page" to "2",
                    "x-pagination-per-page" to "20",
                    "x-pagination-total-count" to "45",
                    "x-pagination-page-count" to "3",
                ),
            ),
        )
        val result = SignerResource(mock, "acc").list(ListParams(page = 2))

        assertThat(result.meta?.currentPage).isEqualTo(2)
        assertThat(result.meta?.perPage).isEqualTo(20)
        assertThat(result.meta?.total).isEqualTo(45)
        assertThat(result.meta?.lastPage).isEqualTo(3)
    }

    @Test
    fun `findByEmail returns null when no match`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = HttpRawResponse(200, emptyListJson, emptyMap()))
        assertThat(SignerResource(mock, "acc").findByEmail("nobody@example.com")).isNull()
    }

    @Test
    fun `findByEmail returns a matching signer case-insensitively`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            HttpRawResponse(
                200,
                """{"status":200,"data":[{"id":"1","full_name":"John","email":"JOHN@EXAMPLE.COM"}]}""",
                emptyMap(),
            ),
        )
        assertThat(SignerResource(mock, "acc").findByEmail("john@example.com")?.id).isEqualTo("1")
    }

    @Test
    fun `create reuses existing signer by email before posting`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            HttpRawResponse(
                200,
                """{"status":200,"data":[{"id":"existing","full_name":"John","email":"john@example.com"}]}""",
                emptyMap(),
            ),
        )
        val result = SignerResource(mock, "acc").create(CreateSignerRequest("John", "john@example.com"))

        assertThat(result.id).isEqualTo("existing")
        assertThat(mock.calls.none { it.method == "POST" }).isTrue
    }

    @Test
    fun `create falls back to existing signer when API rejects duplicate with 400`() = runTest {
        val mock = MockApiHttpClient()
        // 1) proactive findByEmail finds nothing
        mock.enqueue(HttpRawResponse(200, emptyListJson, emptyMap()))
        // 2) POST loses the race and the API returns the live duplicate error (HTTP 400)
        mock.enqueue(
            HttpRawResponse(400, """{"status":400,"message":"Um signatário com este e-mail já existe.","data":null}""", emptyMap()),
        )
        // 3) recovery findByEmail now returns the existing signer
        mock.enqueue(
            HttpRawResponse(200, """{"status":200,"data":[{"id":"existing","full_name":"John","email":"john@example.com"}]}""", emptyMap()),
        )

        val result = SignerResource(mock, "acc").create(CreateSignerRequest("John", "john@example.com"))

        assertThat(result.id).isEqualTo("existing")
    }

    @Test
    fun `uploadSignature sends raw binary with the given content type`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = HttpRawResponse(204, null, emptyMap()))

        SignerResource(mock, "acc").uploadSignature("code", "initial", byteArrayOf(1, 2), "image/jpeg")

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("POST_SIGNATURE")
        assertThat(call.path).isEqualTo("/signature?signer-access-code=code&type=initial")
        assertThat(call.body).isEqualTo("image/jpeg")
    }

    @Test
    fun `create maps whatsapp_phone_number in request body`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(HttpRawResponse(200, emptyListJson, emptyMap()))
        mock.enqueue(HttpRawResponse(200, """{"status":200,"data":$signerJson}""", emptyMap()))

        SignerResource(mock, "acc").create(
            CreateSignerRequest(fullName = "John", email = "john@example.com", whatsappPhoneNumber = "+5548999990000"),
        )

        val postCall = mock.calls.first { it.method == "POST" }
        assertThat(postCall.body).contains("whatsapp_phone_number")
        assertThat(postCall.body).contains("+5548999990000")
    }

    @Test
    fun `getSelf sends signer access code as query params`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(HttpRawResponse(200, """{"status":200,"data":$signerJson}""", emptyMap()))

        SignerResource(mock, "acc").getSelf("access+/=")

        val call = mock.lastCall()
        assertThat(call.path).isEqualTo("/signers/self")
        assertThat(call.queryParams["signer-access-code"]).isEqualTo("access+/=")
    }

    @Test
    fun `uploadSignature encodes signer access code and type`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = HttpRawResponse(204, null, emptyMap()))

        SignerResource(mock, "acc").uploadSignature("access+/=", "drawn signature", byteArrayOf(1))

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("POST_SIGNATURE")
        assertThat(call.path).isEqualTo("/signature?signer-access-code=access%2B%2F%3D&type=drawn%20signature")
    }

    @Test
    fun `uploadSignature rejects empty image data`() {
        assertThatThrownBy {
            runBlocking { SignerResource(MockApiHttpClient(), "acc").uploadSignature("access", "drawn", ByteArray(0)) }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `downloadSignature encodes path and query values`() = runTest {
        val mock = MockApiHttpClient(binaryResponse = byteArrayOf(1))

        SignerResource(mock, "acc").downloadSignature("access+/=", "drawn signature")

        assertThat(mock.lastCall().path).isEqualTo("/signature/drawn%20signature?signer-access-code=access%2B%2F%3D")
    }
}
