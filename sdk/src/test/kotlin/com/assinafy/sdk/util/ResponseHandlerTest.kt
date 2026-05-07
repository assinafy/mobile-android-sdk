package com.assinafy.sdk.util

import com.assinafy.sdk.exceptions.ApiException
import com.assinafy.sdk.exceptions.AssinafyException
import com.assinafy.sdk.exceptions.NetworkException
import com.assinafy.sdk.http.HttpRawResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.IOException

data class TestModel(val id: String = "", val name: String = "")

class ResponseHandlerTest {

    @Test
    fun `handle returns data on 2xx envelope`() {
        val response = HttpRawResponse(200, """{"status":200,"data":{"id":"123","name":"Test"}}""", emptyMap())
        val result = ResponseHandler.handle(response, TestModel::class.java)
        assertThat(result.id).isEqualTo("123")
        assertThat(result.name).isEqualTo("Test")
    }

    @Test
    fun `handle throws ApiException on non-2xx HTTP status`() {
        val response = HttpRawResponse(404, """{"message":"Not found"}""", emptyMap())
        assertThatThrownBy {
            ResponseHandler.handle(response, TestModel::class.java)
        }.isInstanceOf(ApiException::class.java)
            .hasFieldOrPropertyWithValue("statusCode", 404)
    }

    @Test
    fun `handle throws ApiException on non-2xx envelope status`() {
        val response = HttpRawResponse(200, """{"status":400,"message":"Bad request","data":{}}""", emptyMap())
        assertThatThrownBy {
            ResponseHandler.handle(response, TestModel::class.java)
        }.isInstanceOf(ApiException::class.java)
    }

    @Test
    fun `handle passes through when no envelope is present`() {
        val response = HttpRawResponse(200, """{"id":"456","name":"Direct"}""", emptyMap())
        val result = ResponseHandler.handle(response, TestModel::class.java)
        assertThat(result.id).isEqualTo("456")
    }

    @Test
    fun `handleList returns data array and meta from headers`() {
        val response = HttpRawResponse(
            200,
            """{"status":200,"data":[{"id":"1","name":"A"},{"id":"2","name":"B"}]}""",
            mapOf(
                "x-pagination-current-page" to "1",
                "x-pagination-per-page" to "20",
                "x-pagination-total-count" to "2",
                "x-pagination-page-count" to "1",
            ),
        )
        val result = ResponseHandler.handleList(response, TestModel::class.java)
        assertThat(result.data).hasSize(2)
        assertThat(result.data[0].id).isEqualTo("1")
        assertThat(result.meta?.currentPage).isEqualTo(1)
        assertThat(result.meta?.total).isEqualTo(2)
    }

    @Test
    fun `handleVoid succeeds on 2xx`() {
        ResponseHandler.handleVoid(HttpRawResponse(204, null, emptyMap()))
    }

    @Test
    fun `handleVoid throws ApiException on non-2xx`() {
        assertThatThrownBy {
            ResponseHandler.handleVoid(HttpRawResponse(400, null, emptyMap()))
        }.isInstanceOf(ApiException::class.java)
    }

    @Test
    fun `toSdkException passes AssinafyException through unchanged`() {
        val original = ApiException("original", 400)
        assertThat(ResponseHandler.toSdkException(original, "ignored")).isSameAs(original)
    }

    @Test
    fun `toSdkException wraps IOException as NetworkException`() {
        val e = IOException("connection refused")
        val result = ResponseHandler.toSdkException(e, "upload")
        assertThat(result).isInstanceOf(NetworkException::class.java)
        assertThat(result.message).contains("upload")
    }

    @Test
    fun `toSdkException wraps plain Exception as AssinafyException`() {
        val e = RuntimeException("boom")
        val result = ResponseHandler.toSdkException(e, "failed")
        assertThat(result).isInstanceOf(AssinafyException::class.java)
        assertThat(result.message).contains("failed")
    }

    @Test
    fun `handleList returns empty list on empty response body`() {
        val response = HttpRawResponse(200, null, emptyMap())
        val result = ResponseHandler.handleList(response, TestModel::class.java)
        assertThat(result.data).isEmpty()
    }
}
