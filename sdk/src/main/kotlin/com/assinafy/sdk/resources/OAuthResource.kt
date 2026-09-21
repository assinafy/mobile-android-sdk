package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.SdkConstants
import com.assinafy.sdk.exceptions.AssinafyException
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.oauth.AuthorizationRequest
import com.assinafy.sdk.oauth.AuthorizationServerMetadata
import com.assinafy.sdk.oauth.OAuthChallenge
import com.assinafy.sdk.oauth.OAuthConfig
import com.assinafy.sdk.oauth.OAuthException
import com.assinafy.sdk.oauth.OAuthTokens
import com.assinafy.sdk.oauth.PkcePair
import com.assinafy.sdk.oauth.ProtectedResourceMetadata
import com.assinafy.sdk.oauth.UserInfo
import com.assinafy.sdk.util.ResponseHandler
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

/**
 * OAuth 2.1 authorization-code flow with mandatory PKCE, for applications that act **in someone
 * else's workspace with that person's permission**. Automating your own workspace needs an API key,
 * not this flow.
 *
 * The flow spans two hosts on purpose: the approval page lives on the authorization server
 * (`https://auth.assinafy.com.br`) and every endpoint your code calls lives on this API. A token
 * belongs to exactly one workspace — the one the user picked — and carries only the scopes they
 * approved.
 *
 * ### Android applications are public clients
 * An app distributed through a store cannot keep a secret, so it is registered as `Public`, leaves
 * [OAuthConfig.clientSecret] `null`, and authenticates with PKCE alone. Never embed a client secret
 * in an APK.
 *
 * ### The flow
 * ```kotlin
 * // 1. Prepare an attempt and open it in a Custom Tab.
 * val attempt = client.oauth.authorizationRequest()
 * session.save(attempt.state, attempt.pkce.codeVerifier)
 * CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(attempt.url))
 *
 * // 2. On your redirect URI, validate and exchange. parseCallback checks state and iss for you.
 * val code = client.oauth.parseCallback(callbackUri, attempt)
 * val tokens = client.oauth.exchangeCode(code, attempt.pkce.codeVerifier)
 *
 * // 3. Build a workspace client from the access token and find the one workspace it covers.
 * val user = AssinafyClient.create(AssinafyClientConfig(token = tokens.accessToken))
 * val workspaceId = user.workspaces.list().data.first().id
 * ```
 *
 * ### Environments
 * Production exposes the OAuth surface. The resource indicator and the
 * protected-resource metadata URL are derived from the client's base URL, so pointing a client at
 * `https://api.assinafy.com.br/v1` targets the flow with no other change. Register a
 * redirect URI per environment — they are matched character for character.
 *
 * @param publicHttp Credential-free transport used for the token and revocation endpoints, which
 *   authenticate the client through `client_id`/PKCE rather than an API key.
 * @param http Authenticated transport used for `GET /oauth/userinfo`, which needs the bearer token.
 * @param config Registered application; `null` until one is supplied through
 *   [com.assinafy.sdk.AssinafyClientConfig.oauth].
 * @param baseUrl API prefix, used to derive the resource indicator and the protected-resource
 *   metadata URL so self-hosted prefixes resolve correctly.
 * @param logger SDK logger; tokens, codes, verifiers and secrets are never logged.
 */
