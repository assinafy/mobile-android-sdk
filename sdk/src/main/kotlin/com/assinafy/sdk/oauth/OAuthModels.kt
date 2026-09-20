package com.assinafy.sdk.oauth

import com.assinafy.sdk.exceptions.ValidationException
import com.google.gson.annotations.SerializedName
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * OAuth 2.1 scopes published by the Assinafy authorization server.
 *
 * Request the minimum set your application needs: the user approves everything requested or
 * nothing, and every extra permission is another line they read before deciding.
 */
object OAuthScope {
    /** Read documents, their pages, tags, signers, assignments and activity. */
    const val DOCUMENTS_READ = "documents:read"

    /**
     * Create, update and delete documents, and manage their signers, assignments and activity.
     * Sending a document for signature notifies signers, so this scope can spend workspace credits.
     */
    const val DOCUMENTS_WRITE = "documents:write"

    /** Read reusable document templates, their pages, roles, fields and tags. */
    const val TEMPLATES_READ = "templates:read"

    /** Create, update and delete templates, their pages, roles, fields and tags. */
    const val TEMPLATES_WRITE = "templates:write"

    /** Read the workspace's profile, theme and logo. */
    const val ACCOUNT_READ = "account:read"

    /** Identify the authenticated user and enable `GET /oauth/userinfo`. */
    const val OPENID = "openid"

    /** Include the user's name in the `id_token` and userinfo claims. */
    const val PROFILE = "profile"

    /** Include the user's email and its verification status in the `id_token` and userinfo claims. */
    const val EMAIL = "email"

    /**
     * Request a refresh token so the application keeps working after the user's session expires.
     * It is a request-time signal rather than a permission, so it never appears in the granted
     * [OAuthTokens.scope].
     */
    const val OFFLINE_ACCESS = "offline_access"
}

/**
 * RFC 7636 PKCE verifier/challenge pair. Generate a new pair for **every** connection attempt and
 * keep [codeVerifier] in the user's session until the code is exchanged.
 *
 * @property codeVerifier High-entropy secret, 43-128 characters from the RFC 7636 unreserved set.
 * @property codeChallenge Base64url-encoded SHA-256 of [codeVerifier], without padding.
 * @property codeChallengeMethod Always `S256`; the authorization server accepts no other method.
 */
data class PkcePair(
    val codeVerifier: String,
    val codeChallenge: String,
    val codeChallengeMethod: String = "S256",
) {
    /** Returns a diagnostic representation with the verifier redacted. */
    override fun toString(): String =
        "PkcePair(codeVerifier=***, codeChallenge=$codeChallenge, codeChallengeMethod=$codeChallengeMethod)"

    /** Creates cryptographically random PKCE pairs. */
    companion object {
        private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        private const val DEFAULT_VERIFIER_LENGTH = 64
        private val RANDOM = SecureRandom()

        /**
         * Generates a new verifier and its S256 challenge.
         *
         * The verifier is drawn directly from the RFC 7636 unreserved alphabet, so it is in-grammar
         * by construction; a 64-character verifier carries roughly 387 bits of entropy.
         *
         * @param verifierLength Verifier length; RFC 7636 allows 43 to 128 characters.
         * @return A fresh pair to use for exactly one authorization attempt.
         * @throws ValidationException if [verifierLength] is outside the RFC 7636 range.
         */
        fun generate(verifierLength: Int = DEFAULT_VERIFIER_LENGTH): PkcePair {
            if (verifierLength !in 43..128) {
                throw ValidationException("PKCE verifier length must be between 43 and 128 characters")
            }
            val verifier = buildString(verifierLength) {
                repeat(verifierLength) { append(UNRESERVED[RANDOM.nextInt(UNRESERVED.length)]) }
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return PkcePair(codeVerifier = verifier, codeChallenge = base64UrlNoPadding(digest))
        }

        /** Generates an opaque random value suitable for the `state` parameter. */
        internal fun randomState(): String = buildString(32) {
            repeat(32) { append(UNRESERVED[RANDOM.nextInt(UNRESERVED.length)]) }
        }
    }
}

private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/**
 * RFC 4648 §5 base64url without padding. Hand-rolled because `java.util.Base64` needs API 26 while
 * this SDK supports API 21, and `android.util.Base64` is unavailable to JVM unit tests.
 */
internal fun base64UrlNoPadding(bytes: ByteArray): String {
    val out = StringBuilder((bytes.size + 2) / 3 * 4)
    var index = 0
    while (index < bytes.size) {
        val b0 = bytes[index].toInt() and 0xFF
        val b1 = if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xFF else -1
        val b2 = if (index + 2 < bytes.size) bytes[index + 2].toInt() and 0xFF else -1
        out.append(BASE64_URL_ALPHABET[b0 ushr 2])
        out.append(BASE64_URL_ALPHABET[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 ushr 4 else 0)])
        if (b1 >= 0) out.append(BASE64_URL_ALPHABET[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 ushr 6 else 0)])
        if (b2 >= 0) out.append(BASE64_URL_ALPHABET[b2 and 0x3F])
        index += 3
    }
    return out.toString()
}

