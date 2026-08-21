package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.ApiException
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.CreateWorkspaceRequest
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
        assertThat(ws.roles).isNull()
        assertThat(ws.isDeleteAllowed).isNull()
    }

    @Test
    fun `create posts to the accounts endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(HttpRawResponse(200, """{"status":200,"data":{"id":"acc-9","name":"My WS"}}""", emptyMap()))

        val ws = WorkspaceResource(mock).create(
            CreateWorkspaceRequest(name = "My WS", notificationSenderType = "Account", primaryColor = "ff0066"),
        )

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("POST")
        assertThat(call.path).isEqualTo("/accounts")
        assertThat(call.body).contains("My WS").contains("ff0066").contains("notification_sender_type")
        assertThat(ws.id).isEqualTo("acc-9")
    }

    @Test
    fun `create throws when name is blank`() {
        assertThatThrownBy {
            runBlocking { WorkspaceResource(MockApiHttpClient()).create(CreateWorkspaceRequest(name = "")) }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `create rejects hash-prefixed account colors`() {
        assertThatThrownBy {
            runBlocking {
                WorkspaceResource(MockApiHttpClient()).create(
                    CreateWorkspaceRequest(name = "Test", primaryColor = "#ff0066"),
                )
            }
        }.isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("without #")
    }

    @Test
    fun `update puts to the account-by-id endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(HttpRawResponse(200, """{"status":200,"data":{"id":"acc-1","name":"Renamed"}}""", emptyMap()))

        WorkspaceResource(mock).update("acc-1", UpdateWorkspaceRequest(name = "Renamed"))

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("PUT")
        assertThat(call.path).isEqualTo("/accounts/acc-1")
    }

    @Test
    fun `list hits the accounts collection endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(HttpRawResponse(200, """{"status":200,"data":[{"id":"acc-1","name":"WS"}]}""", emptyMap()))

        val result = WorkspaceResource(mock).list()

        assertThat(mock.lastCall().path).isEqualTo("/accounts")
        assertThat(result.data).hasSize(1)
        assertThat(result.data.single().roles).isNull()
    }

    @Test
    fun `getTheme fetches and parses the theme endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            HttpRawResponse(
                200,
                """{"status":200,"data":{"account_name":"MT","primary_color":"2072b9","secondary_color":"ffffff","logo":null}}""",
                emptyMap(),
            ),
        )

        val theme = WorkspaceResource(mock).getTheme("acc-1")

        assertThat(mock.lastCall().method).isEqualTo("GET")
        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc-1/theme")
        assertThat(theme.accountName).isEqualTo("MT")
        assertThat(theme.primaryColor).isEqualTo("2072b9")
        assertThat(theme.logo).isNull()
    }

    @Test
    fun `getTheme throws without an account ID`() {
        assertThatThrownBy {
            runBlocking { WorkspaceResource(MockApiHttpClient()).getTheme("") }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `getLogo returns the raw bytes`() = runTest {
        val logoBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val mock = MockApiHttpClient(binaryResponse = logoBytes)

        val bytes = WorkspaceResource(mock).getLogo("acc-1")

        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc-1/logo")
        assertThat(bytes).isEqualTo(logoBytes)
    }

    @Test
    fun `getLogo returns null when the account has no logo (404)`() = runTest {
        val mock = MockApiHttpClient()
        mock.binaryError = ApiException("Arquivo de armazenamento não encontrado.", 404)

        assertThat(WorkspaceResource(mock).getLogo("acc-1")).isNull()
    }

    @Test
    fun `upload and delete logo use the documented routes`() = runTest {
        val mock = MockApiHttpClient()
        val resource = WorkspaceResource(mock)

        resource.uploadLogo("acc-1", byteArrayOf(1, 2), "logo.png")
        assertThat(mock.lastCall().method).isEqualTo("POST_MULTIPART_FILE")
        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc-1/logo")
        assertThat(mock.lastCall().body).startsWith("file:logo.png:image/png:")

        resource.deleteLogo("acc-1")
        assertThat(mock.lastCall().method).isEqualTo("DELETE")
        assertThat(mock.lastCall().path).isEqualTo("/accounts/acc-1/logo")
    }

    @Test
    fun `delete sends force only when explicitly requested`() = runTest {
        val mock = MockApiHttpClient()
        val resource = WorkspaceResource(mock)

        resource.delete("acc-1")
        assertThat(mock.lastCall().body).isNull()
        resource.delete("acc-1", force = true)
        assertThat(mock.lastCall().body).isEqualTo("""{"force":true}""")
    }

    @Test
    fun `getStats sends daily query and parses every counter`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            HttpRawResponse(
                200,
                """{"status":200,"data":[{"period":"2026-08-01","documents_uploaded":1,"documents_sent":2,"signature_requests":3,"signature_requests_notification_email":3,"signature_requests_notification_whatsapp":0,"signature_requests_notification_bypass":0,"signature_requests_verification_email":3,"signature_requests_verification_whatsapp":0,"signature_requests_verification_bypass":0,"signature_requests_verification_digital_certificate":0,"signature_requests_viewed":2,"signature_requests_completed":1,"documents_certified":1}]}""",
                emptyMap(),
            ),
        )

        val rows = WorkspaceResource(mock).getStats("acc-1", "daily", "2026-08")

        assertThat(mock.lastCall().queryParams).containsEntry("granularity", "daily").containsEntry("month", "2026-08")
        assertThat(rows.single().signatureRequests).isEqualTo(3)
    }
}
