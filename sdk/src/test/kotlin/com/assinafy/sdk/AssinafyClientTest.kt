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
    fun `allows a credentialless client for login and public operations`() {
        val client = AssinafyClient.create(AssinafyClientConfig(accountId = "acc"))
        assertThat(client.authentication).isNotNull
        assertThat(client.signerDocuments).isNotNull
    }

    @Test
    fun `accepts apiKey credentials`() {
        val client = AssinafyClient.create(AssinafyClientConfig(apiKey = "k", accountId = "acc"))
        assertThat(client.documents).isNotNull
        assertThat(client.signers).isNotNull
        assertThat(client.workspaces).isNotNull
        assertThat(client.assignments).isNotNull
        assertThat(client.fields).isNotNull
        assertThat(client.users).isNotNull
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
    fun `rejects ambiguous credentials and invalid base URLs`() {
        assertThatThrownBy {
            AssinafyClient.create(AssinafyClientConfig(apiKey = "k", token = "t"))
        }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy {
            AssinafyClient.create(AssinafyClientConfig(baseUrl = "not-a-url"))
        }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy {
            AssinafyClient.create(AssinafyClientConfig(apiKey = "k", baseUrl = "http://api.example.com/v1"))
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `static create() builds a configured client`() {
        val client = AssinafyClient.create(apiKey = "k", accountId = "acc", webhookSecret = "s")
        assertThat(client.documents).isNotNull
    }

    @Test
    fun `static create rejects blank credentials and account`() {
        assertThatThrownBy { AssinafyClient.create(apiKey = " ", accountId = "acc") }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { AssinafyClient.create(apiKey = "k", accountId = " ") }
            .isInstanceOf(ValidationException::class.java)
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
    fun `config diagnostics redact every secret`() {
        val text = AssinafyClientConfig(
            apiKey = "api-secret",
            token = "bearer-secret",
            webhookSecret = "webhook-secret",
        ).toString()

        assertThat(text).contains("apiKey=***", "token=***", "webhookSecret=***")
        assertThat(text).doesNotContain("api-secret", "bearer-secret", "webhook-secret")
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
    fun `uploadAndRequestSignatures validates every email before uploading`() {
        val mock = MockApiHttpClient()
        val client = AssinafyClient.create(AssinafyClientConfig(apiKey = "k", accountId = "acc"), mock)

        assertThatThrownBy {
            runBlocking {
                client.uploadAndRequestSignatures(
                    UploadAndRequestSignaturesRequest(
                        fileData = "%PDF-1.7\n".toByteArray(),
                        fileName = "test.pdf",
                        signers = listOf(UploadAndRequestSignaturesRequest.SignerEntry("John", "invalid")),
                    ),
                )
            }
        }.isInstanceOf(ValidationException::class.java)
        assertThat(mock.calls).isEmpty()
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
                """{"status":200,"data":{"id":"doc-1","account_id":"acc","name":"test.pdf","status":"uploaded","created_at":"2024-01-01","updated_at":"2024-01-01","is_closed":false,"decline_reason":null,"declined_by":null}}""",
                emptyMap(),
            ),
        )
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
        mock.enqueue(
            HttpRawResponse(
                200,
                """{"status":200,"data":{"id":"doc-1","account_id":"acc","name":"test.pdf","status":"pending_signature","created_at":"2024-01-01","updated_at":"2024-01-01","is_closed":false,"decline_reason":null,"declined_by":null}}""",
                emptyMap(),
            ),
        )

        val client = AssinafyClient.create(AssinafyClientConfig(apiKey = "k", accountId = "acc"), mock)
        val result = client.uploadAndRequestSignatures(
            UploadAndRequestSignaturesRequest(
                fileData = "%PDF-1.7\n".toByteArray(),
                fileName = "test.pdf",
                signers = listOf(
                    UploadAndRequestSignaturesRequest.SignerEntry(name = "John", email = "john@example.com"),
                ),
            ),
        )

        assertThat(result.document.id).isEqualTo("doc-1")
        assertThat(result.document.status).isEqualTo("pending_signature")
        assertThat(result.assignment.id).isEqualTo("asg-1")
        assertThat(result.signerIds).containsExactly("s1")
    }
}
