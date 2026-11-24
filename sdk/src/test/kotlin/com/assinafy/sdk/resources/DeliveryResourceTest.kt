package com.assinafy.sdk.resources

import com.assinafy.sdk.SignatureType
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DeliveryResourceTest {
    private fun response(data: String) = HttpRawResponse(200, """{"status":200,"data":$data}""", emptyMap())

    @Test
    fun `watchers can be read replaced and cleared with normalized distinct IDs`() =
        runTest {
            val mock = MockApiHttpClient(response("""[{"id":"watcher","full_name":"Example","email":"watcher@example.com"}]"""))
            val resource = DocumentResource(mock)
            assertThat(resource.listWatchers(" doc/id ").single().id).isEqualTo("watcher")
            assertThat(mock.lastCall().method).isEqualTo("GET")
            assertThat(mock.lastCall().path).isEqualTo("/documents/doc%2Fid/watchers")
            resource.setWatchers("doc", listOf(" watcher ", "watcher"))
            assertThat(mock.lastCall().method).isEqualTo("PUT")
            assertThat(mock.lastCall().path).isEqualTo("/documents/doc/watchers")
            assertThat(JsonParser.parseString(mock.lastCall().body)).isEqualTo(JsonParser.parseString("""{"watcher_ids":["watcher"]}"""))
            mock.enqueue(response("[]"))
            assertThat(resource.setWatchers("doc", emptyList())).isEmpty()
            assertThat(mock.lastCall().body).isEqualTo("""{"watcher_ids":[]}""")
        }

    @Test
    fun `watcher limits apply to distinct IDs and reject blanks before sending`() =
        runTest {
            val mock = MockApiHttpClient(response("[]"))
            val resource = DocumentResource(mock)
            resource.setWatchers("doc", List(11) { "same" })
            assertThat(mock.callCount()).isEqualTo(1)
            for (ids in listOf(listOf(" "), List(11) { "id-$it" })) {
                assertThatThrownBy { runBlocking { resource.setWatchers("doc", ids) } }.isInstanceOf(ValidationException::class.java)
            }
            assertThat(mock.callCount()).isEqualTo(1)
        }

    @Test
    fun `notification history retains delivery timestamps and missing opens`() =
        runTest {
            val mock =
                MockApiHttpClient(
                    response(
                        """[{"signer_id":"signer","notification_history":[{"event":"SignatureRequested","status":"Sent","error_code":null,"error_message":null,"sent_at":"2026-11-24T12:00:00Z","failed_at":null,"delivered_at":"2026-11-24T12:00:05Z","opened_at":null}]}]""",
                    ),
                )
            val history = AssignmentResource(mock).getNotificationHistory(" doc/id ").single()
            assertThat(mock.lastCall().method).isEqualTo("GET")
            assertThat(mock.lastCall().path).isEqualTo("/documents/doc%2Fid/notification-history")
            assertThat(history.signerId).isEqualTo("signer")
            assertThat(history.notificationHistory.single().deliveredAt).isEqualTo("2026-11-24T12:00:05Z")
            assertThat(history.notificationHistory.single().openedAt).isNull()
        }

    @Test
    fun `envelope reminder estimate uses the resend shape without sending a reminder`() =
        runTest {
            val mock =
                MockApiHttpClient(
                    response(
                        """{"total":0.45,"breakdown":[{"code":"whatsapp","name":"Whatsapp","cost":0.45}],"credit_balance":2.5,"has_sufficient_credits":true}""",
                    ),
                )
            val cost = EnvelopeResource(mock, "default").estimateResendCost(" env/id ", " signer ", "override")
            assertThat(mock.lastCall().method).isEqualTo("POST")
            assertThat(mock.lastCall().path).isEqualTo("/accounts/override/envelopes/env%2Fid/signers/signer/estimate-resend-cost")
            assertThat(mock.lastCall().body).isNull()
            assertThat(cost.legacyTotal).isEqualTo(0.45)
            assertThat(cost.legacyHasSufficientCredits).isTrue()
        }

    @Test
    fun `signature upload supports JPEG and retains the PNG entry point`() =
        runTest {
            val mock = MockApiHttpClient(response("[]"))
            val resource = SignerDocumentResource(mock)
            resource.uploadSignature(
                " code+/= ",
                byteArrayOf(1, 2),
                type = SignatureType.INITIAL,
                reuse = false,
                contentType = "image/jpeg",
            )
            assertThat(mock.lastCall().method).isEqualTo("POST_SIGNATURE")
            assertThat(mock.lastCall().path).isEqualTo("/signature?signer-access-code=code%2B%2F%3D&type=initial&reuse=false")
            assertThat(mock.lastCall().body).isEqualTo("image/jpeg")
            resource.uploadSignature("code", byteArrayOf(1))
            assertThat(mock.lastCall().body).isEqualTo("image/png")
            assertThat(mock.lastCall().path).isEqualTo("/signature?signer-access-code=code&type=signature")
            assertThatThrownBy {
                runBlocking {
                    resource.uploadSignature("code", byteArrayOf(1), contentType = "image/svg+xml")
                }
            }.isInstanceOf(ValidationException::class.java)
            assertThatThrownBy {
                runBlocking { resource.uploadSignature("code", byteArrayOf(1), type = "unknown") }
            }.isInstanceOf(ValidationException::class.java)
            assertThat(mock.callCount()).isEqualTo(2)
        }
}
