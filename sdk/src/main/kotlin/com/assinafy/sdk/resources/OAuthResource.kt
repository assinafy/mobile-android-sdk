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
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.Strictness
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
 * in an APK: the SDK never sends one, and rejects a configured one with [ValidationException]
 * before any request. An application's type cannot be changed, so an app registered as
 * `Confidential` moves to a new `Public` application — a new `client_id` — and its users connect
 * again.
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
     * Browser request: `GET https://auth.assinafy.com.br/oauth/authorize`; no request body.
     * Complete query fields (no verifier or client secret is sent):
     * ```json
     * {
     *   "response_type": "code",
     *   "client_id": "public-client-id",
     *   "redirect_uri": "https://example.com/callback",
     *   "scope": "documents:read account:read openid offline_access",
     *   "state": "per-attempt-state",
     *   "code_challenge": "S256-challenge",
     *   "code_challenge_method": "S256",
     *   "resource": "https://api.assinafy.com.br",
     *   "nonce": "per-attempt-nonce"
     * }
     * ```
     * The response is the interactive approval page, followed by the registered redirect with
     * `code`, `state` and `iss`, or `error`, `error_description`, `state` and `iss`.
     *
     * @param scopes Permissions to request; defaults to [OAuthConfig.scopes].
     * @param nonce Optional OpenID Connect nonce, echoed in the `id_token`.
     * @param pkce Pre-generated PKCE pair; a fresh one is generated per attempt by default.
     * @param state Pre-generated CSRF value; a fresh random one is generated by default.
     * @return The URL to open plus the `state` and verifier the exchange will require.
     * @throws ValidationException when no [OAuthConfig] is configured, it carries a client secret, or [scopes] is empty.
     */
    fun authorizationRequest(
        scopes: List<String>? = null,
        nonce: String? = null,
        pkce: PkcePair = PkcePair.generate(),
        state: String = PkcePair.randomState(),
    ): AuthorizationRequest {
        val app = requireConfig()
        val requested = scopes ?: app.scopes
        if (requested.isEmpty() || requested.any { !SCOPE_TOKEN.matches(it) }) {
            throw ValidationException("At least one valid OAuth scope is required")
        }
        requireId(state, "OAuth state")
        nonce?.let { requireId(it, "OpenID nonce") }
        PkcePair.validateVerifier(pkce.codeVerifier)
        if (pkce.codeChallengeMethod != "S256" || pkce.codeChallenge != PkcePair.challenge(pkce.codeVerifier)) {
            throw ValidationException("PKCE challenge must be the S256 challenge of the verifier")
        }
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
     * Checks, in order, that `state` equals the value from [request] and that `iss` equals the
     * configured authorization server, on an approval and on an error alike. Either mismatch — a
     * missing `iss` included — means the response is not this application's and the code is
     * discarded rather than exchanged.
     *
     * Approved: `https://myapp.com/oauth/callback?code=…&state=…&iss=https://auth.assinafy.com.br`.
     * Declined: `…?error=access_denied&error_description=…&state=…&iss=https://auth.assinafy.com.br`.
     *
     * The returned code is single-use and expires **60 seconds** after approval, so exchange it
     * immediately.
     *
     * Local operation; no HTTP request or response. Input is the complete registered HTTPS
     * redirect with query fields `code`, `state`, `iss`, or `error`, `error_description`, `state`, `iss`.
     * Returns the single-use code; no token is exchanged. Duplicate parameters, a different
     * registered address, fragments and malformed URIs raise ValidationException.
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
     * Local operation; no HTTP request or response. Input is the complete registered HTTPS
     * redirect with query fields `code`, `state`, `iss`, or `error`, `error_description`, `state`, `iss`.
     * Returns the single-use code; no token is exchanged. Duplicate parameters, a different
     * registered address, fragments and malformed URIs raise ValidationException.
     *
     * @param callbackUri Full redirect URI as received.
     * @param expectedState The `state` generated for this attempt.
     * @return The authorization code to pass to [exchangeCode].
     * @throws OAuthException when the server reported an error.
     * @throws ValidationException when `state` or `iss` does not match, or no code is present.
     */
    fun parseCallback(callbackUri: String, expectedState: String): String {
        requireId(expectedState, "Expected OAuth state")
        val app = requireConfig()
        val params = parseQuery(callbackUri)
        val returnedState = params["state"]
        if (returnedState != expectedState) {
            throw ValidationException(
                "OAuth callback state does not match the authorization request",
                mapOf("hasState" to (returnedState != null)),
            )
        }
        val issuer = params["iss"]
        val expectedIssuer = app.authorizationServerUrl.trimEnd('/')
        if (issuer?.trimEnd('/') != expectedIssuer) {
            throw ValidationException("OAuth callback issuer does not match the authorization server")
        }
        params["error"]?.let { throw OAuthException(it, params["error_description"]) }
        return params["code"]?.takeIf { it.isNotBlank() }
            ?: throw ValidationException("OAuth callback contains neither a code nor an error")
    }

    /**
     * Exchanges an authorization code for tokens with `POST /oauth/token`.
     *
     * Request body (`application/x-www-form-urlencoded`, sent once and never replayed):
     * `grant_type=authorization_code`, `code`, `redirect_uri`, `client_id`, `code_verifier` and
     * `resource=https://api.assinafy.com.br`. A public client sends no `client_secret`. Response
     * body (flat, not enveloped):
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
     * Complete form fields for `POST /oauth/token` (shown as JSON for readability; sent as form encoding):
     * ```json
     * {
     *   "grant_type": "authorization_code",
     *   "code": "<single-use-code>",
     *   "redirect_uri": "https://example.com/callback",
     *   "client_id": "public-client-id",
     *   "code_verifier": "<43-to-128-unreserved-characters>",
     *   "resource": "https://api.assinafy.com.br"
     * }
     * ```
     * Malformed or incomplete successful token responses raise `OAuthException.INVALID_RESPONSE`.
     *
     * @param code Single-use code from [parseCallback]; it expires 60 seconds after approval.
     * @param codeVerifier The verifier generated for this same attempt.
     * @param redirectUri Redirect URI override; defaults to [OAuthConfig.redirectUri] and must match
     *   the one sent to the authorization server exactly.
     * @return Access token, granted scopes, and the optional refresh and identity tokens.
     * @throws OAuthException `invalid_grant` for an expired, replayed or wrong-client code, a
     *   mismatched verifier or redirect URI; `invalid_client` for a bad client; `invalid_target`
     *   for a resource this server does not issue tokens for.
     * @throws ValidationException when no [OAuthConfig] is configured, it carries a client secret, or an argument is blank.
     */
    suspend fun exchangeCode(code: String, codeVerifier: String, redirectUri: String? = null): OAuthTokens {
        val app = requireConfig()
        PkcePair.validateVerifier(codeVerifier)
        val redirect = redirectUri ?: app.redirectUri
        requireHttpsUri(redirect, "OAuth redirect URI")
        val body = mapOf(
            "grant_type" to "authorization_code",
            "code" to requireId(code, "Authorization code"),
            "redirect_uri" to redirect,
            "client_id" to app.clientId,
            "code_verifier" to codeVerifier,
            "resource" to resourceIndicator(),
        )
        logger.info("Exchanging OAuth authorization code")
        return token(body)
    }

    /**
     * Renews an access token with `POST /oauth/token` and `grant_type=refresh_token`.
     *
     * Request body (`application/x-www-form-urlencoded`, sent once and never replayed):
     * `grant_type=refresh_token`, `refresh_token` and `client_id`. The response has the same shape
     * as [exchangeCode].
     *
     * Every refresh returns a **new** refresh token and retires the old one, so:
     *
     * 1. Persist [OAuthTokens.refreshToken] and [OAuthTokens.accessToken] together before doing
     *    anything else with the response, and build later clients from the new access token.
     * 2. Never send the same refresh token twice. After a failure that may have reached the server —
     *    a timeout, a dropped connection, an error status, `invalid_response` — re-read the stored
     *    token: continue only if a different, newer one was saved meanwhile; if it is unchanged, ask
     *    the user to connect again instead of resending it.
     * 3. Only a failure that provably happened before anything was sent is safe to retry with the
     *    same token: a [com.assinafy.sdk.exceptions.NetworkException] whose cause is
     *    `java.net.UnknownHostException`, `java.net.ConnectException` or
     *    `javax.net.ssl.SSLHandshakeException`.
     * 4. Refresh one at a time per connection.
     * 5. Run this call and the save together under `NonCancellable`: cancelling the coroutine
     *    mid-request can discard a response whose token the server already rotated.
     *
     * Replaying a retired refresh token cannot be told apart from a stolen one, so it ends the whole
     * connection and the user must approve the application again. A refresh token is valid for
     * **30 days**, and every refresh returns a new one with a fresh 30 days: a connection only
     * expires after 30 days without a refresh.
     *
     * Complete form fields for `POST /oauth/token` (shown as JSON; sent as form encoding):
     * ```json
     * {
     *   "grant_type": "refresh_token",
     *   "refresh_token": "<latest-refresh-token>",
     *   "client_id": "public-client-id"
     * }
     * ```
     * Complete flat response body:
     * ```json
     * {
     *   "access_token": "<access-token>",
     *   "token_type": "Bearer",
     *   "expires_in": 3600,
     *   "scope": "documents:read account:read openid",
     *   "refresh_token": "<rotated-refresh-token>",
     *   "id_token": "<id-token>"
     * }
     * ```
     *
     * @param refreshToken The most recently stored refresh token for this connection.
     * @return A new access token and a new refresh token to store in its place.
     * @throws OAuthException `invalid_grant` when the token was already used, has expired, or the
     *   user reconnected with different permissions; `invalid_response` when a successful answer
     *   carries no new refresh token (missing, blank, or the one sent).
     * @throws ValidationException when no [OAuthConfig] is configured, it carries a client secret, or [refreshToken] is blank.
     */
    suspend fun refresh(refreshToken: String): OAuthTokens {
        val app = requireConfig()
        val sent = requireId(refreshToken, "Refresh token")
        val body = mapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to sent,
            "client_id" to app.clientId,
        )
        logger.info("Refreshing OAuth access token")
        val tokens = token(body)
        // The server may already have retired `sent`; without a distinct replacement the caller would
        // keep a dead token, and replaying it ends the connection.
        if (tokens.refreshToken.isNullOrBlank() || tokens.refreshToken == sent) {
            throw OAuthException(OAuthException.INVALID_RESPONSE, "Refresh response carried no new refresh token")
        }
        return tokens
    }

    /**
     * Revokes an access or refresh token with `POST /oauth/revoke`.
     *
     * Request body (`application/x-www-form-urlencoded`): `token` and `client_id`, plus an optional
     * `token_type_hint` of `access_token` or `refresh_token`. The response has no body.
     *
     * Call this when a user disconnects, instead of only deleting the stored token. Every token
     * outcome answers `200` — including a token that never existed, was already revoked, or is
     * malformed — so the endpoint cannot be used to probe whether a token exists. Only failed client
     * authentication answers `401`.
     *
     * Complete form fields for `POST /oauth/revoke` (shown as JSON; sent as form encoding):
     * ```json
     * {
     *   "token": "<latest-token>",
     *   "token_type_hint": "refresh_token",
     *   "client_id": "public-client-id"
     * }
     * ```
     * Response: HTTP 200 with no body. The hint is optional and accepts only `access_token` or `refresh_token`.
     *
     * @param token The access or refresh token to revoke.
     * @param tokenTypeHint Optional `access_token` or `refresh_token` hint.
     * @throws OAuthException `invalid_client` when client authentication fails.
     * @throws ValidationException when no [OAuthConfig] is configured, it carries a client secret, or [token] is blank.
     */
    suspend fun revoke(token: String, tokenTypeHint: String? = null) {
        val app = requireConfig()
        if (tokenTypeHint != null && tokenTypeHint !in setOf("access_token", "refresh_token")) {
            throw ValidationException("OAuth token hint must be access_token or refresh_token")
        }
        val body = buildMap<String, String> {
            put("token", requireId(token, "Token"))
            tokenTypeHint?.let { put("token_type_hint", it) }
            put("client_id", app.clientId)
        }
        logger.info("Revoking OAuth token")
        val response = request("Failed to revoke OAuth token") { publicHttp.postForm(REVOKE_PATH, body) }
        if (response.statusCode !in 200..299) throw response.toOAuthException()
    }

    /**
     * Reads OpenID Connect claims with `GET /oauth/userinfo`, using the bearer token this client was
     * built with.
     *
     * Requires the `openid` scope; `name` additionally requires `profile` and `email` requires
     * `email`. Per OIDC Core §5.3.2 the response is a flat claims object, not this API's envelope:
     * `{"sub":"user-placeholder","name":"Maria Silva","email":"maria@example.com","email_verified":true}`.
     *
     * Request: `GET /oauth/userinfo`, bearer authentication, no request body.
     * Complete flat response body (name/email claims depend on granted scopes):
     * ```json
     * {
     *   "sub": "user-placeholder",
     *   "name": "Example User",
     *   "email": "person@example.com",
     *   "email_verified": true
     * }
     * ```
     *
     * @return The claims the granted scopes allow; [UserInfo.sub] is always present.
     * @throws OAuthException when the token is missing, expired, or lacks the `openid` scope.
     */
    suspend fun userInfo(): UserInfo {
        val response = request("Failed to fetch OAuth userinfo") { http.get(USERINFO_PATH) }
        if (response.statusCode !in 200..299) throw response.toOAuthException()
        return parseResponse(response, UserInfo::class.java, listOf("sub"))
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
     * Request: `GET {apiOrigin}/.well-known/oauth-protected-resource`; no credentials or body.
     * Complete flat response body:
     * ```json
     * {
     *   "resource": "https://api.assinafy.com.br",
     *   "authorization_servers": [
     *     "https://auth.assinafy.com.br"
     *   ],
     *   "scopes_supported": [
     *     "documents:read",
     *     "documents:write",
     *     "templates:read",
     *     "templates:write",
     *     "account:read",
     *     "webhooks:write",
     *     "openid",
     *     "profile",
     *     "email"
     *   ],
     *   "bearer_methods_supported": [
     *     "header"
     *   ]
     * }
     * ```
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
     * Request: `GET {issuer}/.well-known/oauth-authorization-server`; no credentials or body.
     * Complete flat response body:
     * ```json
     * {
     *   "issuer": "https://auth.assinafy.com.br",
     *   "authorization_endpoint": "https://auth.assinafy.com.br/oauth/authorize",
     *   "token_endpoint": "https://api.assinafy.com.br/v1/oauth/token",
     *   "revocation_endpoint": "https://api.assinafy.com.br/v1/oauth/revoke",
     *   "userinfo_endpoint": "https://api.assinafy.com.br/v1/oauth/userinfo",
     *   "introspection_endpoint": "https://api.assinafy.com.br/v1/oauth/introspect",
     *   "introspection_endpoint_auth_methods_supported": [
     *     "client_secret_post"
     *   ],
     *   "jwks_uri": "https://auth.assinafy.com.br/.well-known/jwks.json",
     *   "scopes_supported": [
     *     "documents:read",
     *     "documents:write",
     *     "templates:read",
     *     "templates:write",
     *     "account:read",
     *     "webhooks:write",
     *     "openid",
     *     "profile",
     *     "email",
     *     "offline_access"
     *   ],
     *   "response_types_supported": [
     *     "code"
     *   ],
     *   "grant_types_supported": [
     *     "authorization_code",
     *     "refresh_token",
     *     "urn:ietf:params:oauth:grant-type:token-exchange"
     *   ],
     *   "code_challenge_methods_supported": [
     *     "S256"
     *   ],
     *   "token_endpoint_auth_methods_supported": [
     *     "client_secret_post",
     *     "none"
     *   ],
     *   "authorization_response_iss_parameter_supported": true,
     *   "client_id_metadata_document_supported": true
     * }
     * ```
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

    private suspend fun token(body: Map<String, String>): OAuthTokens {
        val response = request("OAuth token request failed") { publicHttp.postForm(TOKEN_PATH, body) }
        if (response.statusCode !in 200..299) throw response.toOAuthException()
        return parseResponse(response, OAuthTokens::class.java, listOf("access_token", "token_type")).also {
            if (!it.tokenType.equals("Bearer", ignoreCase = true) || it.expiresIn <= 0) {
                throw OAuthException(OAuthException.INVALID_RESPONSE, "Invalid token type or lifetime", response.statusCode)
            }
        }
    }

    private suspend fun <T> discover(url: String, type: Class<T>): T {
        val response = request("Failed to fetch $url") { publicHttp.getAbsolute(url) }
        if (response.statusCode !in 200..299) throw response.toOAuthException()
        val required = if (type == ProtectedResourceMetadata::class.java) {
            listOf("resource")
        } else {
            listOf("issuer", "authorization_endpoint", "token_endpoint")
        }
        return parseResponse(response, type, required)
    }

    private fun <T> parseResponse(response: HttpRawResponse, type: Class<T>, required: List<String>): T {
        try {
            val json = GSON.fromJson(response.body ?: "", JsonObject::class.java)
                ?: throw OAuthException(OAuthException.INVALID_RESPONSE, "Empty OAuth response", response.statusCode)
            if (required.any { name ->
                    val value = json.get(name)
                    value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString || value.asString.isBlank()
                }
            ) {
                throw OAuthException(OAuthException.INVALID_RESPONSE, "OAuth response is missing required fields", response.statusCode)
            }
            return GSON.fromJson(json, type)
        } catch (_: JsonParseException) {
            throw OAuthException(OAuthException.INVALID_RESPONSE, "Malformed OAuth response", response.statusCode)
        } catch (_: IllegalStateException) {
            throw OAuthException(OAuthException.INVALID_RESPONSE, "Malformed OAuth response", response.statusCode)
        } catch (_: NumberFormatException) {
            throw OAuthException(OAuthException.INVALID_RESPONSE, "Malformed OAuth response", response.statusCode)
        }
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

    @Suppress("DEPRECATION")
    private fun requireConfig(): OAuthConfig {
        val app = config ?: throw ValidationException(
            "OAuth is not configured. Set AssinafyClientConfig.oauth with your registered application.",
        )
        // An Android app is a public client: a secret in an APK is extractable, so refuse to ship or send it.
        if (app.clientSecret != null) {
            throw ValidationException(
                "OAuthConfig.clientSecret must be null: an Android app is a public OAuth client and the SDK " +
                    "never sends a client secret. An application's type cannot be changed, so create a new " +
                    "Public application in Assinafy, use its client_id without clientSecret, and have each " +
                    "user connect again.",
            )
        }
        requireId(app.clientId, "OAuth client ID")
        requireHttpsUri(app.redirectUri, "OAuth redirect URI")
        requireHttpsUri(app.authorizationServerUrl, "OAuth authorization server", allowQuery = false)
        app.resource?.let { requireHttpsUri(it, "OAuth resource indicator") }
        return app
    }

    /** Resource indicator naming the API a token is issued for, e.g. `https://api.assinafy.com.br`. */
    private fun resourceIndicator(): String = config?.resource ?: apiOrigin()

    /** Scheme and authority of the configured base URL, without its `/v1` (or proxy) path prefix. */
    private fun apiOrigin(): String {
        val uri = URI(baseUrl.trim())
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "${uri.scheme}://${uri.host}$port"
    }

    private fun parseQuery(callbackUri: String): Map<String, String> {
        val uri = requireHttpsUri(callbackUri, "OAuth callback URI", allowQuery = true)
        val registered = URI(requireConfig().redirectUri)
        if (uri.scheme != registered.scheme || uri.rawAuthority != registered.rawAuthority || uri.rawPath != registered.rawPath) {
            throw ValidationException("OAuth callback does not match the registered redirect URI")
        }
        val query = uri.rawQuery ?: return emptyMap()
        val params = mutableMapOf<String, String>()
        query.split('&').filter { it.isNotBlank() }.forEach { pair ->
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            val decodedName = decode(name)
            if (params.put(decodedName, decode(value)) != null) {
                throw ValidationException("OAuth callback contains duplicate parameters")
            }
        }
        registered.rawQuery?.split('&')?.forEach { pair ->
            if (params[decode(pair.substringBefore('='))] != decode(pair.substringAfter('=', ""))) {
                throw ValidationException("OAuth callback does not match the registered redirect query")
            }
        }
        return params
    }

    private fun requireHttpsUri(value: String, name: String, allowQuery: Boolean = true): URI {
        val uri = try {
            URI(value)
        } catch (_: java.net.URISyntaxException) {
            throw ValidationException("$name must be a valid HTTPS URI")
        }
        if (uri.scheme != "https" ||
            uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null ||
            uri.rawFragment != null ||
            (!allowQuery && uri.rawQuery != null)
        ) {
            throw ValidationException("$name must be an absolute HTTPS URI without user info or a fragment")
        }
        return uri
    }

    private fun decode(value: String): String =
        java.net.URLDecoder.decode(value, Charsets.UTF_8.name())

    private companion object {
        val GSON: Gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
        val SCOPE_TOKEN = Regex("[\\x21\\x23-\\x5B\\x5D-\\x7E]+")
        const val AUTHORIZE_PATH = "/oauth/authorize"
        const val TOKEN_PATH = "/oauth/token"
        const val REVOKE_PATH = "/oauth/revoke"
        const val USERINFO_PATH = "/oauth/userinfo"
        const val PROTECTED_RESOURCE_METADATA_PATH = "/.well-known/oauth-protected-resource"
        const val AUTHORIZATION_SERVER_METADATA_PATH = "/.well-known/oauth-authorization-server"
    }
}
