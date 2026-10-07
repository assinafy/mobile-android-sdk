package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.ApiException
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.CreateWebhookEndpointRequest
import com.assinafy.sdk.request.UpdateWebhookEndpointRequest
import com.assinafy.sdk.request.WebhookDispatchParams
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class WebhookEndpointTest {

    private val endpointJson = """{"id":"ep1","name":"ERP","url":"https://example.com/wh","email":"ops@example.com",""" +
        """"events":["document_ready"],"is_active":true,"signing_enabled":true,"created_at":"2026-10-01T12:00:00Z","updated_at":"2026-10-01T12:00:00Z"}"""

    private fun ok(data: String) = HttpRawResponse(200, """{"status":200,"message":"","data":$data}""", emptyMap())

    private fun body(call: MockApiHttpClient.Call): Map<*, *> = Gson().fromJson(call.body, Map::class.java)

    @Test
    fun `listEndpoints gets the endpoints collection`() = runTest {
        val mock = MockApiHttpClient().apply { enqueue(ok("[$endpointJson]")) }

        val endpoints = WebhookResource(mock, "acc").listEndpoints()

        assertThat(mock.lastCall().method).isEqualTo("GET")
        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/webhooks/endpoints")
        assertThat(endpoints.single().signingEnabled).isTrue
        assertThat(endpoints.single().name).isEqualTo("ERP")
    }

    @Test
    fun `createEndpoint posts the complete body`() = runTest {
        val mock = MockApiHttpClient().apply { enqueue(ok(endpointJson)) }

        val created = WebhookResource(mock, "acc").createEndpoint(
            CreateWebhookEndpointRequest(
                url = " https://example.com/wh ",
                email = "ops@example.com",
                events = listOf("document_ready"),
                name = "ERP",
                signingEnabled = true,
            ),
        )

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("POST")
        assertThat(call.path).isEqualTo("/accounts/acc/webhooks/endpoints")
        assertThat(body(call)).isEqualTo(
            mapOf(
                "url" to "https://example.com/wh",
                "email" to "ops@example.com",
                "events" to listOf("document_ready"),
                "name" to "ERP",
                "signing_enabled" to true,
            ),
        )
        assertThat(created.id).isEqualTo("ep1")
    }

    @Test
    fun `createEndpoint validates before sending`() {
        val mock = MockApiHttpClient()
        val resource = WebhookResource(mock, "acc")
        listOf(
            CreateWebhookEndpointRequest("ftp://example.com", "ops@example.com", listOf("document_ready")),
            CreateWebhookEndpointRequest("https://example.com", "not-an-email", listOf("document_ready")),
            CreateWebhookEndpointRequest("https://example.com", "ops@example.com", emptyList()),
        ).forEach { request ->
            assertThatThrownBy { runBlocking { resource.createEndpoint(request) } }.isInstanceOf(ValidationException::class.java)
        }
        assertThat(mock.callCount()).isZero
    }

    @Test
    fun `createEndpoint surfaces the plan limit as a 403 ApiException`() {
        val mock = MockApiHttpClient().apply {
            enqueue(HttpRawResponse(403, """{"status":403,"message":"limit","data":null}""", emptyMap()))
        }
        assertThatThrownBy {
            runBlocking {
                WebhookResource(mock, "acc").createEndpoint(
                    CreateWebhookEndpointRequest("https://example.com/wh", "ops@example.com", listOf("document_ready")),
                )
            }
        }.isInstanceOf(ApiException::class.java).extracting("statusCode").isEqualTo(403)
    }

    @Test
    fun `getEndpoint encodes the id and returns null on 404`() = runTest {
        val mock = MockApiHttpClient().apply {
            enqueue(ok(endpointJson))
            enqueue(HttpRawResponse(404, """{"status":404,"message":"","data":null}""", emptyMap()))
        }
        val resource = WebhookResource(mock, "acc")

        assertThat(resource.getEndpoint("ep 1")?.id).isEqualTo("ep1")
        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/webhooks/endpoints/ep%201")
        assertThat(resource.getEndpoint("gone")).isNull()
    }

    @Test
    fun `updateEndpoint sends only supplied fields and rejects an empty update`() = runTest {
        val mock = MockApiHttpClient().apply { enqueue(ok(endpointJson)) }
        val resource = WebhookResource(mock, "acc")

        resource.updateEndpoint("ep1", UpdateWebhookEndpointRequest(isActive = false, signingEnabled = true))

        assertThat(mock.lastCall().method).isEqualTo("PUT")
        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/webhooks/endpoints/ep1")
        assertThat(body(mock.lastCall())).isEqualTo(mapOf("is_active" to false, "signing_enabled" to true))
        assertThatThrownBy { runBlocking { resource.updateEndpoint("ep1", UpdateWebhookEndpointRequest()) } }
            .isInstanceOf(ValidationException::class.java)
        assertThat(mock.callCount()).isEqualTo(1)
    }

    @Test
    fun `deleteEndpoint deletes the endpoint`() = runTest {
        val mock = MockApiHttpClient().apply { enqueue(ok("[]")) }

        WebhookResource(mock, "acc").deleteEndpoint("ep1")

        assertThat(mock.lastCall().method).isEqualTo("DELETE")
        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/webhooks/endpoints/ep1")
    }

    @Test
    fun `secret get and rotate hit their routes and redact the value`() = runTest {
        val mock = MockApiHttpClient().apply {
            enqueue(ok("""{"secret":"whsec_old"}"""))
            enqueue(ok("""{"secret":"whsec_new"}"""))
        }
        val resource = WebhookResource(mock, "acc")

        val current = resource.getEndpointSecret("ep1")
        assertThat(mock.lastCall().method).isEqualTo("GET")
        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/webhooks/endpoints/ep1/secret")
        val rotated = resource.rotateEndpointSecret("ep1")
        assertThat(mock.lastCall().method).isEqualTo("POST")
        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/webhooks/endpoints/ep1/secret/rotate")

        assertThat(current.secret).isEqualTo("whsec_old")
        assertThat(rotated.secret).isEqualTo("whsec_new")
        assertThat(rotated.toString()).doesNotContain("whsec_new")
    }

    @Test
    fun `listDispatches filters by endpoint and parses endpoint_id`() = runTest {
        val mock = MockApiHttpClient().apply {
            enqueue(ok("""[{"id":"d1","event":"document_ready","endpoint_id":"ep1","delivered":true}]"""))
        }

        val page = WebhookResource(mock, "acc").listDispatches(WebhookDispatchParams(endpointId = "ep1", perPage = 5))

        assertThat(mock.lastCall().queryParams).isEqualTo(mapOf("endpoint_id" to "ep1", "per-page" to 5))
        assertThat(page.data.single().endpointId).isEqualTo("ep1")
    }
}
