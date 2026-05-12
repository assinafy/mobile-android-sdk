package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.UpdateWorkspaceRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class WorkspaceResourceTest {

    @Test
    fun `throws when getting workspace without account ID`() {
        assertThatThrownBy {
            runBlocking { WorkspaceResource(MockApiHttpClient()).get("") }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `throws when updating workspace without account ID`() {
        assertThatThrownBy {
            runBlocking { WorkspaceResource(MockApiHttpClient()).update("", UpdateWorkspaceRequest(name = "Test")) }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `throws when deleting workspace without account ID`() {
        assertThatThrownBy {
            runBlocking { WorkspaceResource(MockApiHttpClient()).delete("") }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `get parses primary_color and secondary_color`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            HttpRawResponse(
                200,
                """{"status":200,"data":{"id":"acc-1","name":"BM","primary_color":"#ff00aa","secondary_color":null,"created_at":"2023-07-18T19:27:29Z"}}""",
                emptyMap(),
            ),
        )

        val ws = WorkspaceResource(mock).get("acc-1")

        assertThat(ws.primaryColor).isEqualTo("#ff00aa")
        assertThat(ws.secondaryColor).isNull()
    }
}
