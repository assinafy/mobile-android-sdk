package com.assinafy.sdk.live

import com.assinafy.sdk.AssinafyClient
import com.assinafy.sdk.AssinafyClientConfig
import com.assinafy.sdk.exceptions.ApiException
import com.assinafy.sdk.oauth.OAuthConfig
import com.assinafy.sdk.oauth.OAuthException
import com.assinafy.sdk.oauth.OAuthScope
import com.assinafy.sdk.request.ListParams
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Browser-assisted, opt-in Public-client lifecycle; no token or verifier is written to disk. */
class LiveOAuthTest {
    @Test
    fun `public PKCE connection reads rotates and revokes`() = runBlocking<Unit> {
        val clientId = System.getenv("ASSINAFY_OAUTH_CLIENT_ID").orEmpty()
        val redirect = System.getenv("ASSINAFY_OAUTH_REDIRECT_URI").orEmpty()
        val directory = System.getenv("ASSINAFY_OAUTH_CALLBACK_DIR").orEmpty()
        assumeTrue(clientId.isNotBlank() && redirect.isNotBlank() && directory.isNotBlank(), "Set the OAuth live-test environment")
        val config = AssinafyClientConfig(
            oauth = OAuthConfig(
                clientId = clientId,
                redirectUri = redirect,
                scopes = listOf(OAuthScope.DOCUMENTS_READ, OAuthScope.ACCOUNT_READ, OAuthScope.OPENID, OAuthScope.OFFLINE_ACCESS),
            ),
        )
        val sdk = AssinafyClient.create(config)
        val attempt = sdk.oauth.authorizationRequest()
        val callback = File(directory, "callback.txt")
        check(!callback.exists()) { "Remove a previous callback before starting a new attempt" }
        File(directory, "authorization-url.txt").writeText(attempt.url)
        val deadline = System.nanoTime() + 600_000_000_000L
        while (!callback.exists() && System.nanoTime() < deadline) delay(250)
        check(callback.exists()) { "No browser callback received within ten minutes" }
        val callbackUri = redirect.substringBefore('?') + callback.readText().substringAfter("/callback")
        val code = sdk.oauth.parseCallback(callbackUri, attempt)
        var latest = sdk.oauth.exchangeCode(code, attempt.pkce.codeVerifier)
        try {
            assertThat(latest.hasScope(OAuthScope.DOCUMENTS_READ)).isTrue()
            assertThat(latest.hasScope(OAuthScope.ACCOUNT_READ)).isTrue()
            assertThat(latest.hasScope(OAuthScope.OPENID)).isTrue()
            assertThat(latest.hasScope(OAuthScope.OFFLINE_ACCESS)).isFalse()
            assertThat(!latest.refreshToken.isNullOrBlank()).isTrue()
            assertThat(!latest.idToken.isNullOrBlank()).isTrue()
            val workspace = AssinafyClient.create(config.copy(token = latest.accessToken))
            assertThat(workspace.oauth.userInfo().sub.isNotBlank()).isTrue()
            val accounts = workspace.workspaces.list()
            assertThat(accounts.data.size).isEqualTo(1)
            val accountId = accounts.data.single().id
            workspace.workspaces.get(accountId)
            workspace.documents.list(accountId = accountId, params = ListParams(perPage = 1))
            val missingScope = runCatching { workspace.templates.list(accountId = accountId) }.exceptionOrNull()
            assertThat(missingScope is ApiException && missingScope.statusCode == 403 && missingScope.challenge?.isInsufficientScope == true).isTrue()
            val previous = latest.refreshToken!!
            latest = sdk.oauth.refresh(previous)
            assertThat(latest.refreshToken != previous).isTrue()
            val renewed = AssinafyClient.create(config.copy(token = latest.accessToken))
            assertThat(renewed.oauth.userInfo().sub.isNotBlank()).isTrue()
            renewed.documents.list(accountId = accountId, params = ListParams(perPage = 1))
        } finally {
            latest.refreshToken?.let { sdk.oauth.revoke(it, "refresh_token") }
            sdk.oauth.revoke(latest.accessToken, "access_token")
        }
        val revoked = runCatching { sdk.oauth.refresh(latest.refreshToken!!) }.exceptionOrNull()
        assertThat(revoked is OAuthException && revoked.isInvalidGrant).isTrue()
    }
}
