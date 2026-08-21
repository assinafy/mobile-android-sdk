package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.CreateAssignmentRequest
import com.assinafy.sdk.request.SignerReference
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AssignmentResourceTest {

    private val gson = Gson()
    private val assignmentJson = """{"id":"asg-1","method":"virtual","signers":[]}"""

    private fun successResponse(data: String) = HttpRawResponse(200, """{"status":200,"data":$data}""", emptyMap())

    @Test
    fun `create posts to documents endpoint with normalised body`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse(assignmentJson))

        val result = AssignmentResource(mock, "acc").create(
            "doc-1",
            CreateAssignmentRequest(signers = listOf(SignerReference.ofId("s1"), SignerReference.ofId("s2"))),
        )

        val call = mock.lastCall()
        assertThat(call.path).isEqualTo("/documents/doc-1/assignments")
        assertThat(result.id).isEqualTo("asg-1")

        @Suppress("UNCHECKED_CAST")
        val body = gson.fromJson(call.body, Map::class.java) as Map<String, Any>
        assertThat(body["method"]).isEqualTo("virtual")
        assertThat((body["signers"] as List<*>)).hasSize(2)
    }

    @Test
    fun `create serializes copy receiver signer ids`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = successResponse(assignmentJson))

        AssignmentResource(mock, "acc").create(
            "doc-1",
            CreateAssignmentRequest(
                signers = listOf(SignerReference.ofId("signer-1")),
                copyReceivers = listOf(" copy-signer-1 ", "copy-signer-2"),
            ),
        )

        @Suppress("UNCHECKED_CAST")
        val body = gson.fromJson(mock.lastCall().body, Map::class.java) as Map<String, Any?>
        assertThat(body["copy_receivers"] as List<*>).containsExactly("copy-signer-1", "copy-signer-2")
    }

    @Test
    fun `create throws when signers list is empty`() {
        assertThatThrownBy {
            runBlocking {
                AssignmentResource(MockApiHttpClient(), "acc").create(
                    "doc-1",
                    CreateAssignmentRequest(signers = emptyList()),
                )
            }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `resendNotification requires all three IDs`() {
        val resource = AssignmentResource(MockApiHttpClient(), "acc")
        assertThatThrownBy {
            runBlocking { resource.resendNotification("", "a", "s") }
        }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy {
            runBlocking { resource.resendNotification("d", "", "s") }
        }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy {
            runBlocking { resource.resendNotification("d", "a", "") }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `estimateCost accepts signers without ids when methods supplied`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(HttpRawResponse(200, """{"status":200,"data":{"total_credits":0.45}}""", emptyMap()))

        AssignmentResource(mock, "acc").estimateCost(
            "doc-1",
            CreateAssignmentRequest(signers = listOf(SignerReference(verificationMethod = "Whatsapp"))),
        )

        val call = mock.lastCall()
        assertThat(call.path).isEqualTo("/documents/doc-1/assignments/estimate-cost")
        @Suppress("UNCHECKED_CAST")
        val body = gson.fromJson(call.body, Map::class.java) as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val signer = (body["signers"] as List<Map<String, Any>>)[0]
        assertThat(signer["verification_method"]).isEqualTo("Whatsapp")
        assertThat(signer.containsKey("id")).isFalse
    }

    @Test
    fun `create rejects every signer without an id`() {
        assertThatThrownBy {
            runBlocking {
                AssignmentResource(MockApiHttpClient(), "acc").create(
                    "doc-1",
                    CreateAssignmentRequest(signers = listOf(SignerReference(verificationMethod = "Email"))),
                )
            }
        }.isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("Signer ID")
    }

    @Test
    fun `create rejects an extreme non-contiguous step without allocating a range`() {
        val mock = MockApiHttpClient()

        assertThatThrownBy {
            runBlocking {
                AssignmentResource(mock, "acc").create(
                    "doc-1",
                    CreateAssignmentRequest(
                        signers = listOf(
                            SignerReference(id = "s1", step = 1),
                            SignerReference(id = "s2", step = Int.MAX_VALUE),
                        ),
                    ),
                )
            }
        }.isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("contiguous")
        assertThat(mock.calls).isEmpty()
    }

    @Test
    fun `list sends pagination and explicit legacy accountId query`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = successResponse("[]"))

        AssignmentResource(mock, "acc").list(
            com.assinafy.sdk.request.ListParams(page = 2, perPage = 25),
            accountId = "acc",
        )

        assertThat(mock.lastCall().path).isEqualTo("/assignments")
        assertThat(mock.lastCall().queryParams).containsEntry("accountId", "acc")
            .containsEntry("page", 2)
            .containsEntry("per-page", 25)
    }

    @Test
    fun `list default query is OpenAPI exact`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = successResponse("[]"))

        AssignmentResource(mock, "acc").list(com.assinafy.sdk.request.ListParams(page = 1))

        assertThat(mock.lastCall().queryParams).containsOnlyKeys("page")
        assertThat(mock.lastCall().queryParams["accountId"]).isNull()
    }

    @Test
    fun `resetExpiration posts to correct endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse(assignmentJson))

        AssignmentResource(mock, "acc").resetExpiration("doc-1", "asg-1", "2026-12-31T00:00:00Z")

        assertThat(mock.lastCall().path).isEqualTo("/documents/doc-1/assignments/asg-1/reset-expiration")
    }

    @Test
    fun `resetExpiration explicit null opts into deployed clear compatibility`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse(assignmentJson))

        AssignmentResource(mock, "acc").resetExpiration("doc-1", "asg-1", null)

        // The frozen OpenAPI documents only a date-time; deployed services accept explicit null to clear it.
        assertThat(mock.lastCall().body).isEqualTo("""{"expires_at":null}""")
    }

    @Test
    fun `resetExpiration uses the authenticated transport`() = runTest {
        val authenticated = MockApiHttpClient(defaultResponse = successResponse(assignmentJson))
        val public = MockApiHttpClient()

        AssignmentResource(authenticated, "acc", publicHttp = public)
            .resetExpiration("doc-1", "asg-1", null)

        assertThat(authenticated.lastCall().path).contains("reset-expiration")
        assertThat(public.calls).isEmpty()
    }

    @Test
    fun `create serializes the sequential signing step field`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse(assignmentJson))

        AssignmentResource(mock, "acc").create(
            "doc-1",
            CreateAssignmentRequest(
                signers = listOf(
                    SignerReference(id = "s1", step = 1),
                    SignerReference(id = "s2", step = 2),
                ),
            ),
        )

        @Suppress("UNCHECKED_CAST")
        val body = gson.fromJson(mock.lastCall().body, Map::class.java) as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val signers = body["signers"] as List<Map<String, Any>>
        assertThat(signers[0]["step"]).isEqualTo(1.0)
        assertThat(signers[1]["step"]).isEqualTo(2.0)
    }

    @Test
    fun `decline puts to reject endpoint with access code and reason`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = HttpRawResponse(200, """{"status":200,"data":[]}""", emptyMap()))

        AssignmentResource(mock, "acc").decline("doc-1", "asg-1", "code+1", "Not agreed")

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("PUT")
        assertThat(call.path).isEqualTo("/documents/doc-1/assignments/asg-1/reject?signer-access-code=code%2B1")
        assertThat(call.body).contains("Not agreed")
    }

    @Test
    fun `decline uses the credentialless transport`() = runTest {
        val authenticated = MockApiHttpClient()
        val public = MockApiHttpClient(defaultResponse = HttpRawResponse(204, null, emptyMap()))

        AssignmentResource(authenticated, "acc", publicHttp = public)
            .decline("doc-1", "asg-1", "code", "No")

        assertThat(authenticated.calls).isEmpty()
        assertThat(public.lastCall().path).contains("signer-access-code=code")
    }

    @Test
    fun `listWhatsappNotifications parses rendered messages`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            successResponse(
                """[{"sent_at":1710000000,"header":"H","body":"B","buttons":[{"text":"Abrir"}],"phone_number":"+5511999990001","signer_id":"s1"}]""",
            ),
        )

        val notifications = AssignmentResource(mock, "acc").listWhatsappNotifications("doc-1", "asg-1")

        assertThat(mock.lastCall().path)
            .isEqualTo("/documents/doc-1/assignments/asg-1/whatsapp-notifications")
        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].phoneNumber).isEqualTo("+5511999990001")
        assertThat(notifications[0].buttons[0].text).isEqualTo("Abrir")
    }

    @Test
    fun `signing_urls is parsed as a list of {signer_id,url} objects`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            successResponse(
                """{"id":"asg-1","method":"virtual","signers":[],"signing_urls":[{"signer_id":"s1","url":"https://x/1"},{"signer_id":"s2","url":"https://x/2"}]}""",
            ),
        )
        val asg = AssignmentResource(mock, "acc").create(
            "doc-1",
            CreateAssignmentRequest(signers = listOf(SignerReference.ofId("s1"))),
        )
        val urls = requireNotNull(asg.signingUrls)
        assertThat(urls).hasSize(2)
        assertThat(urls[0].signerId).isEqualTo("s1")
        assertThat(urls[1].url).isEqualTo("https://x/2")
    }

    @Test
    fun `resendNotification puts to the signer resend endpoint and parses the response`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse("""{"is_sent":true,"document_id":"doc-1","signer_id":"s1"}"""))

        val result = AssignmentResource(mock, "acc").resendNotification("doc-1", "asg-1", "s1")

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("PUT")
        assertThat(call.path).isEqualTo("/documents/doc-1/assignments/asg-1/signers/s1/resend")
        assertThat(call.body).isNull()
        assertThat(result.isSent).isTrue
        assertThat(result.signerId).isEqualTo("s1")
    }

    @Test
    fun `resendNotification supports the deployed channel body`() = runTest {
        val mock = MockApiHttpClient(defaultResponse = successResponse("""{"is_sent":true}"""))

        AssignmentResource(mock, "acc").resendNotification("doc-1", "asg-1", "s1", " Email ")

        assertThat(mock.lastCall().body).isEqualTo("""{"channel":"email"}""")
        assertThatThrownBy {
            runBlocking { AssignmentResource(mock, "acc").resendNotification("doc-1", "asg-1", "s1", "sms") }
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `estimateResendCost posts to the estimate-resend-cost endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse("""{"total":0,"breakdown":[],"credit_balance":0,"has_sufficient_credits":true}"""))

        val cost = AssignmentResource(mock, "acc").estimateResendCost("doc-1", "asg-1", "s1")

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("POST")
        assertThat(call.path).isEqualTo("/documents/doc-1/assignments/asg-1/signers/s1/estimate-resend-cost")
        assertThat(cost.legacyHasSufficientCredits).isTrue
    }

    @Test
    fun `listWhatsappNotifications hits the whatsapp-notifications endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse("[]"))

        AssignmentResource(mock, "acc").listWhatsappNotifications("doc-1", "asg-1")

        assertThat(mock.lastCall().path).isEqualTo("/documents/doc-1/assignments/asg-1/whatsapp-notifications")
    }

    @Test
    fun `resetExpiration with a value serializes the trimmed date`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse(assignmentJson))

        AssignmentResource(mock, "acc").resetExpiration("doc-1", "asg-1", "  2026-12-31T00:00:00Z  ")

        assertThat(mock.lastCall().body).isEqualTo("""{"expires_at":"2026-12-31T00:00:00Z"}""")
    }

    @Test
    fun `copy_receivers in response is parsed as signer objects`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            successResponse(
                """{"id":"asg-1","method":"virtual","signers":[],"copy_receivers":[{"id":"cr1","full_name":"Obs One","email":"observer@example.com"}]}""",
            ),
        )
        val asg = AssignmentResource(mock, "acc").create(
            "doc-1",
            CreateAssignmentRequest(signers = listOf(SignerReference.ofId("s1"))),
        )
        val copies = requireNotNull(asg.copyReceivers)
        assertThat(copies).hasSize(1)
        assertThat(copies[0].id).isEqualTo("cr1")
        assertThat(copies[0].fullName).isEqualTo("Obs One")
    }
}
