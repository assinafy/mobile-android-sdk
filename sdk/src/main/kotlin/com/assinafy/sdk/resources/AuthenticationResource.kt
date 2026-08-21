package com.assinafy.sdk.resources

import com.assinafy.sdk.Logger
import com.assinafy.sdk.NoOpLogger
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.http.ApiHttpClient
import com.assinafy.sdk.models.ApiKeyResponse
import com.assinafy.sdk.models.AuthenticationEmailResponse
import com.assinafy.sdk.models.AuthenticationSession
import com.assinafy.sdk.request.ChangePasswordRequest
import com.assinafy.sdk.request.CreateApiKeyRequest
import com.assinafy.sdk.request.LinkSocialLoginRequest
import com.assinafy.sdk.request.LoginRequest
import com.assinafy.sdk.request.RequestPasswordResetRequest
import com.assinafy.sdk.request.ResetPasswordRequest
import com.assinafy.sdk.request.SocialLoginRequest
import com.assinafy.sdk.util.requireValidEmail

/**
 * Human-user authentication, password management, social identity linking, and personal API-key management.
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
     * Response `data`:
     * ```json
     * {
     *   "access_token": "jwt-token",
     *   "user": {
     *     "id": "user-1", "name": "Example User", "email": "user@example.com",
     *     "telephone": null, "government_id": null, "is_email_verified": true,
     *     "has_accepted_terms": true, "created_at": "2026-01-01T00:00:00Z",
     *     "to_be_deleted_at": null
     *   },
     *   "accounts": [{
     *     "id": "account-1", "name": "Example", "roles": ["Owner"],
     *     "is_delete_allowed": true, "created_at": "2026-01-01T00:00:00Z"
     *   }]
     * }
     * ```
     *
     * @param request Required email and password payload.
     * @return JWT, authenticated user, and accessible accounts.
     * @throws ValidationException when the email is invalid or the password is blank.
     */
    suspend fun login(request: LoginRequest): AuthenticationSession {
        val normalized = request.copy(email = requireValidEmail(request.email))
        requireSecret(normalized.password, "Password")
        return call("Login failed", AuthenticationSession::class.java) {
            publicHttp.post("/login", toJson(normalized))
        }
    }

    /**
     * Sends a reset message with `PUT /authentication/request-password-reset`.
     *
     * Request body: `{"email":"user@example.com"}`.
     * Response `data`: `{"email":"user@example.com"}`.
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
     * @param request Google provider token and terms acceptance.
     * @return JWT, authenticated user, and accessible accounts.
     * @throws ValidationException when the provider token is blank.
     */
    suspend fun socialLogin(request: SocialLoginRequest): AuthenticationSession {
        requireSecret(request.token, "Provider token")
        return call("Social login failed", AuthenticationSession::class.java) {
            publicHttp.post("/authentication/social-login", toJson(request))
        }
    }

    /**
     * Links a provider identity with `POST /auth/link-social-login`.
     *
     * Request body: `{"provider":"google","token":"provider-token"}`.
     * Response body is the standard success envelope with no `data` payload.
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
     */
    suspend fun deleteApiKey() {
        callVoid("Failed to delete API key") { http.delete("/users/api-keys") }
    }

    private fun requireSecret(value: String, name: String) {
        if (value.isBlank()) throw ValidationException("$name is required")
    }
}
