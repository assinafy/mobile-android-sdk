package com.assinafy.sdk.http

import com.assinafy.sdk.exceptions.ApiException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OkHttpApiClientTest {

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
        }
    }
}
