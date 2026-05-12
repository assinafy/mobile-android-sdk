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

    private fun successResponse(data: String) =
        HttpRawResponse(200, """{"status":200,"data":$data}""", emptyMap())

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
    fun `resetExpiration posts to correct endpoint`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(successResponse(assignmentJson))

        AssignmentResource(mock, "acc").resetExpiration("doc-1", "asg-1", "2026-12-31T00:00:00Z")

        assertThat(mock.lastCall().path).isEqualTo("/documents/doc-1/assignments/asg-1/reset-expiration")
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
    fun `copy_receivers in response is parsed as signer objects`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            successResponse(
                """{"id":"asg-1","method":"virtual","signers":[],"copy_receivers":[{"id":"cr1","full_name":"Obs One","email":"obs1@x.com"}]}""",
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
