package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * JSON body for `POST /login`.
 *
 * @property email Email address used to authenticate.
 * @property password User's current password. The SDK never logs or alters this value.
 */
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
) {
    /** Returns a diagnostic representation with the password redacted. */
    override fun toString(): String = "LoginRequest(email=$email, password=***)"
}

/**
 * JSON body for `PUT /authentication/request-password-reset`.
 *
 * @property email Address that receives the password-reset message.
 */
data class RequestPasswordResetRequest(
    @SerializedName("email") val email: String,
)

/**
 * JSON body for `PUT /authentication/reset-password`.
 *
 * @property email Address whose password will be reset.
 * @property newPassword New password to store. The SDK never logs or alters this value.
 * @property token Reset token received by email. The current API schema permits omission.
 */
data class ResetPasswordRequest(
    @SerializedName("email") val email: String,
    @SerializedName("new_password") val newPassword: String,
    @SerializedName("token") val token: String? = null,
) {
    /** Returns a diagnostic representation with the new password and reset token redacted. */
    override fun toString(): String =
        "ResetPasswordRequest(email=$email, newPassword=***, token=${if (token == null) "null" else "***"})"
}

/**
 * JSON body for `PUT /authentication/change-password`.
 *
 * @property email Authenticated user's email address.
 * @property password Current password used to authorize the change.
 * @property newPassword New password to store.
 */
data class ChangePasswordRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("new_password") val newPassword: String,
) {
    /** Returns a diagnostic representation with both passwords redacted. */
    override fun toString(): String = "ChangePasswordRequest(email=$email, password=***, newPassword=***)"
}

/** Social identity provider accepted by the current authentication API. */
enum class SocialLoginProvider {
    /** Google OAuth/OpenID Connect. */
    @SerializedName("google")
    GOOGLE,
}

/**
 * JSON body for `POST /authentication/social-login`.
 *
 * @property provider Social identity provider that issued [token].
 * @property token Provider-issued OAuth access or ID token.
 * @property hasAcceptedTerms Whether the user has accepted Assinafy's terms.
 */
data class SocialLoginRequest(
    @SerializedName("provider") val provider: SocialLoginProvider,
    @SerializedName("token") val token: String,
    @SerializedName("has_accepted_terms") val hasAcceptedTerms: Boolean,
) {
    /** Returns a diagnostic representation with the provider token redacted. */
    override fun toString(): String =
        "SocialLoginRequest(provider=$provider, token=***, hasAcceptedTerms=$hasAcceptedTerms)"
}

/**
 * JSON body for `POST /auth/link-social-login`.
 *
 * @property provider Social identity provider to link.
 * @property token Provider-issued OAuth access or ID token.
 */
data class LinkSocialLoginRequest(
    @SerializedName("provider") val provider: SocialLoginProvider,
    @SerializedName("token") val token: String,
) {
    /** Returns a diagnostic representation with the provider token redacted. */
    override fun toString(): String = "LinkSocialLoginRequest(provider=$provider, token=***)"
}

/**
 * JSON body for `POST /users/api-keys`.
 *
 * @property password Current password used to authorize API-key generation.
 */
data class CreateApiKeyRequest(
    @SerializedName("password") val password: String,
) {
    /** Returns a diagnostic representation with the password redacted. */
    override fun toString(): String = "CreateApiKeyRequest(password=***)"
}