/**
 * Registration of an OAuth application, as created under *Settings → OAuth applications* in the
 * Assinafy app.
 *
 * An Android application is a **public client**: it runs on the user's device and cannot keep a
 * secret, so it is registered as `Public`, leaves [clientSecret] `null`, and authenticates with PKCE
 * alone. Shipping a client secret inside an APK grants anyone who unpacks it your application's
 * identity.
 *
 * @property clientId Public identifier issued when the application was registered.
 * @property redirectUri Registered return address, matched character for character by the
 *   authorization server. Must be `https://` without a fragment; `…/callback` and `…/callback/` are
 *   different URIs.
 * @property scopes Permissions requested at connect time, within what the application is registered
 *   for. Defaults to read-only document access.
 * @property clientSecret Confidential-client secret, sent as `client_secret_post`. Leave `null` in
 *   a distributed application.
 * @property authorizationServerUrl Issuer that owns the browser-facing approval page. Defaults to
 *   the production issuer; for any other environment read it from
 *   `client.oauth.protectedResourceMetadata().authorizationServer` rather than guessing.
 * @property resource RFC 8707 resource indicator naming the API the token is for. `null` derives it
 *   from the client's base URL origin, which is correct for both sandbox and production.
 */
data class OAuthConfig(
    val clientId: String,
    val redirectUri: String,
    val scopes: List<String> = listOf(OAuthScope.DOCUMENTS_READ),
    val clientSecret: String? = null,
    val authorizationServerUrl: String = DEFAULT_AUTHORIZATION_SERVER,
    val resource: String? = null,
) {
    /** Returns a diagnostic representation with the client secret redacted. */
    override fun toString(): String =
        "OAuthConfig(clientId=$clientId, redirectUri=$redirectUri, scopes=$scopes, " +
            "clientSecret=${if (clientSecret == null) "null" else "***"}, " +
            "authorizationServerUrl=$authorizationServerUrl, resource=$resource)"

    /** Authorization-server defaults. */
    companion object {
        /** Issuer that hosts the approval page and the JWKS document. */
        const val DEFAULT_AUTHORIZATION_SERVER = "https://auth.assinafy.com.br"
    }
}

/**
 * A prepared authorization attempt. Open [url] in a browser or Custom Tab, and keep [state] and
 * [pkce] in the user's session until the redirect comes back — both are needed to complete the
 * exchange safely.
 *
 * @property url Full authorization URL, with every parameter URL-encoded.
 * @property state Random per-attempt CSRF value echoed by the authorization server.
 * @property pkce Verifier/challenge pair whose verifier the token exchange requires.
 */
data class AuthorizationRequest(
    val url: String,
    val state: String,
    val pkce: PkcePair,
)

/**
 * Successful token response from `POST /oauth/token`.
 *
 * Unlike the rest of the API, the OAuth endpoints answer with a flat RFC 6749 §5.1 object rather
 * than the `{status, message, data}` envelope.
 *
 * @property accessToken Bearer token for API calls; valid for [expiresIn] seconds (1 hour).
 * @property tokenType Token type; always `Bearer` in practice.
 * @property expiresIn Access-token lifetime in seconds.
 * @property refreshToken Present only when `offline_access` was requested **and** consented. Every
 *   refresh returns a new one and retires the old one — persist it before using the response.
 * @property scope Permissions actually granted, space-separated. Read it instead of assuming the
 *   requested set was approved. `offline_access` never appears here.
 * @property idToken Signed OpenID Connect identity token (RS256), present only when `openid` was
 *   granted.
 */
data class OAuthTokens(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String? = null,
    @SerializedName("expires_in") val expiresIn: Int = 3_600,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("scope") val scope: String? = null,
    @SerializedName("id_token") val idToken: String? = null,
) {
    /** Granted permissions split from [scope], or an empty list when the server returned none. */
    val scopes: List<String> get() = scope?.split(' ')?.filter { it.isNotBlank() } ?: emptyList()

    /** Returns `true` when [scope] includes [scope name][required]. */
    fun hasScope(required: String): Boolean = required in scopes

    /** Returns a diagnostic representation with every token value redacted. */
    override fun toString(): String =
        "OAuthTokens(accessToken=***, tokenType=$tokenType, expiresIn=$expiresIn, " +
            "refreshToken=${if (refreshToken == null) "null" else "***"}, scope=$scope, " +
            "idToken=${if (idToken == null) "null" else "***"})"
}

/**
 * OpenID Connect claims from `GET /oauth/userinfo`, which requires the `openid` scope.
 *
 * @property sub Stable identifier of the user who approved the connection.
 * @property name Display name; `null` unless the `profile` scope was granted.
 * @property email Email address; `null` unless the `email` scope was granted.
 * @property emailVerified Whether [email] is verified; `null` unless the `email` scope was granted.
 */
data class UserInfo(
    @SerializedName("sub") val sub: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("email_verified") val emailVerified: Boolean? = null,
)

