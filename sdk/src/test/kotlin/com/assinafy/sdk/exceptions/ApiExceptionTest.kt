package com.assinafy.sdk.exceptions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiExceptionTest {

    @Test
    fun `fromResponse extracts message from a parsed map`() {
        val e = ApiException.fromResponse(400, mapOf("message" to "Bad input", "status" to 400))
        assertThat(e.statusCode).isEqualTo(400)
        assertThat(e.message).isEqualTo("Bad input")
    }

    @Test
    fun `fromResponse parses a raw JSON string body (binary endpoint path)`() {
        val e = ApiException.fromResponse(404, """{"status":404,"message":"Document not found","data":null}""")
        assertThat(e.message).isEqualTo("Document not found")
        @Suppress("UNCHECKED_CAST")
        val data = e.responseData as Map<String, Any>
        assertThat(data["message"]).isEqualTo("Document not found")
    }

    @Test
    fun `fromResponse falls back to the raw string when body is not JSON`() {
        val e = ApiException.fromResponse(502, "upstream unavailable")
        assertThat(e.message).isEqualTo("upstream unavailable")
    }

    @Test
    fun `fromResponse uses error field when message is absent`() {
        val e = ApiException.fromResponse(403, mapOf("error" to "Forbidden"))
        assertThat(e.message).isEqualTo("Forbidden")
    }

    @Test
    fun `fromResponse defaults the message when nothing usable is present`() {
        val e = ApiException.fromResponse(500, null)
        assertThat(e.message).isEqualTo("API request failed")
        assertThat(e.responseData).isNull()
    }

    @Test
    fun `context omits responseData key when null`() {
        val e = ApiException("boom", 500, null)
        assertThat(e.context).containsEntry("statusCode", 500)
        assertThat(e.context).doesNotContainKey("responseData")
    }
}
