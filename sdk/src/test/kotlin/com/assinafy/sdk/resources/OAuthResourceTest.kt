package com.assinafy.sdk.resources

import com.assinafy.sdk.exceptions.NetworkException
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.helper.MockApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.oauth.OAuthChallenge
import com.assinafy.sdk.oauth.OAuthConfig
import com.assinafy.sdk.oauth.OAuthException
import com.assinafy.sdk.oauth.OAuthScope
import com.assinafy.sdk.oauth.PkcePair
import com.assinafy.sdk.oauth.base64UrlNoPadding
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.URI
import java.security.MessageDigest

class OAuthResourceTest {

    private val gson = Gson()

    private val config = OAuthConfig(
        clientId = "client-123",
        redirectUri = "https://myapp.example/oauth/callback",
        scopes = listOf(OAuthScope.DOCUMENTS_READ, OAuthScope.WEBHOOKS_WRITE, OAuthScope.OFFLINE_ACCESS),
    )

    private fun resource(
        mock: MockApiHttpClient = MockApiHttpClient(),
        oauthConfig: OAuthConfig? = config,
        baseUrl: String = "https://api.assinafy.com.br/v1",
    ) = OAuthResource(mock, mock, oauthConfig, baseUrl)

    private fun query(url: String): Map<String, String> =
        URI(url).rawQuery.split('&').associate {
            java.net.URLDecoder.decode(it.substringBefore('='), "UTF-8") to
                java.net.URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }

    @Suppress("UNCHECKED_CAST")
    private fun body(raw: String?): Map<String, Any?> = gson.fromJson(raw, Map::class.java) as Map<String, Any?>

    // ---- PKCE -------------------------------------------------------------------------------

