package com.assinafy.sdk

import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.UploadAndRequestSignaturesRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AssinafyClientTest {

    @Test
    fun `throws when no credentials are provided`() {
        assertThatThrownBy {
            AssinafyClient.create(AssinafyClientConfig(accountId = "acc"))
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `accepts apiKey credentials`() {
        val client = AssinafyClient.create(AssinafyClientConfig(apiKey = "k", accountId = "acc"))
        assertThat(client.documents).isNotNull
        assertThat(client.signers).isNotNull
        assertThat(client.workspaces).isNotNull
        assertThat(client.assignments).isNotNull
        assertThat(client.webhooks).isNotNull
        assertThat(client.templates).isNotNull
        assertThat(client.tags).isNotNull
        assertThat(client.webhookVerifier).isNotNull
    }

    @Test
    fun `accepts legacy token credentials`() {
        val client = AssinafyClient.create(AssinafyClientConfig(token = "t", accountId = "acc"))
        assertThat(client.documents).isNotNull
    }

    @Test
    fun `rejects invalid timeout`() {
        assertThatThrownBy {
            AssinafyClient.create(AssinafyClientConfig(apiKey = "k", accountId = "acc", timeoutMs = 0))
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `static create() builds a configured client`() {
        val client = AssinafyClient.create(apiKey = "k", accountId = "acc", webhookSecret = "s")
        assertThat(client.documents).isNotNull
    }

    @Test
    fun `accepts baseUrl with trailing slash`() {
        val client = AssinafyClient.create(
            AssinafyClientConfig(
                apiKey = "k",
                accountId = "acc",
                baseUrl = "https://sandbox.assinafy.com.br/v1/",
            ),
        )
        assertThat(client).isNotNull
    }

    @Test
    fun `uploadAndRequestSignatures throws when no signers provided`() {
        val mock = MockApiHttpClient()
        val client = AssinafyClient.create(AssinafyClientConfig(apiKey = "k", accountId = "acc"), mock)
        assertThatThrownBy {
            runBlocking {
                client.uploadAndRequestSignatures(
                    UploadAndRequestSignaturesRequest(
                        fileData = ByteArray(0),
                        fileName = "test.pdf",
                        signers = emptyList(),
                    ),
                )
            }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `uploadAndRequestSignatures throws when signer data is blank`() {
        val mock = MockApiHttpClient()
        val client = AssinafyClient.create(AssinafyClientConfig(apiKey = "k", accountId = "acc"), mock)

        assertThatThrownBy {
            runBlocking {
                client.uploadAndRequestSignatures(
                    UploadAndRequestSignaturesRequest(
                        fileData = ByteArray(100),
                        fileName = "test.pdf",
                        signers = listOf(
                            UploadAndRequestSignaturesRequest.SignerEntry(name = "", email = "john@example.com"),
                        ),
                    ),
                )
            }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `uploadAndRequestSignaturesRequest hashCode includes all equality fields`() {
        val left = UploadAndRequestSignaturesRequest(
            fileData = byteArrayOf(1),
            fileName = "test.pdf",
            signers = listOf(UploadAndRequestSignaturesRequest.SignerEntry("John", "john@example.com")),
            message = "A",
        )
        val right = left.copy(message = "B")

        assertThat(left).isNotEqualTo(right)
        assertThat(left.hashCode()).isNotEqualTo(right.hashCode())
    }

    @Test
    fun `uploadAndRequestSignatures completes full workflow`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            HttpRawResponse(
                200,
                """{"status":200,"data":{"id":"doc-1","account_id":"acc","name":"test.pdf","status":"metadata_ready","created_at":"2024-01-01","updated_at":"2024-01-01","is_closed":false,"decline_reason":null,"declined_by":null}}""",
                emptyMap(),
            ),
        )
        mock.enqueue(HttpRawResponse(200, """{"status":200,"data":[]}""", emptyMap()))
        mock.enqueue(
            HttpRawResponse(200, """{"status":200,"data":{"id":"s1","full_name":"John","email":"john@example.com"}}""", emptyMap()),
        )
        mock.enqueue(
            HttpRawResponse(200, """{"status":200,"data":{"id":"asg-1","method":"virtual","signers":[]}}""", emptyMap()),
        )

        val client = AssinafyClient.create(AssinafyClientConfig(apiKey = "k", accountId = "acc"), mock)
        val result = client.uploadAndRequestSignatures(
            UploadAndRequestSignaturesRequest(
                fileData = ByteArray(100),
                fileName = "test.pdf",
                signers = listOf(
                    UploadAndRequestSignaturesRequest.SignerEntry(name = "John", email = "john@example.com"),
                ),
                waitForReady = false,
            ),
        )

        assertThat(result.document.id).isEqualTo("doc-1")
        assertThat(result.assignment.id).isEqualTo("asg-1")
        assertThat(result.signerIds).containsExactly("s1")
    }
}