/**
 * RFC 9728 protected-resource metadata served at `{apiOrigin}/.well-known/oauth-protected-resource`.
 *
 * Only `resource` is required by RFC 9728, so the remaining members are nullable; Assinafy populates
 * all of them.
 *
 * @property resource Canonical identifier of this API, and the RFC 8707 `resource` value to send.
 * @property authorizationServers Issuers that can mint tokens for [resource]; start discovery at
 *   the first entry's own `/.well-known/oauth-authorization-server` document.
 * @property scopesSupported Scopes this API accepts. `offline_access` is deliberately absent — it
 *   is a client concern, not something the resource is protected by.
 * @property bearerMethodsSupported How a token may be presented; Assinafy accepts `header` only.
 */
data class ProtectedResourceMetadata(
    @SerializedName("resource") val resource: String,
    @SerializedName("authorization_servers") val authorizationServers: List<String>? = null,
    @SerializedName("scopes_supported") val scopesSupported: List<String>? = null,
    @SerializedName("bearer_methods_supported") val bearerMethodsSupported: List<String>? = null,
) {
    /**
     * First issuer advertised by [authorizationServers], or `null` when the document lists none.
     * This is the value to pass to
     * [com.assinafy.sdk.resources.OAuthResource.authorizationServerMetadata].
     */
    val authorizationServer: String? get() = authorizationServers?.firstOrNull()
}

/**
 * RFC 8414 authorization-server metadata served by the issuer, not by this API.
 *
 * Only `issuer`, `authorization_endpoint` and `token_endpoint` are required by RFC 8414, so the
 * remaining members are nullable; Assinafy populates all of them.
 *
 * @property issuer Canonical issuer identifier; must equal the `iss` returned on the redirect.
 * @property authorizationEndpoint Browser-facing approval page.
 * @property tokenEndpoint Endpoint that exchanges codes and refresh tokens.
 * @property revocationEndpoint Endpoint that revokes an access or refresh token.
 * @property userinfoEndpoint OpenID Connect claims endpoint.
 * @property jwksUri Signing keys used to validate an `id_token`.
 * @property scopesSupported Scopes the issuer can grant.
 * @property responseTypesSupported Supported authorization response types; Assinafy issues `code`.
 * @property grantTypesSupported Supported grants: `authorization_code` and `refresh_token`.
 * @property codeChallengeMethodsSupported PKCE methods; Assinafy accepts `S256` only.
 * @property tokenEndpointAuthMethodsSupported `client_secret_post` for confidential clients and
 *   `none` for public ones.
 */
data class AuthorizationServerMetadata(
    @SerializedName("issuer") val issuer: String,
    @SerializedName("authorization_endpoint") val authorizationEndpoint: String,
    @SerializedName("token_endpoint") val tokenEndpoint: String,
    @SerializedName("revocation_endpoint") val revocationEndpoint: String? = null,
    @SerializedName("userinfo_endpoint") val userinfoEndpoint: String? = null,
    @SerializedName("jwks_uri") val jwksUri: String? = null,
    @SerializedName("scopes_supported") val scopesSupported: List<String>? = null,
    @SerializedName("response_types_supported") val responseTypesSupported: List<String>? = null,
    @SerializedName("grant_types_supported") val grantTypesSupported: List<String>? = null,
    @SerializedName("code_challenge_methods_supported") val codeChallengeMethodsSupported: List<String>? = null,
    @SerializedName("token_endpoint_auth_methods_supported") val tokenEndpointAuthMethodsSupported: List<String>? = null,
)

/**
 * A parsed RFC 6750 `WWW-Authenticate: Bearer …` challenge.
 *
 * The API attaches one to every `401` and to a `403` caused by a missing scope, which is the signal
 * to send the user through the authorization flow again with [scope] added rather than to retry.
 *
 * @property error Challenge code, such as `invalid_token` or `insufficient_scope`.
 * @property errorDescription Human-readable detail, when the server supplies one.
 * @property scope Scope the refused call required; populated for `insufficient_scope`.
 * @property resourceMetadata URL of the RFC 9728 document describing this API.
 */
data class OAuthChallenge(
    val error: String? = null,
    val errorDescription: String? = null,
    val scope: String? = null,
    val resourceMetadata: String? = null,
) {
    /** Whether the call was refused only because the token lacks [scope]. */
    val isInsufficientScope: Boolean get() = error == INSUFFICIENT_SCOPE

    /** Challenge parsing. */
    companion object {
        /** Challenge code returned when the token is valid but lacks a required scope. */
        const val INSUFFICIENT_SCOPE = "insufficient_scope"

        private val PARAMETER = Regex("""([A-Za-z_-]+)\s*=\s*"([^"]*)"""")

        /**
         * Parses a `WWW-Authenticate` header value.
         *
         * @param header Raw header value, or `null` when the response carried none.
         * @return The parsed challenge, or `null` when [header] is absent or is not a Bearer challenge.
         */
        fun parse(header: String?): OAuthChallenge? {
            if (header.isNullOrBlank() || !header.trimStart().startsWith("Bearer", ignoreCase = true)) return null
            val values = PARAMETER.findAll(header).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            return OAuthChallenge(
                error = values["error"],
                errorDescription = values["error_description"],
                scope = values["scope"],
                resourceMetadata = values["resource_metadata"],
            )
        }
    }
}