    @Test
    fun `pkce challenge matches the RFC 7636 appendix B test vector`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))

        assertThat(base64UrlNoPadding(digest)).isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
    }

    @Test
    fun `base64url encoding handles every remainder length without padding`() {
        assertThat(base64UrlNoPadding(byteArrayOf(0xFB.toByte()))).isEqualTo("-w")
        assertThat(base64UrlNoPadding(byteArrayOf(0xFB.toByte(), 0xF0.toByte()))).isEqualTo("-_A")
        assertThat(base64UrlNoPadding(byteArrayOf(0xFB.toByte(), 0xF0.toByte(), 0x00))).isEqualTo("-_AA")
        assertThat(base64UrlNoPadding(ByteArray(0))).isEmpty()
    }

    @Test
    fun `generated pkce pairs are in grammar and self-consistent`() {
        val pair = PkcePair.generate()

        assertThat(pair.codeVerifier).hasSize(64).matches("[A-Za-z0-9\\-._~]+")
        assertThat(pair.codeChallengeMethod).isEqualTo("S256")
        val expected = MessageDigest.getInstance("SHA-256").digest(pair.codeVerifier.toByteArray(Charsets.US_ASCII))
        assertThat(pair.codeChallenge).isEqualTo(base64UrlNoPadding(expected))
        assertThat(pair.codeChallenge).doesNotContain("=").doesNotContain("+").doesNotContain("/")
        assertThat(PkcePair.generate().codeVerifier).isNotEqualTo(pair.codeVerifier)
    }

    @Test
    fun `pkce rejects verifier lengths outside the RFC range`() {
        assertThatThrownBy { PkcePair.generate(42) }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { PkcePair.generate(129) }.isInstanceOf(ValidationException::class.java)
        assertThat(PkcePair.generate(43).codeVerifier).hasSize(43)
        assertThat(PkcePair.generate(128).codeVerifier).hasSize(128)
    }

    @Test
    fun `pkce and tokens redact secrets in toString`() {
        assertThat(PkcePair.generate().toString()).contains("codeVerifier=***")
    }

    // ---- Authorization request --------------------------------------------------------------

    @Test
    fun `authorization request carries every required PKCE and OAuth parameter`() {
        val request = resource().authorizationRequest()

        assertThat(request.url).startsWith("https://auth.assinafy.com.br/oauth/authorize?")
        val params = query(request.url)
        assertThat(params["response_type"]).isEqualTo("code")
        assertThat(params["client_id"]).isEqualTo("client-123")
        assertThat(params["redirect_uri"]).isEqualTo("https://myapp.example/oauth/callback")
        assertThat(params["scope"]).isEqualTo("documents:read webhooks:write offline_access")
        assertThat(params["code_challenge_method"]).isEqualTo("S256")
        assertThat(params["code_challenge"]).isEqualTo(request.pkce.codeChallenge)
        assertThat(params["state"]).isEqualTo(request.state)
        assertThat(params["resource"]).isEqualTo("https://api.assinafy.com.br")
        assertThat(params).doesNotContainKey("nonce")
        // The verifier is the client's secret and must never reach the authorization server.
        assertThat(request.url).doesNotContain(request.pkce.codeVerifier)
    }

    @Test
    fun `authorization request encodes reserved characters and accepts an openid nonce`() {
        val request = resource().authorizationRequest(scopes = listOf(OAuthScope.OPENID), nonce = "n-once")

        assertThat(request.url).contains("redirect_uri=https%3A%2F%2Fmyapp.example%2Foauth%2Fcallback")
        assertThat(request.url).doesNotContain(" ")
        assertThat(query(request.url)["nonce"]).isEqualTo("n-once")
        assertThat(query(request.url)["scope"]).isEqualTo("openid")
    }

    @Test
    fun `authorization request derives the resource indicator from the base url origin`() {
        val request = resource(baseUrl = "https://api.example.test:8443/v1").authorizationRequest()

        assertThat(query(request.url)["resource"]).isEqualTo("https://api.example.test:8443")
    }

    @Test
    fun `each authorization request uses a fresh state and verifier`() {
        val first = resource().authorizationRequest()
        val second = resource().authorizationRequest()

        assertThat(first.state).isNotEqualTo(second.state)
        assertThat(first.pkce.codeVerifier).isNotEqualTo(second.pkce.codeVerifier)
    }

    @Test
    fun `authorization request requires a configured application and a scope`() {
        assertThatThrownBy { resource(oauthConfig = null).authorizationRequest() }
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("OAuth is not configured")
        assertThatThrownBy { resource().authorizationRequest(scopes = emptyList()) }
            .isInstanceOf(ValidationException::class.java)
    }

    // ---- Callback ---------------------------------------------------------------------------

    @Test
    fun `parse callback returns the code when state and issuer match`() {
        val request = resource().authorizationRequest()
        val callback = "https://myapp.example/oauth/callback" +
            "?code=the-code&state=${request.state}&iss=https%3A%2F%2Fauth.assinafy.com.br"

        assertThat(resource().parseCallback(callback, request)).isEqualTo("the-code")
    }

    @Test
    fun `parse callback rejects a mismatched state before reading anything else`() {
        val request = resource().authorizationRequest()

        assertThatThrownBy {
            resource().parseCallback("https://myapp.example/oauth/callback?code=c&state=forged", request)
        }.isInstanceOf(ValidationException::class.java).hasMessageContaining("state")
    }

    @Test
    fun `parse callback rejects a foreign issuer`() {
        val request = resource().authorizationRequest()
        val callback = "https://myapp.example/oauth/callback" +
            "?code=c&state=${request.state}&iss=https%3A%2F%2Fevil.example"

        assertThatThrownBy { resource().parseCallback(callback, request) }
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("issuer")
    }

    @Test
    fun `parse callback surfaces a declined approval as access_denied`() {
        val request = resource().authorizationRequest()
        val callback = "https://myapp.example/oauth/callback" +
            "?error=access_denied&error_description=User+declined&state=${request.state}"

        val thrown = runCatching { resource().parseCallback(callback, request) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(OAuthException::class.java)
        assertThat((thrown as OAuthException).isAccessDenied).isTrue
        assertThat(thrown.errorDescription).isEqualTo("User declined")
    }

    @Test
    fun `parse callback rejects a response carrying neither code nor error`() {
        val request = resource().authorizationRequest()

        assertThatThrownBy {
            resource().parseCallback("https://myapp.example/oauth/callback?state=${request.state}", request)
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `parse callback accepts a stored state without the original request`() {
        val callback = "https://myapp.example/oauth/callback?code=abc&state=stored-state"

        assertThat(resource().parseCallback(callback, "stored-state")).isEqualTo("abc")
    }

    // ---- Token exchange ---------------------------------------------------------------------

    private val tokenResponse = HttpRawResponse(
        200,
        """
        {"access_token":"at-1","token_type":"Bearer","expires_in":3600,
         "refresh_token":"rt-1","scope":"documents:read documents:write","id_token":"id-1"}
        """.trimIndent(),
        emptyMap(),
    )

    @Test
    fun `exchange code posts the authorization_code grant with PKCE and no client secret`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(tokenResponse)

        val tokens = resource(mock).exchangeCode("the-code", "the-verifier")

        val call = mock.lastCall()
        assertThat(call.method).isEqualTo("POST")
        assertThat(call.path).isEqualTo("/oauth/token")
        val sent = body(call.body)
        assertThat(sent["grant_type"]).isEqualTo("authorization_code")
        assertThat(sent["code"]).isEqualTo("the-code")
        assertThat(sent["code_verifier"]).isEqualTo("the-verifier")
        assertThat(sent["client_id"]).isEqualTo("client-123")
        assertThat(sent["redirect_uri"]).isEqualTo("https://myapp.example/oauth/callback")
        assertThat(sent["resource"]).isEqualTo("https://api.assinafy.com.br")
        assertThat(sent).doesNotContainKey("client_secret")

        assertThat(tokens.accessToken).isEqualTo("at-1")
        assertThat(tokens.refreshToken).isEqualTo("rt-1")
        assertThat(tokens.idToken).isEqualTo("id-1")
        assertThat(tokens.expiresIn).isEqualTo(3_600)
        assertThat(tokens.scopes).containsExactly("documents:read", "documents:write")
        assertThat(tokens.hasScope(OAuthScope.DOCUMENTS_WRITE)).isTrue
        assertThat(tokens.hasScope(OAuthScope.TEMPLATES_WRITE)).isFalse
    }

    @Test
    fun `exchange code sends client_secret only for a confidential client`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(tokenResponse)

        resource(mock, config.copy(clientSecret = "sh-1")).exchangeCode("c", "v")

        assertThat(body(mock.lastCall().body)["client_secret"]).isEqualTo("sh-1")
    }

    @Test
    fun `tokens redact every secret in toString`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(tokenResponse)

        val rendered = resource(mock).exchangeCode("c", "v").toString()

        assertThat(rendered).contains("accessToken=***").contains("refreshToken=***").contains("idToken=***")
        assertThat(rendered).doesNotContain("at-1").doesNotContain("rt-1").doesNotContain("id-1")
        assertThat(rendered).contains("scope=documents:read documents:write")
    }

    @Test
    fun `refresh posts the refresh_token grant without a redirect uri`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(tokenResponse)

        resource(mock).refresh("rt-0")

        val sent = body(mock.lastCall().body)
        assertThat(mock.lastCall().path).isEqualTo("/oauth/token")
        assertThat(sent["grant_type"]).isEqualTo("refresh_token")
        assertThat(sent["refresh_token"]).isEqualTo("rt-0")
        assertThat(sent["client_id"]).isEqualTo("client-123")
        assertThat(sent).doesNotContainKey("redirect_uri")
    }

    @Test
    fun `token errors surface the flat OAuth error body rather than the API envelope`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            HttpRawResponse(
                400,
                """{"error":"invalid_grant","error_description":"Authorization code has expired."}""",
                emptyMap(),
            ),
        )

        val thrown = runCatching { resource(mock).exchangeCode("stale", "v") }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(OAuthException::class.java)
        val oauth = thrown as OAuthException
        assertThat(oauth.error).isEqualTo("invalid_grant")
        assertThat(oauth.isInvalidGrant).isTrue
        assertThat(oauth.errorDescription).isEqualTo("Authorization code has expired.")
        assertThat(oauth.statusCode).isEqualTo(400)
        assertThat(oauth.message).isEqualTo("invalid_grant: Authorization code has expired.")
    }

    @Test
    fun `a 401 without a parseable body still reports invalid_client`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(HttpRawResponse(401, "", emptyMap()))

        val thrown = runCatching { resource(mock).refresh("rt") }.exceptionOrNull()

        assertThat((thrown as OAuthException).error).isEqualTo(OAuthException.INVALID_CLIENT)
    }

    @Test
    fun `exchange and refresh validate their arguments and the configuration`() {
        assertThatThrownBy { runBlocking { resource().exchangeCode("", "v") } }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { resource().exchangeCode("c", " ") } }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { resource().refresh("") } }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { runBlocking { resource(oauthConfig = null).refresh("rt") } }
            .isInstanceOf(ValidationException::class.java)
    }

    // ---- Revocation, userinfo, discovery ----------------------------------------------------

    @Test
    fun `revoke posts the token with its hint and treats an empty 200 as success`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(HttpRawResponse(200, null, emptyMap()))

        resource(mock).revoke("rt-1", tokenTypeHint = "refresh_token")

        val sent = body(mock.lastCall().body)
        assertThat(mock.lastCall().path).isEqualTo("/oauth/revoke")
        assertThat(sent["token"]).isEqualTo("rt-1")
        assertThat(sent["token_type_hint"]).isEqualTo("refresh_token")
        assertThat(sent["client_id"]).isEqualTo("client-123")
    }

    @Test
    fun `userinfo parses the flat OIDC claims object`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            HttpRawResponse(
                200,
                """{"sub":"d6zqpbyog2v3xvxerwn8la94","name":"Maria Silva","email":"maria@example.test","email_verified":true}""",
                emptyMap(),
            ),
        )

        val info = resource(mock).userInfo()

        assertThat(mock.lastCall().path).isEqualTo("/oauth/userinfo")
        assertThat(info.sub).isEqualTo("d6zqpbyog2v3xvxerwn8la94")
        assertThat(info.name).isEqualTo("Maria Silva")
        assertThat(info.emailVerified).isTrue
    }

    @Test
    fun `an insufficient_scope challenge names the scope the caller must request`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            HttpRawResponse(
                403,
                null,
                mapOf(
                    "www-authenticate" to
                        """Bearer error="insufficient_scope", scope="documents:write", """ +
                        """resource_metadata="https://api.assinafy.com.br/.well-known/oauth-protected-resource"""",
                ),
            ),
        )

        val thrown = runCatching { resource(mock).userInfo() }.exceptionOrNull()

        assertThat((thrown as OAuthException).error).isEqualTo(OAuthChallenge.INSUFFICIENT_SCOPE)
        assertThat(thrown.errorDescription).contains("documents:write")
    }

    @Test
    fun `challenge parsing extracts every parameter and ignores non-bearer headers`() {
        val challenge = OAuthChallenge.parse(
            """Bearer error="insufficient_scope", error_description="Missing permission", """ +
                """scope="templates:write", resource_metadata="https://api.assinafy.com.br/.well-known/x"""",
        )

        assertThat(challenge).isNotNull
        assertThat(challenge!!.isInsufficientScope).isTrue
        assertThat(challenge.scope).isEqualTo("templates:write")
        assertThat(challenge.errorDescription).isEqualTo("Missing permission")
        assertThat(challenge.resourceMetadata).isEqualTo("https://api.assinafy.com.br/.well-known/x")

        assertThat(OAuthChallenge.parse(null)).isNull()
        assertThat(OAuthChallenge.parse("Basic realm=\"x\"")).isNull()
    }

    @Test
    fun `protected resource metadata is fetched from the api host root not the v1 prefix`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            HttpRawResponse(
                200,
                """
                {"resource":"https://api.assinafy.com.br",
                 "authorization_servers":["https://auth.assinafy.com.br"],
                 "scopes_supported":["documents:read"],"bearer_methods_supported":["header"]}
                """.trimIndent(),
                emptyMap(),
            ),
        )

        val metadata = resource(mock).protectedResourceMetadata()

        assertThat(mock.lastCall().method).isEqualTo("GET_ABSOLUTE")
        assertThat(mock.lastCall().path)
            .isEqualTo("https://api.assinafy.com.br/.well-known/oauth-protected-resource")
        assertThat(metadata.authorizationServers).containsExactly("https://auth.assinafy.com.br")
        assertThat(metadata.authorizationServer).isEqualTo("https://auth.assinafy.com.br")
        assertThat(metadata.bearerMethodsSupported).containsExactly("header")
    }

    @Test
    fun `authorization server metadata is fetched from the issuer, never from this api`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(
            HttpRawResponse(
                200,
                """
                {"issuer":"https://auth.assinafy.com.br",
                 "authorization_endpoint":"https://auth.assinafy.com.br/oauth/authorize",
                 "token_endpoint":"https://api.assinafy.com.br/v1/oauth/token",
                 "code_challenge_methods_supported":["S256"]}
                """.trimIndent(),
                emptyMap(),
            ),
        )

        val metadata = resource(mock).authorizationServerMetadata()

        assertThat(mock.lastCall().path)
            .isEqualTo("https://auth.assinafy.com.br/.well-known/oauth-authorization-server")
        assertThat(metadata.issuer).isEqualTo("https://auth.assinafy.com.br")
        assertThat(metadata.codeChallengeMethodsSupported).containsExactly("S256")
    }

    @Test
    fun `discovery works without a registered application`() = runTest {
        val mock = MockApiHttpClient()
        mock.enqueue(HttpRawResponse(200, """{"resource":"https://api.assinafy.com.br"}""", emptyMap()))

        val metadata = resource(mock, oauthConfig = null).protectedResourceMetadata()

        assertThat(metadata.resource).isEqualTo("https://api.assinafy.com.br")
        // RFC 9728 makes every other member optional, and Gson bypasses Kotlin constructor
        // defaults, so a minimal document must not blow up on the absent fields.
        assertThat(metadata.authorizationServers).isNull()
        assertThat(metadata.authorizationServer).isNull()
    }

    @Test
    fun `transport failures surface as NetworkException, not a raw IOException`() {
        val mock = MockApiHttpClient()
        mock.transportError = java.io.IOException("connection reset")

        // Every OAuth entry point must normalize into the SDK's exception hierarchy.
        assertThatThrownBy { runBlocking { resource(mock).exchangeCode("c", "v") } }
            .isInstanceOf(NetworkException::class.java)
        assertThatThrownBy { runBlocking { resource(mock).refresh("rt") } }
            .isInstanceOf(NetworkException::class.java)
        assertThatThrownBy { runBlocking { resource(mock).revoke("t") } }
            .isInstanceOf(NetworkException::class.java)
        assertThatThrownBy { runBlocking { resource(mock).userInfo() } }
            .isInstanceOf(NetworkException::class.java)
        assertThatThrownBy { runBlocking { resource(mock).protectedResourceMetadata() } }
            .isInstanceOf(NetworkException::class.java)
    }

    @Test
    fun `oauth config redacts the client secret in toString`() {
        assertThat(config.copy(clientSecret = "sh-1").toString())
            .contains("clientSecret=***")
            .doesNotContain("sh-1")
    }
}
