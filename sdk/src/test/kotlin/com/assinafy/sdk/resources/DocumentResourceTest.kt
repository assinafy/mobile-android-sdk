package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DocumentResourceTest {

    private val docUploadJson = """{"id":"doc-1","account_id":"acc","name":"test.pdf","status":"metadata_ready","created_at":"2024-01-01","updated_at":"2024-01-01","is_closed":false,"decline_reason":null,"declined_by":null}"""
    private val docDetailsJson = """{"id":"doc-1","account_id":"acc","name":"test.pdf","status":"metadata_ready","created_at":"2024-01-01","updated_at":"2024-01-01","is_closed":false}"""

    private fun successResponse(data: String) =
        HttpRawResponse(200, """{"status":200,"data":$data}""", emptyMap())

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
    fun `confirmSignerData encodes signer access code`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = HttpRawResponse(204, null, emptyMap()))

        DocumentResource(mock, "acc").confirmSignerData("doc-1", "access+/=", mapOf("cpf" to "123"))

        assertThat(mock.lastCall().path)
            .isEqualTo("/documents/doc-1/signers/confirm-data?signer-access-code=access%2B%2F%3D")
    }
}
