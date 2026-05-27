package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.RegisterWebhookRequest
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class WebhookResourceTest {

    private val gson = Gson()

    private fun successResponse(data: String) = HttpRawResponse(200, """{"status":200,"data":$data}""", emptyMap())

    @Test
    fun `register default events include document_prepared`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            successResponse("""{"url":"https://example.com/webhook","email":"ops@example.com","events":[],"is_active":true}"""),
        )

        WebhookResource(mock, "acc").register(
            RegisterWebhookRequest(url = "https://example.com/webhook", email = "ops@example.com"),
        )

        @Suppress("UNCHECKED_CAST")
        val body = gson.fromJson(mock.lastCall().body, Map::class.java) as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val events = body["events"] as List<String>
        assertThat(events).contains("document_ready", "document_prepared", "signer_signed_document")
    }

    @Test
    fun `listEventTypes calls global event-types endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse("[]"))

        WebhookResource(mock).listEventTypes()

        assertThat(mock.lastCall().path).isEqualTo("/webhooks/event-types")
    }

    @Test
    fun `listDispatches passes pagination headers through`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            HttpRawResponse(
                200,
                """{"status":200,"data":[]}""",
                mapOf(
                    "x-pagination-current-page" to "1",
                    "x-pagination-per-page" to "20",
                    "x-pagination-total-count" to "2",
                    "x-pagination-page-count" to "1",
                ),
            ),
        )

        val result = WebhookResource(mock, "acc").listDispatches()

        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/webhooks")
        assertThat(result.meta?.currentPage).isEqualTo(1)
        assertThat(result.meta?.perPage).isEqualTo(20)
        assertThat(result.meta?.total).isEqualTo(2)
        assertThat(result.meta?.lastPage).isEqualTo(1)
    }

    @Test
    fun `retryDispatch requires a dispatch id`() {
        assertThatThrownBy {
            runBlocking { WebhookResource(MockApiHttpClient(), "acc").retryDispatch("") }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `inactivate hits the documented endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            successResponse("""{"url":"https://example.com","email":"ops@example.com","events":[],"is_active":false}"""),
        )

        WebhookResource(mock, "acc").inactivate()

        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/webhooks/inactivate")
    }

    @Test
    fun `register throws when url is blank`() {
        assertThatThrownBy {
            runBlocking {
                WebhookResource(MockApiHttpClient(), "acc").register(
                    RegisterWebhookRequest(url = "", email = "ops@example.com"),
                )
            }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `register throws when url is not http`() {
        assertThatThrownBy {
            runBlocking {
                WebhookResource(MockApiHttpClient(), "acc").register(
                    RegisterWebhookRequest(url = "ftp://example.com", email = "ops@example.com"),
                )
            }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `register throws when email is blank`() {
        assertThatThrownBy {
            runBlocking {
                WebhookResource(MockApiHttpClient(), "acc").register(
                    RegisterWebhookRequest(url = "https://example.com", email = ""),
                )
            }
        }.isInstanceOf(ValidationException::class.java)
    }
}
