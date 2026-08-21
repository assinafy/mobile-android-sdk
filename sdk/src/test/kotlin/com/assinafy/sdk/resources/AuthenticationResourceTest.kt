package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.request.ChangePasswordRequest
import com.assinafy.sdk.request.CreateApiKeyRequest
import com.assinafy.sdk.request.LinkSocialLoginRequest
import com.assinafy.sdk.request.LoginRequest
import com.assinafy.sdk.request.RequestPasswordResetRequest
import com.assinafy.sdk.request.ResetPasswordRequest
import com.assinafy.sdk.request.SocialLoginProvider
import com.assinafy.sdk.request.SocialLoginRequest
import com.assinafy.sdk.request.VerifySignerEmailRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AuthenticationResourceTest {

    @Test
    fun `login uses the public transport and parses the complete session`() = runTest {
        val authenticated = MockApiHttpClient()
        val public = MockApiHttpClient()
        public.enqueue(ok(LOGIN_DATA))

        val session = AuthenticationResource(authenticated, publicHttp = public).login(
            LoginRequest(email = "user@example.com", password = "secret"),
        )

        assertThat(authenticated.callCount()).isZero()
        assertThat(public.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                method = "POST",
                path = "/login",
                body = """{"email":"user@example.com","password":"secret"}""",
            ),
        )
        assertThat(session.accessToken).isEqualTo("jwt-token")
        assertThat(session.toString()).doesNotContain("jwt-token")
        assertThat(session.user.email).isEqualTo("user@example.com")
        assertThat(session.accounts.single().roles).containsExactly("Owner")
    }

    @Test
    fun `requestPasswordReset puts the exact public payload and parses email`() = runTest {
        val authenticated = MockApiHttpClient()
        val public = MockApiHttpClient()
        public.enqueue(ok("""{"email":"user@example.com"}"""))

        val result = AuthenticationResource(authenticated, publicHttp = public).requestPasswordReset(
            RequestPasswordResetRequest("user@example.com"),
        )

        assertThat(authenticated.callCount()).isZero()
        assertThat(public.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "PUT",
                "/authentication/request-password-reset",
                """{"email":"user@example.com"}""",
            ),
        )
        assertThat(result.email).isEqualTo("user@example.com")
    }

    @Test
    fun `resetPassword puts token and new password through the public transport`() = runTest {
        val authenticated = MockApiHttpClient()
        val public = MockApiHttpClient()
        public.enqueue(ok("""{"email":"user@example.com"}"""))

        val result = AuthenticationResource(authenticated, publicHttp = public).resetPassword(
            ResetPasswordRequest(
                email = "user@example.com",
                newPassword = "new-secret",
                token = "reset-token",
            ),
        )

        assertThat(authenticated.callCount()).isZero()
        assertThat(public.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "PUT",
                "/authentication/reset-password",
                """{"email":"user@example.com","new_password":"new-secret","token":"reset-token"}""",
            ),
        )
        assertThat(result.email).isEqualTo("user@example.com")
    }

    @Test
    fun `changePassword puts the exact authenticated payload and parses email`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok("""{"email":"user@example.com"}"""))

        val result = AuthenticationResource(mock).changePassword(
            ChangePasswordRequest(
                email = "user@example.com",
                password = "old-secret",
                newPassword = "new-secret",
            ),
        )

        assertThat(mock.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "PUT",
                "/authentication/change-password",
                """{"email":"user@example.com","password":"old-secret","new_password":"new-secret"}""",
            ),
        )
        assertThat(result.email).isEqualTo("user@example.com")
    }

    @Test
    fun `socialLogin posts the complete public provider payload and parses session`() = runTest {
        val authenticated = MockApiHttpClient()
        val public = MockApiHttpClient()
        public.enqueue(ok(LOGIN_DATA))

        val session = AuthenticationResource(authenticated, publicHttp = public).socialLogin(
            SocialLoginRequest(
                provider = SocialLoginProvider.GOOGLE,
                token = "provider-token",
                hasAcceptedTerms = true,
            ),
        )

        assertThat(authenticated.callCount()).isZero()
        assertThat(public.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "POST",
                "/authentication/social-login",
                """{"provider":"google","token":"provider-token","has_accepted_terms":true}""",
            ),
        )
        assertThat(session.user.id).isEqualTo("user-1")
        assertThat(session.accounts.single().id).isEqualTo("account-1")
    }

    @Test
    fun `linkSocialLogin posts authenticated provider payload and returns void`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok("[]"))

        val result = AuthenticationResource(mock).linkSocialLogin(
            LinkSocialLoginRequest(SocialLoginProvider.GOOGLE, "provider-token"),
        )

        assertThat(mock.lastCall()).isEqualTo(
            MockApiHttpClient.Call(
                "POST",
                "/auth/link-social-login",
                """{"provider":"google","token":"provider-token"}""",
            ),
        )
        assertThat(result).isEqualTo(Unit)
    }

    @Test
    fun `getApiKey gets the exact path and parses a masked key`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok("""{"api_key":"********suffix"}"""))

        val result = AuthenticationResource(mock).getApiKey()

        assertThat(mock.lastCall()).isEqualTo(MockApiHttpClient.Call("GET", "/users/api-keys"))
        assertThat(result?.apiKey).isEqualTo("********suffix")
    }

    @Test
    fun `getApiKey normalizes a documented empty key payload`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok("null"))

        assertThat(AuthenticationResource(mock).getApiKey()).isNull()
        assertThat(mock.lastCall()).isEqualTo(MockApiHttpClient.Call("GET", "/users/api-keys"))
    }

    @Test
    fun `createApiKey posts the password and parses the one-time full key`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok("""{"api_key":"full-key"}"""))

        val result = AuthenticationResource(mock).createApiKey(CreateApiKeyRequest("secret"))

        assertThat(mock.lastCall()).isEqualTo(
            MockApiHttpClient.Call("POST", "/users/api-keys", """{"password":"secret"}"""),
        )
        assertThat(result.apiKey).isEqualTo("full-key")
        assertThat(result.toString()).doesNotContain("full-key")
    }

    @Test
    fun `deleteApiKey deletes the exact path without a body`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(ok("[]"))

        val result = AuthenticationResource(mock).deleteApiKey()

        assertThat(mock.lastCall()).isEqualTo(MockApiHttpClient.Call("DELETE", "/users/api-keys"))
        assertThat(result).isEqualTo(Unit)
    }

    @Test
    fun `authentication inputs are rejected before transport calls`() {
        val mock = MockApiHttpClient()
        val auth = AuthenticationResource(mock)

        assertThatThrownBy { runBlocking { auth.login(LoginRequest("not-an-email", "secret")) } }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy {
            runBlocking { auth.socialLogin(SocialLoginRequest(SocialLoginProvider.GOOGLE, "", true)) }
        }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { auth.createApiKey(CreateApiKeyRequest(" ")) } }
            .isInstanceOf(ValidationException::class.java)
        assertThat(mock.callCount()).isZero()
    }

    @Test
    fun `secret-bearing requests redact diagnostics`() {
        val values = listOf(
            LoginRequest("user@example.com", "login-secret"),
            ResetPasswordRequest("user@example.com", "new-secret", "reset-secret"),
            ChangePasswordRequest("user@example.com", "old-secret", "next-secret"),
            SocialLoginRequest(SocialLoginProvider.GOOGLE, "social-secret", true),
            LinkSocialLoginRequest(SocialLoginProvider.GOOGLE, "link-secret"),
            CreateApiKeyRequest("key-secret"),
            VerifySignerEmailRequest("otp-secret"),
        )

        assertThat(values.joinToString()).doesNotContain(
            "login-secret",
            "new-secret",
            "reset-secret",
            "old-secret",
            "next-secret",
            "social-secret",
            "link-secret",
            "key-secret",
            "otp-secret",
        )
    }

    private fun ok(data: String): HttpRawResponse = HttpRawResponse(
        200,
        """{"status":200,"message":"","data":$data}""",
        emptyMap(),
    )

    private companion object {
        val LOGIN_DATA = """
            {
              "access_token":"jwt-token",
              "user":{
                "id":"user-1","name":"Example User","email":"user@example.com",
                "telephone":null,"government_id":null,"is_email_verified":true,
                "has_accepted_terms":true,"created_at":"2026-01-01T00:00:00Z","to_be_deleted_at":null
              },
              "accounts":[{
                "id":"account-1","name":"Example","roles":["Owner"],
                "is_delete_allowed":true,"created_at":"2026-01-01T00:00:00Z"
              }]
            }
        """.trimIndent()
    }
}
