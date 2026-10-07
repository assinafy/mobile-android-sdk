package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.MfaRequiredException
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.http.HttpRawResponse
import com.assinafy.sdk.models.ApiKeyResponse
import com.assinafy.sdk.models.AuthenticationEmailResponse
import com.assinafy.sdk.models.AuthenticationSession
import com.assinafy.sdk.models.MfaRecoveryCodes
import com.assinafy.sdk.models.MfaRemoval
import com.assinafy.sdk.models.MfaStatus
import com.assinafy.sdk.models.TotpEnrollment
import com.assinafy.sdk.request.ChangePasswordRequest
import com.assinafy.sdk.request.ConfirmTotpRequest
import com.assinafy.sdk.request.CreateApiKeyRequest
import com.assinafy.sdk.request.LinkSocialLoginRequest
import com.assinafy.sdk.request.LoginRequest
import com.assinafy.sdk.request.MfaReauthRequest
import com.assinafy.sdk.request.MfaVerifyRequest
import com.assinafy.sdk.request.RequestPasswordResetRequest
import com.assinafy.sdk.request.ResetPasswordRequest
import com.assinafy.sdk.request.SocialLoginRequest
import com.assinafy.sdk.util.ResponseHandler
import com.assinafy.sdk.util.requireValidEmail
import com.google.gson.JsonObject

/**
 * Human-user authentication, two-factor authentication, password management, social identity
 * linking, and personal API-key management.
 *
 * [http] is used for authenticated operations. [publicHttp] is used for login, social login, and password-reset
 * operations that declare no authentication in the API contract; it defaults to [http].
 *
 * @param http Authenticated API transport used for protected operations.
 * @param defaultAccountId Optional default account retained for consistency with other resources.
 * @param logger SDK logger; secrets are never logged.
 * @param publicHttp Unauthenticated transport used for public authentication operations.
 */
