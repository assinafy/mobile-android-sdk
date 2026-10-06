package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class BaseResourceTest {

    @Test
    fun `dot segment identifiers never resolve to a different API resource`() {
        val mock = MockApiHttpClient()
        listOf(".", "..").forEach { id ->
            assertThatThrownBy { runBlocking { DocumentResource(mock, "account").delete(id) } }
                .isInstanceOf(ValidationException::class.java)
            assertThatThrownBy { runBlocking { SignerResource(mock, id).delete("signer") } }
                .isInstanceOf(ValidationException::class.java)
        }
        org.assertj.core.api.Assertions.assertThat(mock.calls).isEmpty()
    }

    private class TestResource : BaseResource(MockApiHttpClient()) {
        suspend fun slowCall(): String = call("slow call", String::class.java) {
            delay(5_000)
            HttpRawResponse(200, """{"status":200,"data":"done"}""", emptyMap())
        }
    }

    @Test
    fun `resource calls preserve coroutine cancellation`() {
        assertThatThrownBy {
            runBlocking { withTimeout(50) { TestResource().slowCall() } }
        }.isInstanceOf(TimeoutCancellationException::class.java)
    }
}
