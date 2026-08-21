package com.assinafy.sdk.http

import com.assinafy.sdk.exceptions.ApiException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class OkHttpApiClientTest {

    private fun envelope(data: String = "{}") = MockResponse(body = """{"status":200,"data":$data}""")

    @Test
    fun `getBinary throws ApiException on non-2xx response`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 404, body = """{"message":"missing"}"""))
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
            assertThat(req.headers["X-Api-Key"]).isEqualTo("key-123")
            assertThat(req.headers["Authorization"]).isNull()
            assertThat(req.headers["Accept"]).isEqualTo("application/json")
            assertThat(req.headers["User-Agent"]).startsWith("assinafy-android-sdk/")
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
            assertThat(req.headers["Authorization"]).isEqualTo("Bearer jwt-xyz")
            assertThat(req.headers["X-Api-Key"]).isNull()
        }
    }

    @Test
    fun `credentials are not forwarded to a redirect on another origin`() {
        MockWebServer().use { first ->
            MockWebServer().use { redirected ->
                redirected.enqueue(envelope())
                redirected.start()
                first.enqueue(
                    MockResponse(
                        code = 302,
                        headers = headersOf("Location", redirected.url("/target").toString()),
                    ),
                )
                first.start()

                val client = OkHttpApiClient(first.url("/").toString(), apiKey = "secret", token = null)
                runBlocking { client.get("/redirect") }

                assertThat(first.takeRequest().headers["X-Api-Key"]).isEqualTo("secret")
                assertThat(redirected.takeRequest().headers["X-Api-Key"]).isNull()
            }
        }
    }

    @Test
    fun `cancelling a json request cancels the underlying call`() {
        MockWebServer().use { server ->
            server.enqueue(envelope().newBuilder().bodyDelay(5, TimeUnit.SECONDS).build())
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/").toString())
            val started = System.nanoTime()
            assertThatThrownBy {
                runBlocking { withTimeout(100) { client.get("/slow") } }
            }.isInstanceOf(kotlinx.coroutines.TimeoutCancellationException::class.java)
            assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)).isLessThan(2)
        }
    }

    @Test
    fun `query params are appended and null values dropped`() {
        MockWebServer().use { server ->
            server.enqueue(envelope("[]"))
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/").toString())
            runBlocking { client.get("/items", mapOf("search" to "a b", "per-page" to 20, "skip" to null)) }

            val url = server.takeRequest().url
            assertThat(url.queryParameter("search")).isEqualTo("a b")
            assertThat(url.queryParameter("per-page")).isEqualTo("20")
            assertThat(url.queryParameterNames).doesNotContain("skip")
        }
    }

    @Test
    fun `trailing-slash baseUrl is normalised against a leading-slash path`() {
        MockWebServer().use { server ->
            server.enqueue(envelope("[]"))
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/v1/").toString())
            runBlocking { client.get("/accounts") }

            assertThat(server.takeRequest().target).isEqualTo("/v1/accounts")
        }
    }

    @Test
    fun `response header keys are lowercased`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse(
                    headers = headersOf("X-Pagination-Current-Page", "2"),
                    body = """{"status":200,"data":[]}""",
                ),
            )
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/").toString())
            val resp = runBlocking { client.get("/items") }

            assertThat(resp.headers["x-pagination-current-page"]).isEqualTo("2")
        }
    }

    @Test
    fun `post with null body sends no payload`() {
        MockWebServer().use { server ->
            server.enqueue(envelope())
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/").toString())
            runBlocking { client.post("/things", null) }

            val req = server.takeRequest()
            assertThat(req.method).isEqualTo("POST")
            assertThat(req.bodySize).isZero()
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
            assertThat(req.headers["Content-Type"]).isEqualTo("image/png")
            assertThat(req.body!!.toByteArray()).containsExactly(1, 2, 3)
        }
    }

    @Test
    fun `patch sends the PATCH verb with the json body`() {
        MockWebServer().use { server ->
            server.enqueue(envelope())
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/").toString())
            runBlocking { client.patch("/documents/d1", """{"name":"Renamed.pdf"}""") }

            val req = server.takeRequest()
            assertThat(req.method).isEqualTo("PATCH")
            assertThat(req.body!!.utf8()).isEqualTo("""{"name":"Renamed.pdf"}""")
            assertThat(req.headers["Content-Type"]).contains("application/json")
        }
    }

    @Test
    fun `patch with null body sends no payload`() {
        MockWebServer().use { server ->
            server.enqueue(envelope())
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/").toString())
            runBlocking { client.patch("/documents/d1", null) }

            val req = server.takeRequest()
            assertThat(req.method).isEqualTo("PATCH")
            assertThat(req.bodySize).isZero()
        }
    }

    @Test
    fun `getBinary returns the raw response bytes on success`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(body = "PDF-RAW-BYTES"))
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/").toString())
            val bytes = runBlocking { client.getBinary("/documents/d/download/original") }

            assertThat(bytes.toString(Charsets.UTF_8)).isEqualTo("PDF-RAW-BYTES")
            assertThat(server.takeRequest().headers["Accept"]).isEqualTo("*/*")
        }
    }

    @Test
    fun `rate limits retry only read methods`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 429, headers = headersOf("Retry-After", "0")))
            server.enqueue(envelope())
            server.enqueue(MockResponse(code = 429, headers = headersOf("Retry-After", "0")))
            server.enqueue(MockResponse(code = 429, headers = headersOf("Retry-After", "0")))
            server.start()

            val client = OkHttpApiClient.forTesting(OkHttpClient(), server.url("/").toString())

            assertThat(runBlocking { client.get("/safe") }.statusCode).isEqualTo(200)
            assertThat(runBlocking { client.put("/unsafe-action", "{}") }.statusCode).isEqualTo(429)
            assertThat(runBlocking { client.post("/unsafe", "{}") }.statusCode).isEqualTo(429)
            assertThat(server.requestCount).isEqualTo(4)
        }
    }
}
