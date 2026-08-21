package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.models.DocumentStatsGranularity
import com.assinafy.sdk.models.DocumentStatsQuery
import com.assinafy.sdk.request.UpdateNotificationPreferencesRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class UserResourceTest {

    @Test
    fun `getCurrent gets the exact path and parses the current contract`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok(USER_DATA))

        val result = UserResource(mock).getCurrent()

        assertThat(mock.lastCall()).isEqualTo(MockApiHttpClient.Call("GET", "/users/self"))
        assertThat(result.id).isEqualTo("user-1")
        assertThat(result.email).isEqualTo("user@example.com")
        assertThat(result.governmentId).isNull()
        assertThat(result.isEmailVerified).isTrue()
    }

    @Test
    fun `getCurrent normalizes the sandbox legacy user wrapper`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok("""{"user":$USER_DATA,"accounts":[]}"""))

        val result = UserResource(mock).getCurrent()

        assertThat(mock.lastCall()).isEqualTo(MockApiHttpClient.Call("GET", "/users/self"))
        assertThat(result.name).isEqualTo("Example User")
    }

    @Test
    fun `getStats gets the exact daily query and parses every current counter`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok("[$STATS_DATA]"))

        val result = UserResource(mock).getStats(
            DocumentStatsQuery(DocumentStatsGranularity.DAILY, "2026-06"),
        )

        assertThat(mock.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "GET",
                "/users/self/stats",
                queryParams = mapOf("granularity" to "daily", "month" to "2026-06"),
            ),
        )
        assertThat(result.single().period).isEqualTo("2026-06-01")
        assertThat(result.single().signatureRequestsNotificationEmail).isEqualTo(4L)
        assertThat(result.single().signatureRequestsVerificationDigitalCertificate).isEqualTo(1L)
        assertThat(result.single().documentsCertified).isEqualTo(2L)
    }

    @Test
    fun `getNotificationPreferences gets the exact path and parses all settings`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok(PREFERENCES_DATA))

        val result = UserResource(mock).getNotificationPreferences()

        assertThat(mock.lastCall()).isEqualTo(
            MockApiHttpClient.Call("GET", "/users/self/notification-preferences"),
        )
        assertThat(result.documentCompleted).isTrue()
        assertThat(result.signerDeclined).isFalse()
        assertThat(result.signerWhatsappFailed).isTrue()
    }

    @Test
    fun `updateNotificationPreferences puts only supplied keys and parses the full map`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok(PREFERENCES_DATA))

        val result = UserResource(mock).updateNotificationPreferences(
            UpdateNotificationPreferencesRequest(
                documentCompleted = false,
                signerDeclined = true,
            ),
        )

        assertThat(mock.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "PUT",
                "/users/self/notification-preferences",
                """{"DocumentCompleted":false,"SignerDeclined":true}""",
            ),
        )
        assertThat(result.documentCompleted).isTrue()
        assertThat(result.signerDeclined).isFalse()
        assertThat(result.documentExpired).isTrue()
    }

    @Test
    fun `invalid stats and empty preference updates are rejected before transport calls`() {
        val mock = MockApiHttpClient()
        val users = UserResource(mock)

        assertThatThrownBy {
            runBlocking { users.getStats(DocumentStatsQuery(DocumentStatsGranularity.DAILY)) }
        }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy {
            runBlocking { users.getStats(DocumentStatsQuery(month = "06-2026")) }
        }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy {
            runBlocking { users.updateNotificationPreferences(UpdateNotificationPreferencesRequest()) }
        }.isInstanceOf(ValidationException::class.java)
        assertThat(mock.callCount()).isZero()
    }

    private fun ok(data: String): HttpRawResponse = HttpRawResponse(
        200,
        """{"status":200,"message":"","data":$data}""",
        emptyMap(),
    )

    private companion object {
        val USER_DATA = """
            {
              "id":"user-1","name":"Example User","email":"user@example.com","telephone":null,
              "government_id":null,"is_email_verified":true,"has_accepted_terms":true,
              "created_at":"2026-01-01T00:00:00Z","to_be_deleted_at":null
            }
        """.trimIndent()

        val STATS_DATA = """
            {
              "period":"2026-06-01","documents_uploaded":3,"documents_sent":2,"signature_requests":5,
              "signature_requests_notification_email":4,"signature_requests_notification_whatsapp":2,
              "signature_requests_notification_bypass":1,"signature_requests_verification_email":2,
              "signature_requests_verification_whatsapp":1,"signature_requests_verification_bypass":1,
              "signature_requests_verification_digital_certificate":1,"signature_requests_viewed":4,
              "signature_requests_completed":3,"documents_certified":2
            }
        """.trimIndent()

        val PREFERENCES_DATA = """
            {
              "DocumentCompleted":true,"SignerDeclined":false,"DocumentCancelled":true,
              "DocumentAboutToExpire":true,"DocumentExpired":true,"DocumentExpirationReset":true,
              "DocumentProcessingFailed":true,"TemplateProcessingFailed":false,"SignerWhatsappFailed":true
            }
        """.trimIndent()
    }
}
