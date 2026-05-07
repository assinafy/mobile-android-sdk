package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.request.UpdateWorkspaceRequest
import kotlinx.coroutines.runBlocking
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
}
