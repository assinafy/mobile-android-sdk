package com.assinafy.sdk.resources

import com.assinafy.sdk.ScheduledSendStatus
import com.assinafy.sdk.SignerCapacity
import com.assinafy.sdk.exceptions.ApiException
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.CreateAssignmentRequest
import com.assinafy.sdk.request.CreateEnvelopeRequest
import com.assinafy.sdk.request.EnvelopeDocumentRequest
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.SignerReference
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ScheduledSendTest {
    private val sendAt = System.currentTimeMillis() / 1_000 + 7_200
    private val assignment =
        CreateAssignmentRequest(
            signers = listOf(SignerReference(id = " signer ", signAs = SignerCapacity.WITNESS)),
            message = "Invitation",
            maxTotalCredits = BigDecimal("0.50"),
        )
    private val envelope =
        CreateEnvelopeRequest(
            " Example ",
            listOf(SignerReference.ofId(" signer ")),
            listOf(EnvelopeDocumentRequest(" doc ", listOf(" signer "))),
        )
    private val scheduled = """{"resource":"scheduled_send","id":"scheduled","type":"assignment","document_ids":["doc"],"status":"pending","send_at":"2027-01-01T12:00:00Z","assignment_id":null,"envelope_id":null,"failure_reason":null,"created_at":"2027-01-01T10:00:00Z","processed_at":null}"""

    private fun response(data: String) = HttpRawResponse(200, """{"status":200,"data":$data}""", emptyMap())

    private fun assertCall(
        mock: MockApiHttpClient,
        method: String,
        path: String,
        body: String? = null,
    ) {
        assertThat(mock.lastCall().method).isEqualTo(method)
        assertThat(mock.lastCall().path).isEqualTo(path)
        assertThat(mock.lastCall().body?.let(JsonParser::parseString)).isEqualTo(body?.let(JsonParser::parseString))
    }

    @Test
    fun `scheduling preserves assignment settings and exact credit ceilings`() =
        runTest {
            val mock = MockApiHttpClient(response(scheduled))
            val result = AssignmentResource(mock, "account").schedule(" doc/id ", assignment, sendAt)
            assertCall(
                mock,
                "POST",
                "/documents/doc%2Fid/scheduled-sends",
                """{"method":"virtual","signers":[{"id":"signer","sign_as":"Witness"}],"message":"Invitation","max_total_credits":0.50,"send_at":$sendAt}""",
            )
            assertThat(result.status).isEqualTo(ScheduledSendStatus.PENDING)
            assertThat(result.documentIds).containsExactly("doc")
            assertThat(result.assignmentId).isNull()
            assertThat(result.processedAt).isNull()
            assertThat(mock.callCount()).isEqualTo(1)
        }

    @Test
    fun `envelope schedule reuses normalized envelope payload without idempotency header`() =
        runTest {
            val mock = MockApiHttpClient(response(scheduled.replace("assignment", "envelope")))
            AssignmentResource(mock, "default").scheduleEnvelope(envelope, sendAt, "account/id")
            assertCall(
                mock,
                "POST",
                "/accounts/account%2Fid/scheduled-sends",
                """{"name":"Example","signers":[{"id":"signer"}],"documents":[{"id":"doc","signer_ids":["signer"],"method":"virtual"}],"send_at":$sendAt}""",
            )
            assertThat(mock.lastCall().idempotencyKey).isNull()
        }

    @Test
    fun `list sends keeps pagination headers and only supported query keys`() =
        runTest {
            val mock =
                MockApiHttpClient(HttpRawResponse(200, """{"status":200,"data":[$scheduled]}""", mapOf("X-Pagination-Total-Count" to "3")))
            val result =
                AssignmentResource(
                    mock,
                    "account",
                ).listScheduledSends(ListParams(page = 2, perPage = 1, search = "unused"), " doc ")
            assertCall(mock, "GET", "/accounts/account/scheduled-sends")
            assertThat(mock.lastCall().queryParams).containsExactlyInAnyOrderEntriesOf(
                mapOf(
                    "page" to 2,
                    "per-page" to 1,
                    "document_id" to "doc",
                ),
            )
            assertThat(result.meta!!.total).isEqualTo(3)
            assertThat(result.data.single().id).isEqualTo("scheduled")
        }

    @Test
    fun `cancel and send now retain the server state and failure without replay`() =
        runTest {
            val mock = MockApiHttpClient()
            val resource = AssignmentResource(mock, "account")
            mock.enqueue(response(scheduled.replace("pending", "cancelled")))
            assertThat(resource.cancelScheduledSend(" send/id ").status).isEqualTo(ScheduledSendStatus.CANCELLED)
            assertCall(mock, "PUT", "/accounts/account/scheduled-sends/send%2Fid/cancel")
            mock.enqueue(
                response(scheduled.replace("pending", "sent").replace("\"assignment_id\":null", "\"assignment_id\":\"assignment\"")),
            )
            assertThat(resource.sendScheduledNow("scheduled", "override").assignmentId).isEqualTo("assignment")
            assertCall(mock, "PUT", "/accounts/override/scheduled-sends/scheduled/send")
            mock.enqueue(HttpRawResponse(409, """{"status":409,"code":"CreditCeilingExceeded","message":"Price changed"}""", emptyMap()))
            assertThatThrownBy { runBlocking { resource.sendScheduledNow("scheduled") } }.isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.statusCode).isEqualTo(409)
                assertThat((it.responseData as Map<*, *>)["code"]).isEqualTo("CreditCeilingExceeded")
            }
            assertThat(mock.callCount()).isEqualTo(3)
        }

    @Test
    fun `invalid schedules and capacities fail before network calls`() {
        val mock = MockApiHttpClient()
        val resource = AssignmentResource(mock, "account")
        val invalid: List<suspend () -> Unit> =
            listOf(
                { resource.schedule("", assignment, sendAt) },
                { resource.schedule("doc", assignment, 0) },
                { resource.schedule("doc", assignment, Long.MAX_VALUE) },
                { resource.schedule("doc", assignment, System.currentTimeMillis() / 1_000 + 100) },
                { resource.schedule("doc", assignment.copy(signers = emptyList()), sendAt) },
                { resource.schedule("doc", assignment.copy(signers = listOf(SignerReference(signAs = SignerCapacity.SIGN))), sendAt) },
                { resource.schedule("doc", assignment.copy(signers = listOf(SignerReference("id", signAs = "witness"))), sendAt) },
                { resource.estimateCost("doc", assignment.copy(signers = listOf(SignerReference(signAs = "unknown")))) },
                { resource.scheduleEnvelope(envelope.copy(idempotencyKey = "unsupported"), sendAt) },
                { resource.scheduleEnvelope(envelope.copy(signers = assignment.signers), sendAt) },
                { resource.listScheduledSends(documentId = " ") },
                { resource.cancelScheduledSend(" ") },
                { resource.sendScheduledNow("scheduled", " ") },
            )
        invalid.forEach { call -> assertThatThrownBy { runBlocking { call() } }.isInstanceOf(ValidationException::class.java) }
        assertThat(mock.calls).isEmpty()
    }

    @Test
    fun `signer capacities are sent by create and estimates and read separately`() =
        runTest {
            val mock = MockApiHttpClient(response("""{"id":"assignment"}"""))
            val resource = AssignmentResource(mock)
            resource.create("doc", assignment)
            assertCall(
                mock,
                "POST",
                "/documents/doc/assignments",
                """{"method":"virtual","signers":[{"id":"signer","sign_as":"Witness"}],"message":"Invitation","max_total_credits":0.50}""",
            )
            mock.enqueue(response("{}"))
            resource.estimateCost("doc", assignment)
            assertCall(
                mock,
                "POST",
                "/documents/doc/assignments/estimate-cost",
                """{"method":"virtual","signers":[{"sign_as":"Witness"}]}""",
            )
            mock.enqueue(response("""[{"signer_id":"signer","sign_as":"Witness"}]"""))
            assertThat(resource.getSignerCapacities("doc/id").single().signAs).isEqualTo(SignerCapacity.WITNESS)
            assertCall(mock, "GET", "/documents/doc%2Fid/signer-capacities")
        }
}
