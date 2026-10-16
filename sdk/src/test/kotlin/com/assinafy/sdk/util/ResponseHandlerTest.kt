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
    fun `a 403 exposes the insufficient_scope challenge so the caller reconnects instead of retrying`() {
        val response = HttpRawResponse(
            403,
            """{"status":403,"message":"You are not allowed to perform this action.","data":null}""",
            mapOf("www-authenticate" to """Bearer error="insufficient_scope", scope="documents:write""""),
        )

        val error = runCatching { ResponseHandler.handle(response, TestModel::class.java) }.exceptionOrNull() as ApiException

        assertThat(error.challenge!!.isInsufficientScope).isTrue
        assertThat(error.challenge!!.scope).isEqualTo("documents:write")
        assertThat(ApiException.fromResponse(403, null).challenge).isNull()
    }

    @Test
    fun `a 429 exposes the rate-limit headers and Retry-After`() {
        val response = HttpRawResponse(
            429,
            """{"status":429,"message":"Too Many Requests","data":null}""",
            mapOf("X-Rate-Limit-Limit" to "60", "x-rate-limit-remaining" to "0", "x-rate-limit-reset" to "17", "retry-after" to "17"),
        )

        val error = runCatching { ResponseHandler.handle(response, TestModel::class.java) }.exceptionOrNull() as ApiException

        assertThat(error.statusCode).isEqualTo(429)
        assertThat(error.rateLimitLimit).isEqualTo(60)
        assertThat(error.rateLimitRemaining).isEqualTo(0)
        assertThat(error.rateLimitReset).isEqualTo(17L)
        assertThat(error.retryAfter).isEqualTo(17L)
        assertThat(ApiException.fromResponse(429, null).retryAfter).isNull()
    }

    @Test
    fun `handle throws ApiException on non-2xx envelope status`() {
        val response = HttpRawResponse(200, """{"status":400,"message":"Bad request","data":{}}""", emptyMap())
        assertThatThrownBy {
            ResponseHandler.handle(response, TestModel::class.java)
        }.isInstanceOf(ApiException::class.java)
            .hasMessage("Bad request")
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
        ResponseHandler.handleVoid(HttpRawResponse(200, """{"status":200,"message":"OK"}""", emptyMap()))
    }

    @Test
    fun `handleVoid throws ApiException on non-2xx`() {
        assertThatThrownBy {
            ResponseHandler.handleVoid(HttpRawResponse(400, null, emptyMap()))
        }.isInstanceOf(ApiException::class.java)
    }

    @Test
    fun `handleVoid throws ApiException on non-2xx envelope without data`() {
        assertThatThrownBy {
            ResponseHandler.handleVoid(
                HttpRawResponse(200, """{"status":422,"message":"Invalid request"}""", emptyMap()),
            )
        }.isInstanceOf(ApiException::class.java)
            .hasMessage("Invalid request")
            .hasFieldOrPropertyWithValue("statusCode", 422)
    }

    @Test
    fun `handle rejects successful typed envelope without data`() {
        assertThatThrownBy {
            ResponseHandler.handle(
                HttpRawResponse(200, """{"status":200,"message":"OK"}""", emptyMap()),
                TestModel::class.java,
            )
        }.isInstanceOf(AssinafyException::class.java)
            .hasMessageContaining("Empty response data")
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

    @Test
    fun `handle throws when a typed value is expected but the body is empty`() {
        val response = HttpRawResponse(200, "", emptyMap())
        assertThatThrownBy {
            ResponseHandler.handle(response, TestModel::class.java)
        }.isInstanceOf(AssinafyException::class.java)
            .hasMessageContaining("Empty response body")
    }

    @Test
    fun `handleList parses pagination from mixed-case headers`() {
        val response = HttpRawResponse(
            200,
            """{"status":200,"data":[]}""",
            mapOf(
                "X-Pagination-Current-Page" to "3",
                "X-Pagination-Per-Page" to "10",
                "X-Pagination-Total-Count" to "55",
                "X-Pagination-Page-Count" to "6",
            ),
        )
        val result = ResponseHandler.handleList(response, TestModel::class.java)
        assertThat(result.meta?.currentPage).isEqualTo(3)
        assertThat(result.meta?.perPage).isEqualTo(10)
        assertThat(result.meta?.total).isEqualTo(55)
        assertThat(result.meta?.lastPage).isEqualTo(6)
    }

    @Test
    fun `handleVoid rejects an envelope with a malformed status`() {
        assertThatThrownBy {
            ResponseHandler.handleVoid(HttpRawResponse(200, """{"status":"ok","data":[]}""", emptyMap()))
        }.isInstanceOf(AssinafyException::class.java)
            .hasMessageContaining("status must be an integer")
    }

    @Test
    fun `handleList rejects non-array data`() {
        assertThatThrownBy {
            ResponseHandler.handleList(
                HttpRawResponse(200, """{"status":200,"data":{"id":"not-a-list"}}""", emptyMap()),
                TestModel::class.java,
            )
        }.isInstanceOf(AssinafyException::class.java)
            .hasMessageContaining("data must be an array")
    }

    @Test
    fun `every envelope error retains challenge and rate-limit headers`() {
        val response = HttpRawResponse(
            200,
            """{"status":403,"message":"Forbidden","data":null}""",
            mapOf("WWW-Authenticate" to """Bearer error="insufficient_scope", scope="documents:read"""", "Retry-After" to "17"),
        )
        val handlers = listOf<() -> Unit>(
            { ResponseHandler.handle(response, TestModel::class.java) },
            { ResponseHandler.handleList(response, TestModel::class.java) },
            { ResponseHandler.handleMap(response) },
            { ResponseHandler.handleVoid(response) },
        )
        handlers.forEach { handle ->
            val error = runCatching(handle).exceptionOrNull() as ApiException
            assertThat(error.challenge?.scope).isEqualTo("documents:read")
            assertThat(error.retryAfter).isEqualTo(17L)
        }
    }
}
