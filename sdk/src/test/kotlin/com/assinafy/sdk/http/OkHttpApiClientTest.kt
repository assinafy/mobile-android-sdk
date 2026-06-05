package com.assinafy.sdk.http

import com.assinafy.sdk.exceptions.ApiException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OkHttpApiClientTest {

    private fun envelope(data: String = "{}") = MockResponse().setBody("""{"status":200,"data":$data}""")

    @Test
    fun `getBinary throws ApiException on non-2xx response`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"missing"}"""))
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/").toString())

            assertThatThrownBy {
                runBlocking { client.getBinary("/missing.pdf") }
            }.isInstanceOf(ApiException::class.java)
                .hasFieldOrPropertyWithValue("statusCode", 404)
                .hasMessageContaining("missing") // error envelope message is now surfaced, not "API request failed"
        }
    }

    @Test
    fun `apiKey is sent as X-Api-Key header and Authorization is absent`() {
        MockWebServer().use { server ->
            server.enqueue(envelope())
            server.start()

            val client = OkHttpApiClient(server.url("/").toString(), apiKey = "key-123", token = null)
            runBlocking { client.get("/ping") }

            val req = server.takeRequest()
            assertThat(req.getHeader("X-Api-Key")).isEqualTo("key-123")
            assertThat(req.getHeader("Authorization")).isNull()
            assertThat(req.getHeader("Accept")).isEqualTo("application/json")
            assertThat(req.getHeader("User-Agent")).startsWith("assinafy-android-sdk/")
        }
    }

    @Test
    fun `token is sent as Bearer Authorization when apiKey is blank`() {
        MockWebServer().use { server ->
            server.enqueue(envelope())
            server.start()

            val client = OkHttpApiClient(server.url("/").toString(), apiKey = null, token = "jwt-xyz")
            runBlocking { client.get("/ping") }

            val req = server.takeRequest()
            assertThat(req.getHeader("Authorization")).isEqualTo("Bearer jwt-xyz")
            assertThat(req.getHeader("X-Api-Key")).isNull()
        }
    }

    @Test
    fun `query params are appended and null values dropped`() {
        MockWebServer().use { server ->
            server.enqueue(envelope("[]"))
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/").toString())
            runBlocking { client.get("/items", mapOf("search" to "a b", "per-page" to 20, "skip" to null)) }

            val url = server.takeRequest().requestUrl
            assertThat(url?.queryParameter("search")).isEqualTo("a b")
            assertThat(url?.queryParameter("per-page")).isEqualTo("20")
            assertThat(url?.queryParameterNames).doesNotContain("skip")
        }
    }

    @Test
    fun `trailing-slash baseUrl is normalised against a leading-slash path`() {
        MockWebServer().use { server ->
            server.enqueue(envelope("[]"))
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/v1/").toString())
            runBlocking { client.get("/accounts") }

            assertThat(server.takeRequest().path).isEqualTo("/v1/accounts")
        }
    }

    @Test
    fun `response header keys are lowercased`() {
        MockWebServer().use { server ->
            server.enqueue(envelope("[]").addHeader("X-Pagination-Current-Page", "2"))
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/").toString())
            val resp = runBlocking { client.get("/items") }

            assertThat(resp.headers["x-pagination-current-page"]).isEqualTo("2")
        }
    }

    @Test
    fun `post with null body sends an empty json object`() {
        MockWebServer().use { server ->
            server.enqueue(envelope())
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/").toString())
            runBlocking { client.post("/things", null) }

            val req = server.takeRequest()
            assertThat(req.method).isEqualTo("POST")
            assertThat(req.body.readUtf8()).isEqualTo("{}")
            assertThat(req.getHeader("Content-Type")).contains("application/json")
        }
    }

    @Test
    fun `postSignature sends raw bytes with the given content type and no multipart`() {
        MockWebServer().use { server ->
            server.enqueue(envelope())
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/").toString())
            runBlocking { client.postSignature("/signature?type=initial", byteArrayOf(1, 2, 3), "image/png") }

            val req = server.takeRequest()
            assertThat(req.getHeader("Content-Type")).isEqualTo("image/png")
            assertThat(req.body.readByteArray()).containsExactly(1, 2, 3)
        }
    }

    @Test
    fun `getBinary returns the raw response bytes on success`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("PDF-RAW-BYTES"))
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/").toString())
            val bytes = runBlocking { client.getBinary("/documents/d/download/original") }

            assertThat(bytes.toString(Charsets.UTF_8)).isEqualTo("PDF-RAW-BYTES")
        }
    }
}
