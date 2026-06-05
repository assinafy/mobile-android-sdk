package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.ListParams
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TemplateResourceTest {

    private fun success(data: String) = HttpRawResponse(200, """{"status":200,"data":$data}""", emptyMap())

    @Test
    fun `list hits account-scoped templates endpoint and forwards params`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(success("""[{"id":"tpl-1","name":"NDA","status":"active","created_at":"2024-01-01"}]"""))

        val result = TemplateResource(mock, "acc").list(ListParams(search = "NDA", perPage = 20, sort = "-updated_at"))

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("GET")
        assertThat(call.path).isEqualTo("/accounts/acc/templates")
        assertThat(call.queryParams["search"]).isEqualTo("NDA")
        assertThat(call.queryParams["per-page"]).isEqualTo(20)
        assertThat(call.queryParams["sort"]).isEqualTo("-updated_at")
        assertThat(result.data).hasSize(1)
        assertThat(result.data[0].name).isEqualTo("NDA")
    }

    @Test
    fun `get hits template-by-id endpoint and parses roles`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            success(
                """{"id":"tpl-1","name":"NDA","status":"active","created_at":"2024-01-01","roles":[{"id":"r1","name":"Signer"}]}""",
            ),
        )

        val template = TemplateResource(mock, "acc").get("tpl-1")

        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/templates/tpl-1")
        assertThat(template.roles).hasSize(1)
        assertThat(template.roles?.get(0)?.name).isEqualTo("Signer")
    }

    @Test
    fun `get encodes the template id`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(success("""{"id":"a b","name":"X","status":"active","created_at":"2024-01-01"}"""))

        TemplateResource(mock, "acc").get("a b")

        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc/templates/a%20b")
    }

    @Test
    fun `get throws when template id is blank`() {
        assertThatThrownBy {
            runBlocking { TemplateResource(MockApiHttpClient(), "acc").get("") }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `list throws when no account id is available`() {
        assertThatThrownBy {
            runBlocking { TemplateResource(MockApiHttpClient()).list() }
        }.isInstanceOf(ValidationException::class.java)
    }
}
