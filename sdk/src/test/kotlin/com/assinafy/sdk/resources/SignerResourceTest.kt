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
}