class AuthenticationResource internal constructor(
    http: ApiHttpClient,
    defaultAccountId: String? = null,
    logger: Logger = NoOpLogger,
    private val publicHttp: ApiHttpClient = http,
) : BaseResource(http, defaultAccountId, logger) {

    /**
     * Authenticates with `POST /login`.
     *
     * Request body: `{"email":"user@example.com","password":"secret"}`.
     *
     * Wire operation: `POST /v1/login`.
     *
     * Request body (`application/json`; optional members may be omitted):
     * ```json
     * {
     *   "email": "person@example.com",
     *   "password": "<redacted-value>"
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "access_token": "<redacted-value>",
     *   "user": {"id": "id-placeholder","name": "Example","email": "person@example.com","telephone": null,"government_id": null,"is_email_verified": true,"has_accepted_terms": true,"created_at": "2026-01-01T00:00:00Z","to_be_deleted_at": null},
     *   "accounts": [{"id": "id-placeholder","name": "Example","roles": ["example"],"is_delete_allowed": true,"created_at": "2026-01-01T00:00:00Z"}]
     * }
     * ```
     *
     * @param request Required email and password payload.
     * @return JWT, authenticated user, and accessible accounts.
     * When the user has two-factor authentication enabled, `data` carries `{"mfa_token": "..."}`
     * instead and the SDK throws [MfaRequiredException]; finish with [verifyMfa].
     *
     * @throws ValidationException when the email is invalid or the password is blank.
     * @throws MfaRequiredException when a second factor is required.
     */
    suspend fun login(request: LoginRequest): AuthenticationSession {
        val normalized = request.copy(email = requireValidEmail(request.email))
        requireSecret(normalized.password, "Password")
        return session("Login failed") { publicHttp.post("/login", toJson(normalized)) }
    }

    /**
     * Sends a reset message with `PUT /authentication/request-password-reset`.
     *
     * Request body: `{"email":"user@example.com"}`.
     * Response `data`: `{"email":"user@example.com"}`.
     *
     * Wire operation: `PUT /v1/authentication/request-password-reset`.
     *
     * Request body (`application/json`; optional members may be omitted):
     * ```json
     * {
     *   "email": "person@example.com"
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "email": "person@example.com"
     * }
     * ```
     *
     * @param request Email address that receives the reset message.
     * @return The email address accepted by the API.
     * @throws ValidationException when the email is invalid.
     */
    suspend fun requestPasswordReset(request: RequestPasswordResetRequest): AuthenticationEmailResponse {
        val normalized = request.copy(email = requireValidEmail(request.email))
        return call("Failed to request password reset", AuthenticationEmailResponse::class.java) {
            publicHttp.put("/authentication/request-password-reset", toJson(normalized))
        }
    }

    /**
     * Completes a reset with `PUT /authentication/reset-password`.
     *
     * Request body:
     * `{"email":"user@example.com","new_password":"new-secret","token":"emailed-token"}`.
     * The current schema permits omitting `token`. Response `data`: `{"email":"user@example.com"}`.
     *
     * Wire operation: `PUT /v1/authentication/reset-password`.
     *
     * Request body (`application/json`; optional members may be omitted):
     * ```json
     * {
     *   "email": "person@example.com",
     *   "token": "<redacted-value>",
     *   "new_password": "<redacted-value>"
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "email": "person@example.com"
     * }
     * ```
     *
     * @param request Email, new password, and optional emailed reset token.
     * @return The email address whose password was reset.
     * @throws ValidationException when the email is invalid, the new password is blank, or a supplied token is blank.
     */
    suspend fun resetPassword(request: ResetPasswordRequest): AuthenticationEmailResponse {
        val normalized = request.copy(email = requireValidEmail(request.email))
        requireSecret(normalized.newPassword, "New password")
        normalized.token?.let { requireSecret(it, "Reset token") }
        return call("Failed to reset password", AuthenticationEmailResponse::class.java) {
            publicHttp.put("/authentication/reset-password", toJson(normalized))
        }
    }

    /**
     * Changes the authenticated user's password with `PUT /authentication/change-password`.
     *
     * Request body:
     * `{"email":"user@example.com","password":"old-secret","new_password":"new-secret"}`.
     * Response `data`: `{"email":"user@example.com"}`.
     *
     * Wire operation: `PUT /v1/authentication/change-password`.
     *
     * Request body (`application/json`; optional members may be omitted):
     * ```json
     * {
     *   "email": "person@example.com",
     *   "password": "<redacted-value>",
     *   "new_password": "<redacted-value>"
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "email": "person@example.com"
     * }
     * ```
     *
     * @param request Email, current password, and replacement password.
     * @return The email address whose password was changed.
     * @throws ValidationException when the email is invalid or either password is blank.
     */
    suspend fun changePassword(request: ChangePasswordRequest): AuthenticationEmailResponse {
        val normalized = request.copy(email = requireValidEmail(request.email))
        requireSecret(normalized.password, "Password")
        requireSecret(normalized.newPassword, "New password")
        return call("Failed to change password", AuthenticationEmailResponse::class.java) {
            http.put("/authentication/change-password", toJson(normalized))
        }
    }

    /**
     * Exchanges a provider token with `POST /authentication/social-login`.
     *
     * Request body:
     * `{"provider":"google","token":"provider-token","has_accepted_terms":true}`.
     * Response `data` has the same complete `access_token`, `user`, and `accounts` shape documented by [login].
     *
     * Wire operation: `POST /v1/authentication/social-login`.
     *
     * Request body (`application/json`; optional members may be omitted):
     * ```json
     * {
     *   "provider": "google",
     *   "token": "<redacted-value>",
     *   "has_accepted_terms": true
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "access_token": "<redacted-value>",
     *   "user": {"id": "id-placeholder","name": "Example","email": "person@example.com","telephone": null,"government_id": null,"is_email_verified": true,"has_accepted_terms": true,"created_at": "2026-01-01T00:00:00Z","to_be_deleted_at": null},
     *   "accounts": [{"id": "id-placeholder","name": "Example","roles": ["example"],"is_delete_allowed": true,"created_at": "2026-01-01T00:00:00Z"}]
     * }
     * ```
     *
     * @param request Google provider token and terms acceptance.
     * @return JWT, authenticated user, and accessible accounts.
     * @throws ValidationException when the provider token is blank.
     * @throws MfaRequiredException when a second factor is required.
     */
    suspend fun socialLogin(request: SocialLoginRequest): AuthenticationSession {
        requireSecret(request.token, "Provider token")
        return session("Social login failed") { publicHttp.post("/authentication/social-login", toJson(request)) }
    }

    /**
     * Links a provider identity with `POST /auth/link-social-login`.
     *
     * Request body: `{"provider":"google","token":"provider-token"}`.
     * Response body is the standard success envelope with no `data` payload.
     *
     * Wire operation: `POST /v1/auth/link-social-login`.
     *
     * Request body (`application/json`; optional members may be omitted):
     * ```json
     * {
     *   "provider": "google",
     *   "token": "<redacted-value>"
     * }
     * ```
     *
     * Response 200 body; optional members depend on document state and permissions:
     * ```json
     * {
     *   "status": 200,
     *   "message": "example"
     * }
     * ```
     *
     * @param request Google provider and provider-issued token to link.
     * @throws ValidationException when the provider token is blank.
     */
    suspend fun linkSocialLogin(request: LinkSocialLoginRequest) {
        requireSecret(request.token, "Provider token")
        callVoid("Failed to link social login") {
            http.post("/auth/link-social-login", toJson(request))
        }
    }

    /**
     * Retrieves the masked personal key with `GET /users/api-keys`.
     *
     * Request body: none. Response `data`: `{"api_key":"********suffix"}` or `{"api_key":null}`.
     * A legacy top-level `data: null` response is normalized to `null`.
     *
     * Wire operation: `GET /v1/users/api-keys`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "api_key": null
     * }
     * ```
     *
     * @return Masked key payload, or `null` when the API returns no key payload.
     */
    suspend fun getApiKey(): ApiKeyResponse? {
        val result = callMap("Failed to fetch API key") { http.get("/users/api-keys") }
        return if ("api_key" in result) ApiKeyResponse(result["api_key"] as? String) else null
    }

    /**
     * Creates or rotates the personal key with `POST /users/api-keys`.
     *
     * Request body: `{"password":"secret"}`. Response `data`: `{"api_key":"full-key-shown-once"}`.
     * Generating a new key invalidates the previous key.
     *
     * Wire operation: `POST /v1/users/api-keys`.
     *
     * Request body (`application/json`; optional members may be omitted):
     * ```json
     * {
     *   "password": "<redacted-value>"
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * {
     *   "api_key": null
     * }
     * ```
     *
     * @param request Current password used to authorize key generation.
     * @return Newly generated full API key; [ApiKeyResponse.apiKey] may be `null` only if the server returns it so.
     * @throws ValidationException when the password is blank.
     */
    suspend fun createApiKey(request: CreateApiKeyRequest): ApiKeyResponse {
        requireSecret(request.password, "Password")
        return call("Failed to create API key", ApiKeyResponse::class.java) {
            http.post("/users/api-keys", toJson(request))
        }
    }

    /**
     * Revokes the personal key with `DELETE /users/api-keys`.
     *
     * Request body: none. Response `data` is an empty JSON array.
     * Wire operation: `DELETE /v1/users/api-keys`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`); optional members depend on document state and permissions:
     * ```json
     * []
     * ```
     *
     */
    suspend fun deleteApiKey() {
        callVoid("Failed to delete API key") { http.delete("/users/api-keys") }
    }

    /**
     * Completes a two-factor login by exchanging the challenge from [MfaRequiredException] for a
     * session. The challenge is single-use and expires five minutes after login; an expired, used
     * or over-tried challenge answers `401`.
     *
     * Wire operation: `POST /v1/authentication/mfa/verify` (no credential).
     *
     * Request body (`application/json`):
     * ```json
     * {
     *   "mfa_token": "<redacted-value>",
     *   "code": "123456"
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`):
     * ```json
     * {
     *   "access_token": "<redacted-value>",
     *   "user": {"id": "id-placeholder","name": "Example","email": "person@example.com","telephone": null,"government_id": null,"is_email_verified": true,"has_accepted_terms": true,"created_at": "2026-01-01T00:00:00Z","to_be_deleted_at": null},
     *   "accounts": [{"id": "id-placeholder","name": "Example","roles": ["example"],"is_delete_allowed": true,"created_at": "2026-01-01T00:00:00Z"}]
     * }
     * ```
     *
     * @param request Challenge token and authenticator or recovery code.
     * @return JWT, authenticated user, and accessible accounts.
     * @throws ValidationException when either value is blank.
     */
    suspend fun verifyMfa(request: MfaVerifyRequest): AuthenticationSession {
        requireSecret(request.mfaToken, "MFA token")
        requireSecret(request.code, "MFA code")
        return call("Two-factor verification failed", AuthenticationSession::class.java) {
            publicHttp.post("/authentication/mfa/verify", toJson(request.copy(code = request.code.trim())))
        }
    }

    /**
     * Lists the authenticated user's two-factor methods and remaining recovery codes.
     *
     * Wire operation: `GET /v1/users/self/mfa`.
     *
     * Request body: none.
     *
     * Response 200 `data` payload (inside `{status,message,data}`):
     * ```json
     * {
     *   "methods": [
     *     {"id": "id-placeholder", "type": "totp", "label": "Phone", "confirmed_at": "2026-01-01T00:00:00Z", "last_used_at": "2026-01-01T00:00:00Z"}
     *   ],
     *   "recovery_codes_remaining": 10
     * }
     * ```
     *
     * @return Enrolled methods and the recovery-code balance.
     */
    suspend fun listMfaMethods(): MfaStatus =
        call("Failed to list two-factor methods", MfaStatus::class.java) { http.get("/users/self/mfa") }

    /**
     * Starts authenticator-app enrollment. The returned secret is shown only once; two-factor
     * authentication stays off until [confirmTotpEnrollment] succeeds.
     *
     * Wire operation: `POST /v1/users/self/mfa/totp`.
     *
     * Request body (`application/json`; `label` is optional):
     * ```json
     * {
     *   "label": "Phone"
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`):
     * ```json
     * {
     *   "id": "id-placeholder",
     *   "secret": "<redacted-value>",
     *   "provisioning_uri": "otpauth://totp/Assinafy:person%40example.com?secret=<redacted-value>&issuer=Assinafy"
     * }
     * ```
     *
     * @param label Optional name that tells devices apart.
     * @return Pending method id, shared secret and provisioning URI for a QR code.
     */
    suspend fun startTotpEnrollment(label: String? = null): TotpEnrollment {
        val body = label?.trim()?.takeIf { it.isNotEmpty() }?.let { mapOf("label" to it) } ?: emptyMap()
        return call("Failed to start two-factor enrollment", TotpEnrollment::class.java) {
            http.post("/users/self/mfa/totp", toJson(body))
        }
    }

    /**
     * Activates a pending authenticator method with one live code from the new device. From then on
     * every login needs a second factor. Replacing an already confirmed method also needs
     * re-authentication through `password` or `reauth_code`; first-time enrollment needs neither.
     *
     * Wire operation: `PUT /v1/users/self/mfa/totp/confirm`.
     *
     * Request body (`application/json`; `password` and `reauth_code` are optional):
     * ```json
     * {
     *   "id": "id-placeholder",
     *   "code": "123456",
     *   "password": "<redacted-value>",
     *   "reauth_code": "<redacted-value>"
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`); the codes are shown only once:
     * ```json
     * {
     *   "recovery_codes": ["ABCD-EFGH-JKMN"]
     * }
     * ```
     *
     * @param request Pending method id, new-device code and optional re-authentication.
     * @return Recovery codes to show the user once.
     * @throws ValidationException when the id or code is blank.
     */
    suspend fun confirmTotpEnrollment(request: ConfirmTotpRequest): MfaRecoveryCodes {
        requireSecret(request.id, "MFA method ID")
        requireSecret(request.code, "MFA code")
        return call("Failed to confirm two-factor enrollment", MfaRecoveryCodes::class.java) {
            http.put("/users/self/mfa/totp/confirm", toJson(request))
        }
    }

    /**
     * Issues ten fresh recovery codes and invalidates the previous set.
     *
     * Wire operation: `POST /v1/users/self/mfa/recovery-codes`.
     *
     * Request body (`application/json`; send `password` or `code`):
     * ```json
     * {
     *   "password": "<redacted-value>",
     *   "code": "123456"
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`):
     * ```json
     * {
     *   "recovery_codes": ["ABCD-EFGH-JKMN"]
     * }
     * ```
     *
     * @param request Current password, authenticator code or existing recovery code (consumed).
     * @return The new recovery codes.
     * @throws ValidationException when neither proof is supplied.
     */
    suspend fun regenerateRecoveryCodes(request: MfaReauthRequest): MfaRecoveryCodes {
        requireReauth(request)
        return call("Failed to regenerate recovery codes", MfaRecoveryCodes::class.java) {
            http.post("/users/self/mfa/recovery-codes", toJson(request))
        }
    }

    /**
     * Removes an enrolled method. Removing the last one also discards the recovery codes.
     *
     * Wire operation: `DELETE /v1/users/self/mfa/{methodId}`.
     *
     * Request body (`application/json`; send `password` or `code`):
     * ```json
     * {
     *   "password": "<redacted-value>",
     *   "code": "123456"
     * }
     * ```
     *
     * Response 200 `data` payload (inside `{status,message,data}`):
     * ```json
     * {
     *   "is_mfa_enabled": false
     * }
     * ```
     *
     * @param methodId Method identifier from [listMfaMethods].
     * @param request Current password, authenticator code or existing recovery code (consumed).
     * @return Whether two-factor authentication remains enabled.
     * @throws ValidationException when the id is blank or neither proof is supplied.
     */
    suspend fun removeMfaMethod(methodId: String, request: MfaReauthRequest): MfaRemoval {
        val path = "/users/self/mfa/" + pathSegment(requireId(methodId, "MFA method ID"))
        requireReauth(request)
        return call("Failed to remove two-factor method", MfaRemoval::class.java) {
            http.delete(path, toJson(request))
        }
    }

    private suspend fun session(label: String, request: suspend () -> HttpRawResponse): AuthenticationSession {
        val data = call(label, JsonObject::class.java, request)
        data.get("mfa_token")?.takeUnless { it.isJsonNull }?.asString?.let { throw MfaRequiredException(it) }
        return runCatching { ResponseHandler.fromJson(data, AuthenticationSession::class.java) }
            .getOrElse { throw ResponseHandler.toSdkException(it, label) }
    }

    private fun requireReauth(request: MfaReauthRequest) {
        if (request.password.isNullOrBlank() && request.code.isNullOrBlank()) {
            throw ValidationException("Password or two-factor code is required")
        }
    }

    private fun requireSecret(value: String, name: String) {
        if (value.isBlank()) throw ValidationException("$name is required")
    }
}
