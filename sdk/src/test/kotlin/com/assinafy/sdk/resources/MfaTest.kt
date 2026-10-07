package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.MfaRequiredException
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.ConfirmTotpRequest
import com.assinafy.sdk.request.LoginRequest
import com.assinafy.sdk.request.MfaReauthRequest
import com.assinafy.sdk.request.MfaVerifyRequest
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MfaTest {

    private val sessionJson = """{"access_token":"jwt","user":{"id":"u1","name":"Example","email":"person@example.com",""" +
        """"is_email_verified":true,"has_accepted_terms":true,"created_at":"2026-01-01T00:00:00Z"},"accounts":[]}"""

    private fun ok(data: String) = HttpRawResponse(200, """{"status":200,"message":"","data":$data}""", emptyMap())

    private fun body(call: MockApiHttpClient.Call): Map<*, *> = Gson().fromJson(call.body, Map::class.java)

    private fun resource(auth: MockApiHttpClient, public: MockApiHttpClient = auth) =
        AuthenticationResource(auth, publicHttp = public)

    @Test
    fun `login without a second factor returns the session`() = runTest {
        val public = MockApiHttpClient().apply { enqueue(ok(sessionJson)) }

        val session = resource(MockApiHttpClient(), public).login(LoginRequest("person@example.com", "pw"))

        assertThat(session.accessToken).isEqualTo("jwt")
        assertThat(session.user.id).isEqualTo("u1")
    }

    @Test
    fun `login with a second factor throws MfaRequiredException carrying the challenge`() {
        val public = MockApiHttpClient().apply { enqueue(ok("""{"mfa_token":"challenge"}""")) }

        assertThatThrownBy { runBlocking { resource(MockApiHttpClient(), public).login(LoginRequest("person@example.com", "pw")) } }
            .isInstanceOfSatisfying(MfaRequiredException::class.java) {
                assertThat(it.mfaToken).isEqualTo("challenge")
                assertThat(it.toString()).doesNotContain("challenge")
            }
    }

    @Test
    fun `verifyMfa posts on the public transport`() = runTest {
        val auth = MockApiHttpClient()
        val public = MockApiHttpClient().apply { enqueue(ok(sessionJson)) }

        val session = resource(auth, public).verifyMfa(MfaVerifyRequest("challenge", " 123456 "))

        assertThat(auth.callCount()).isZero
        assertThat(public.lastCall().method).isEqualTo("POST")
        assertThat(public.lastCall().path).isEqualTo("/authentication/mfa/verify")
        assertThat(body(public.lastCall())).isEqualTo(mapOf("mfa_token" to "challenge", "code" to "123456"))
        assertThat(session.accessToken).isEqualTo("jwt")
        assertThat(MfaVerifyRequest("challenge", "123456").toString()).doesNotContain("challenge").doesNotContain("123456")
    }

    @Test
    fun `listMfaMethods parses methods and remaining codes`() = runTest {
        val mock = MockApiHttpClient().apply {
            enqueue(ok("""{"methods":[{"id":"m1","type":"totp","label":"Phone","confirmed_at":"2026-01-01T00:00:00Z"}],"recovery_codes_remaining":9}"""))
        }

        val status = resource(mock).listMfaMethods()

        assertThat(mock.lastCall().method).isEqualTo("GET")
        assertThat(mock.lastCall().path).isEqualTo("/users/self/mfa")
        assertThat(status.methods.single().type).isEqualTo("totp")
        assertThat(status.recoveryCodesRemaining).isEqualTo(9)
    }

    @Test
    fun `enrollment start and confirm send their bodies and redact secrets`() = runTest {
        val mock = MockApiHttpClient().apply {
            enqueue(ok("""{"id":"m1","secret":"BASE32SECRET","provisioning_uri":"otpauth://totp/x?secret=BASE32SECRET"}"""))
            enqueue(ok("""{"recovery_codes":["ABCD-EFGH-JKMN"]}"""))
        }
        val auth = resource(mock)

        val enrollment = auth.startTotpEnrollment("Phone")
        assertThat(mock.lastCall().path).isEqualTo("/users/self/mfa/totp")
        assertThat(body(mock.lastCall())).isEqualTo(mapOf("label" to "Phone"))
        assertThat(enrollment.toString()).doesNotContain("BASE32SECRET")

        val codes = auth.confirmTotpEnrollment(ConfirmTotpRequest(enrollment.id, "123456"))
        assertThat(mock.lastCall().method).isEqualTo("PUT")
        assertThat(mock.lastCall().path).isEqualTo("/users/self/mfa/totp/confirm")
        assertThat(body(mock.lastCall())).isEqualTo(mapOf("id" to "m1", "code" to "123456"))
        assertThat(codes.recoveryCodes).containsExactly("ABCD-EFGH-JKMN")
        assertThat(codes.toString()).doesNotContain("ABCD")
    }

    @Test
    fun `startTotpEnrollment without a label sends an empty object`() = runTest {
        val mock = MockApiHttpClient().apply { enqueue(ok("""{"id":"m1","secret":"s","provisioning_uri":"u"}""")) }

        resource(mock).startTotpEnrollment()

        assertThat(mock.lastCall().body).isEqualTo("{}")
    }

    @Test
    fun `recovery codes and removal require re-authentication`() = runTest {
        val mock = MockApiHttpClient().apply {
            enqueue(ok("""{"recovery_codes":["A","B"]}"""))
            enqueue(ok("""{"is_mfa_enabled":false}"""))
        }
        val auth = resource(mock)

        auth.regenerateRecoveryCodes(MfaReauthRequest(password = "pw"))
        assertThat(mock.lastCall().method).isEqualTo("POST")
        assertThat(mock.lastCall().path).isEqualTo("/users/self/mfa/recovery-codes")
        assertThat(body(mock.lastCall())).isEqualTo(mapOf("password" to "pw"))

        val removal = auth.removeMfaMethod("m1", MfaReauthRequest(code = "123456"))
        assertThat(mock.lastCall().method).isEqualTo("DELETE")
        assertThat(mock.lastCall().path).isEqualTo("/users/self/mfa/m1")
        assertThat(body(mock.lastCall())).isEqualTo(mapOf("code" to "123456"))
        assertThat(removal.isMfaEnabled).isFalse

        assertThatThrownBy { runBlocking { auth.removeMfaMethod("m1", MfaReauthRequest()) } }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { auth.verifyMfa(MfaVerifyRequest("", "1")) } }
            .isInstanceOf(ValidationException::class.java)
        assertThat(mock.callCount()).isEqualTo(2)
    }

    @Test
    fun `secret-bearing MFA requests redact diagnostics`() {
        val values = listOf(
            ConfirmTotpRequest("m1", "111111", password = "pw-secret", reauthCode = "222222"),
            MfaReauthRequest(password = "pw-secret", code = "222222"),
        ).map(Any::toString)

        values.forEach { assertThat(it).doesNotContain("111111").doesNotContain("222222").doesNotContain("pw-secret") }
    }
}
