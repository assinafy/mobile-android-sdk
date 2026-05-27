package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TagResourceTest {

    private val gson = Gson()
    private val tagJson = """{"resource":"tag","id":"t1","name":"Contracts","color":"ff8800"}"""

    private fun success(data: String) = HttpRawResponse(200, """{"status":200,"data":$data}""", emptyMap())

    @Test
    fun `list passes search query and hits tags endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(success("""[$tagJson]"""))

        val tags = TagResource(mock, "acc").list(search = "contract")

        val call = mock.lastCall()
        assertThat(call.path).isEqualTo("/accounts/acc/tags")
        assertThat(call.queryParams["search"]).isEqualTo("contract")
        assertThat(tags[0].name).isEqualTo("Contracts")
    }

    @Test
    fun `create posts name and color`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(success(tagJson))

        val tag = TagResource(mock, "acc").create("Contracts", "ff8800")

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("POST")
        assertThat(call.path).isEqualTo("/accounts/acc/tags")
        assertThat(call.body).contains("Contracts").contains("ff8800")
        assertThat(tag.id).isEqualTo("t1")
    }

    @Test
    fun `update clears color with explicit null when requested`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(success("""{"resource":"tag","id":"t1","name":"Contracts","color":null}"""))

        TagResource(mock, "acc").update("t1", clearColor = true)

        @Suppress("UNCHECKED_CAST")
        val body = gson.fromJson(mock.lastCall().body, Map::class.java) as Map<String, Any?>
        assertThat(body.containsKey("color")).isTrue
        assertThat(body["color"]).isNull()
    }

    @Test
    fun `delete with force appends the force query parameter`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = HttpRawResponse(200, """{"status":200,"data":{"deleted":true}}""", emptyMap()))

        TagResource(mock, "acc").delete("t1", force = true)

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("DELETE")
        assertThat(call.path).isEqualTo("/accounts/acc/tags/t1?force=true")
    }

    @Test
    fun `create requires a name`() {
        assertThatThrownBy {
            runBlocking { TagResource(MockApiHttpClient(), "acc").create("") }
        }.isInstanceOf(ValidationException::class.java)
    }
}
