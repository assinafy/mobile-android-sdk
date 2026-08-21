package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * Authenticated user returned in the `data.user` object by `POST /login` and
 * `POST /authentication/social-login`, and directly as `data` by `GET /users/self`.
 *
 * @property id Stable Assinafy user identifier.
 * @property name User's display name.
 * @property email User's email address.
 * @property telephone Telephone number, or `null` when none is registered.
 * @property governmentId Government-issued identifier, or `null` when none is registered.
 * @property isEmailVerified Whether the user has verified [email].
 * @property hasAcceptedTerms Whether the user has accepted Assinafy's terms.
 * @property createdAt ISO-8601 date-time when the user was created.
 * @property toBeDeletedAt Scheduled ISO-8601 deletion date-time, or `null` when deletion is not scheduled.
 */
data class AuthenticatedUser(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("telephone") val telephone: String? = null,
    @SerializedName("government_id") val governmentId: String? = null,
    @SerializedName("is_email_verified") val isEmailVerified: Boolean,
    @SerializedName("has_accepted_terms") val hasAcceptedTerms: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("to_be_deleted_at") val toBeDeletedAt: String? = null,
)

/**
 * Account membership returned in `data.accounts[]` by login operations.
 *
 * @property id Stable account identifier.
 * @property name Account display name.
 * @property roles Roles the authenticated user has in this account.
 * @property isDeleteAllowed Whether this user may delete the account.
 * @property createdAt ISO-8601 date-time when the account was created.
 */
data class AuthenticatedAccount(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("roles") val roles: List<String>,
    @SerializedName("is_delete_allowed") val isDeleteAllowed: Boolean,
    @SerializedName("created_at") val createdAt: String,
)

/**
 * Successful `data` payload from `POST /login` and `POST /authentication/social-login`.
 *
 * @property accessToken JWT used as the Bearer token for authenticated requests.
 * @property user Authenticated user's complete profile.
 * @property accounts Accounts the authenticated user can access.
 */
data class AuthenticationSession(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("user") val user: AuthenticatedUser,
    @SerializedName("accounts") val accounts: List<AuthenticatedAccount>,
) {
    /** Returns a diagnostic representation with the access token redacted. */
    override fun toString(): String =
        "AuthenticationSession(accessToken=***, user=$user, accounts=$accounts)"
}

/**
 * `data` payload returned by `GET /users/api-keys` and `POST /users/api-keys`.
 *
 * @property apiKey Full key on creation, masked key on retrieval, or `null` when no key exists.
 */
data class ApiKeyResponse(
    @SerializedName("api_key") val apiKey: String?,
) {
    /** Returns a diagnostic representation with the API key redacted. */
    override fun toString(): String = "ApiKeyResponse(apiKey=${if (apiKey == null) "null" else "***"})"
}

/**
 * `data` payload returned by the password request, reset, and change operations.
 *
 * @property email Email address affected by the password operation.
 */
data class AuthenticationEmailResponse(
    @SerializedName("email") val email: String,
)