class OAuthResource internal constructor(
    private val publicHttp: ApiHttpClient,
    http: ApiHttpClient,
    private val config: OAuthConfig? = null,
    private val baseUrl: String = SdkConstants.DEFAULT_BASE_URL,
    logger: Logger = NoOpLogger,
) : BaseResource(http, null, logger) {

    /**
     * Builds a fresh authorization attempt: a new PKCE pair, a new `state`, and the full URL to open.
     *
     * Nothing is sent over the network. Open [AuthorizationRequest.url] with a full page navigation
     * — a Custom Tab or the system browser — never an in-app WebView or an AJAX call. Persist
     * [AuthorizationRequest.state] and [PkcePair.codeVerifier] in the user's session; [parseCallback]
     * and [exchangeCode] need them.
     *
     * Produces `https://auth.assinafy.com.br/oauth/authorize` with `response_type=code`,
     * `client_id`, `redirect_uri`, `scope`, `state`, `code_challenge`, `code_challenge_method=S256`,
     * `resource`, and `nonce` when one is supplied.
     *
     * If `client_id` or `redirect_uri` is wrong the user is **not** returned to the application —
     * the authorization server shows an error on its own page, because redirecting to an unverified
     * address would be unsafe.
     *
     * @param scopes Permissions to request; defaults to [OAuthConfig.scopes].
     * @param nonce Optional OpenID Connect nonce, echoed in the `id_token`.
     * @param pkce Pre-generated PKCE pair; a fresh one is generated per attempt by default.
     * @param state Pre-generated CSRF value; a fresh random one is generated by default.
     * @return The URL to open plus the `state` and verifier the exchange will require.
     * @throws ValidationException when no [OAuthConfig] is configured or [scopes] is empty.
     */
    fun authorizationRequest(
        scopes: List<String>? = null,
        nonce: String? = null,
        pkce: PkcePair = PkcePair.generate(),
        state: String = PkcePair.randomState(),
    ): AuthorizationRequest {
        val app = requireConfig()
        val requested = (scopes ?: app.scopes).filter { it.isNotBlank() }
        if (requested.isEmpty()) throw ValidationException("At least one OAuth scope is required")
        val query = queryString(
            "response_type" to "code",
            "client_id" to app.clientId,
            "redirect_uri" to app.redirectUri,
            "scope" to requested.joinToString(" "),
            "state" to state,
            "code_challenge" to pkce.codeChallenge,
            "code_challenge_method" to pkce.codeChallengeMethod,
            "resource" to resourceIndicator(),
            "nonce" to nonce,
        )
        val url = app.authorizationServerUrl.trimEnd('/') + AUTHORIZE_PATH + query
        logger.info("Prepared OAuth authorization request", mapOf("scopes" to requested))
        return AuthorizationRequest(url = url, state = state, pkce = pkce)
    }

    /**
     * Validates the redirect that comes back from the authorization server and returns the
     * single-use authorization code.
     *
     * Checks, in order, that `state` equals the value from [request] and that `iss` — when present —
     * equals the configured authorization server. Either mismatch means the response is not this
     * application's and the code is discarded rather than exchanged.
     *
     * Approved: `https://myapp.com/oauth/callback?code=…&state=…&iss=https://auth.assinafy.com.br`.
     * Declined: `…?error=access_denied&error_description=…&state=…`.
     *
     * The returned code is single-use and expires **60 seconds** after approval, so exchange it
     * immediately.
     *
     * @param callbackUri Full redirect URI as received, including its query string.
     * @param request The attempt returned by [authorizationRequest].
     * @return The authorization code to pass to [exchangeCode].
     * @throws OAuthException when the server reported an error, such as `access_denied`.
     * @throws ValidationException when `state` or `iss` does not match, or no code is present.
     */
    fun parseCallback(callbackUri: String, request: AuthorizationRequest): String =
        parseCallback(callbackUri, request.state)

    /**
     * Overload taking the stored `state` directly, for applications that persist it across process
     * death rather than holding the whole [AuthorizationRequest].
     *
     * @param callbackUri Full redirect URI as received.
     * @param expectedState The `state` generated for this attempt.
     * @return The authorization code to pass to [exchangeCode].
     * @throws OAuthException when the server reported an error.
     * @throws ValidationException when `state` or `iss` does not match, or no code is present.
     */
    fun parseCallback(callbackUri: String, expectedState: String): String {
        val params = parseQuery(callbackUri)
        val returnedState = params["state"]
        if (returnedState != expectedState) {
            throw ValidationException(
                "OAuth callback state does not match the authorization request",
                mapOf("hasState" to (returnedState != null)),
            )
        }
        val issuer = params["iss"]
        val expectedIssuer = requireConfig().authorizationServerUrl.trimEnd('/')
        if (issuer != null && issuer.trimEnd('/') != expectedIssuer) {
            throw ValidationException("OAuth callback issuer does not match the authorization server")
        }
        params["error"]?.let { throw OAuthException(it, params["error_description"]) }
        return params["code"]?.takeIf { it.isNotBlank() }
            ?: throw ValidationException("OAuth callback contains neither a code nor an error")
    }

    /**
     * Exchanges an authorization code for tokens with `POST /oauth/token`.
     *
     * Request body:
     * ```json
     * {
     *   "grant_type": "authorization_code",
     *   "code": "one-time-code",
     *   "redirect_uri": "https://myapp.com/oauth/callback",
     *   "client_id": "your-client-id",
     *   "code_verifier": "the-verifier-from-this-attempt",
     *   "resource": "https://api.assinafy.com.br"
     * }
     * ```
     * A confidential client adds `client_secret`; a public client omits it. Response body (flat, not
     * enveloped):
     * ```json
     * {
     *   "access_token": "…",
     *   "token_type": "Bearer",
     *   "expires_in": 3600,
     *   "scope": "documents:read documents:write",
     *   "refresh_token": "…",
     *   "id_token": "…"
     * }
     * ```
     * `refresh_token` appears only when `offline_access` was requested and consented; `id_token`
     * only when `openid` was granted. Read [OAuthTokens.scope] rather than assuming the requested
     * set was approved.
     *
     * @param code Single-use code from [parseCallback]; it expires 60 seconds after approval.
     * @param codeVerifier The verifier generated for this same attempt.
     * @param redirectUri Redirect URI override; defaults to [OAuthConfig.redirectUri] and must match
     *   the one sent to the authorization server exactly.
     * @return Access token, granted scopes, and the optional refresh and identity tokens.
     * @throws OAuthException `invalid_grant` for an expired, replayed or wrong-client code, a
     *   mismatched verifier or redirect URI; `invalid_client` for a bad client; `invalid_target`
     *   for a resource this server does not issue tokens for.
     * @throws ValidationException when no [OAuthConfig] is configured or an argument is blank.
     */
    suspend fun exchangeCode(code: String, codeVerifier: String, redirectUri: String? = null): OAuthTokens {
        val app = requireConfig()
        val body = buildMap<String, Any?> {
            put("grant_type", "authorization_code")
            put("code", requireId(code, "Authorization code"))
            put("redirect_uri", redirectUri ?: app.redirectUri)
            put("client_id", app.clientId)
            put("code_verifier", requireId(codeVerifier, "Code verifier"))
            app.clientSecret?.let { put("client_secret", it) }
            put("resource", resourceIndicator())
        }
        logger.info("Exchanging OAuth authorization code")
        return token(body)
    }

    /**
     * Renews an access token with `POST /oauth/token` and `grant_type=refresh_token`.
     *
     * Request body: `{"grant_type":"refresh_token","refresh_token":"…","client_id":"…"}`, plus
     * `client_secret` for a confidential client. The response has the same shape as [exchangeCode].
     *
     * Every refresh returns a **new** refresh token and retires the old one, so:
     *
     * 1. Persist [OAuthTokens.refreshToken] before doing anything else with the response.
     * 2. Treat a timeout as "it may have succeeded" — re-read the stored token before retrying,
     *    never retry blindly with the old one.
     * 3. Refresh one at a time per connection.
     *
     * Replaying a retired refresh token cannot be told apart from a stolen one, so it ends the whole
     * connection and the user must approve the application again. A connection also lasts only
     * **30 days from approval**; refreshing does not extend it.
     *
     * @param refreshToken The most recently stored refresh token for this connection.
     * @return A new access token and a new refresh token to store in its place.
     * @throws OAuthException `invalid_grant` when the token was already used, has expired, or the
     *   user reconnected with different permissions.
     * @throws ValidationException when no [OAuthConfig] is configured or [refreshToken] is blank.
     */
    suspend fun refresh(refreshToken: String): OAuthTokens {
        val app = requireConfig()
        val body = buildMap<String, Any?> {
            put("grant_type", "refresh_token")
            put("refresh_token", requireId(refreshToken, "Refresh token"))
            put("client_id", app.clientId)
            app.clientSecret?.let { put("client_secret", it) }
        }
        logger.info("Refreshing OAuth access token")
        return token(body)
    }

    /**
     * Revokes an access or refresh token with `POST /oauth/revoke`.
     *
     * Request body: `{"token":"…","client_id":"…"}` plus an optional `token_type_hint` of
     * `access_token` or `refresh_token`, and `client_secret` for a confidential client. The response
     * has no body.
     *
     * Call this when a user disconnects, instead of only deleting the stored token. Every token
     * outcome answers `200` — including a token that never existed, was already revoked, or is
     * malformed — so the endpoint cannot be used to probe whether a token exists. Only failed client
     * authentication answers `401`.
     *
     * @param token The access or refresh token to revoke.
     * @param tokenTypeHint Optional `access_token` or `refresh_token` hint.
     * @throws OAuthException `invalid_client` when client authentication fails.
     * @throws ValidationException when no [OAuthConfig] is configured or [token] is blank.
     */
    suspend fun revoke(token: String, tokenTypeHint: String? = null) {
        val app = requireConfig()
        val body = buildMap<String, Any?> {
            put("token", requireId(token, "Token"))
            tokenTypeHint?.let { put("token_type_hint", it) }
            put("client_id", app.clientId)
            app.clientSecret?.let { put("client_secret", it) }
        }
        logger.info("Revoking OAuth token")
        val response = request("Failed to revoke OAuth token") { publicHttp.post(REVOKE_PATH, toJson(body)) }
        if (response.statusCode !in 200..299) throw response.toOAuthException()
    }

    /**
     * Reads OpenID Connect claims with `GET /oauth/userinfo`, using the bearer token this client was
     * built with.
     *
     * Requires the `openid` scope; `name` additionally requires `profile` and `email` requires
     * `email`. Per OIDC Core §5.3.2 the response is a flat claims object, not this API's envelope:
     * `{"sub":"d6zqpbyog2v3xvxerwn8la94","name":"Maria Silva","email":"maria@example.com","email_verified":true}`.
     *
     * @return The claims the granted scopes allow; [UserInfo.sub] is always present.
     * @throws OAuthException when the token is missing, expired, or lacks the `openid` scope.
     */
    suspend fun userInfo(): UserInfo {
        val response = request("Failed to fetch OAuth userinfo") { http.get(USERINFO_PATH) }
        if (response.statusCode !in 200..299) throw response.toOAuthException()
        return GSON.fromJson(response.body, UserInfo::class.java)
            ?: throw OAuthException("invalid_response", "Userinfo response was empty", response.statusCode)
    }

    /**
     * Fetches this API's RFC 9728 protected-resource metadata from
     * `{apiOrigin}/.well-known/oauth-protected-resource`.
     *
     * Response: `{"resource":"https://api.assinafy.com.br","authorization_servers":["https://auth.assinafy.com.br"],`
     * `"scopes_supported":["documents:read", …],"bearer_methods_supported":["header"]}`.
     *
     * This is the entry point of discovery: take [ProtectedResourceMetadata.authorizationServer]
     * and read that host's own metadata with [authorizationServerMetadata]. The document is
     * unauthenticated and needs no [OAuthConfig].
     *
     * @return The metadata describing this API as a protected resource.
     * @throws OAuthException when the document cannot be retrieved.
     */
    suspend fun protectedResourceMetadata(): ProtectedResourceMetadata =
        discover(apiOrigin() + PROTECTED_RESOURCE_METADATA_PATH, ProtectedResourceMetadata::class.java)

    /**
     * Fetches the issuer's RFC 8414 metadata from
     * `{authorizationServer}/.well-known/oauth-authorization-server`.
     *
     * These documents are served **only** by the authorization server, never by this API. Most OAuth
     * libraries need nothing but the issuer and read the endpoints, supported scopes, PKCE methods
     * and client-authentication methods from here.
     *
     * @param issuer Authorization-server origin; defaults to [OAuthConfig.authorizationServerUrl],
     *   or to the Assinafy issuer when no application is configured.
     * @return Endpoint URLs and capabilities advertised by the issuer.
     * @throws OAuthException when the document cannot be retrieved.
     */
    suspend fun authorizationServerMetadata(issuer: String? = null): AuthorizationServerMetadata {
        val origin = (issuer ?: config?.authorizationServerUrl ?: OAuthConfig.DEFAULT_AUTHORIZATION_SERVER).trimEnd('/')
        return discover(origin + AUTHORIZATION_SERVER_METADATA_PATH, AuthorizationServerMetadata::class.java)
    }

    private suspend fun token(body: Map<String, Any?>): OAuthTokens {
        val response = request("OAuth token request failed") { publicHttp.post(TOKEN_PATH, toJson(body)) }
        if (response.statusCode !in 200..299) throw response.toOAuthException()
        return GSON.fromJson(response.body, OAuthTokens::class.java)
            ?: throw OAuthException("invalid_response", "Token response was empty", response.statusCode)
    }

    private suspend fun <T> discover(url: String, type: Class<T>): T {
        val response = request("Failed to fetch $url") { publicHttp.getAbsolute(url) }
        if (response.statusCode !in 200..299) throw response.toOAuthException()
        return GSON.fromJson(response.body, type)
            ?: throw OAuthException("invalid_response", "Discovery document at $url was empty", response.statusCode)
    }

    /**
     * Converts a failed OAuth response into a typed exception. The token and revocation endpoints
     * answer with a flat `{error, error_description}` body; userinfo answers `401`/`403` whose
     * detail lives in the `WWW-Authenticate` challenge instead.
     */
    private fun HttpRawResponse.toOAuthException(): OAuthException {
        val json = body?.takeIf { it.isNotBlank() }?.let {
            runCatching { JsonParser.parseString(it) as? JsonObject }.getOrNull()
        }
        val error = json?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
        val description = json?.get("error_description")?.takeIf { it.isJsonPrimitive }?.asString
        val challenge = OAuthChallenge.parse(headers["www-authenticate"])
        return OAuthException(
            error = error ?: challenge?.error ?: defaultErrorFor(statusCode),
            errorDescription = description ?: challenge?.errorDescription
                ?: challenge?.scope?.let { "Requires the $it scope" },
            statusCode = statusCode,
        )
    }

    /**
     * Normalizes transport failures into the SDK's exception hierarchy, so an OAuth call reports a
     * [com.assinafy.sdk.exceptions.NetworkException] on I/O failure like every other call rather
     * than leaking a raw `IOException`. Cancellation and [Error] propagate untouched.
     */
    private suspend fun request(label: String, block: suspend () -> HttpRawResponse): HttpRawResponse =
        runCatching { block() }.getOrElse { e ->
            when (e) {
                is CancellationException -> throw e
                is Error -> throw e
                is AssinafyException -> throw e
                else -> throw ResponseHandler.toSdkException(e, label)
            }
        }

    private fun defaultErrorFor(statusCode: Int): String = when (statusCode) {
        401 -> OAuthException.INVALID_CLIENT
        400 -> OAuthException.INVALID_REQUEST
        else -> "oauth_request_failed"
    }

    private fun requireConfig(): OAuthConfig = config ?: throw ValidationException(
        "OAuth is not configured. Set AssinafyClientConfig.oauth with your registered application.",
    )

    /** Resource indicator naming the API a token is issued for, e.g. `https://api.assinafy.com.br`. */
    private fun resourceIndicator(): String = config?.resource ?: apiOrigin()

    /** Scheme and authority of the configured base URL, without its `/v1` (or proxy) path prefix. */
    private fun apiOrigin(): String {
        val uri = URI(baseUrl.trim())
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "${uri.scheme}://${uri.host}$port"
    }

    private fun parseQuery(callbackUri: String): Map<String, String> {
        val query = URI(callbackUri.trim()).rawQuery ?: return emptyMap()
        return query.split('&')
            .filter { it.isNotBlank() }
            .associate { pair ->
                val name = pair.substringBefore('=')
                val value = pair.substringAfter('=', "")
                decode(name) to decode(value)
            }
    }

    private fun decode(value: String): String =
        java.net.URLDecoder.decode(value, Charsets.UTF_8.name())

    private companion object {
        val GSON: Gson = Gson()
        const val AUTHORIZE_PATH = "/oauth/authorize"
        const val TOKEN_PATH = "/oauth/token"
        const val REVOKE_PATH = "/oauth/revoke"
        const val USERINFO_PATH = "/oauth/userinfo"
        const val PROTECTED_RESOURCE_METADATA_PATH = "/.well-known/oauth-protected-resource"
        const val AUTHORIZATION_SERVER_METADATA_PATH = "/.well-known/oauth-authorization-server"
    }
}
