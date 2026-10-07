package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * JSON body for `POST /authentication/mfa/verify`.
 *
 * @property mfaToken Challenge from [com.assinafy.sdk.exceptions.MfaRequiredException.mfaToken].
 * @property code 6-digit authenticator code, or a recovery code such as `ABCD-EFGH-JKMN`.
 */
data class MfaVerifyRequest(
    @SerializedName("mfa_token") val mfaToken: String,
    @SerializedName("code") val code: String,
) {
    /** Returns a diagnostic representation with both values redacted. */
    override fun toString(): String = "MfaVerifyRequest(mfaToken=***, code=***)"
}

/**
 * JSON body for `PUT /users/self/mfa/totp/confirm`.
 *
 * @property id Pending method identifier from [com.assinafy.sdk.models.TotpEnrollment.id].
 * @property code Live code from the new device.
 * @property password Current password; needed only when replacing a confirmed method.
 * @property reauthCode Code from the current device, or a recovery code; alternative to [password]
 *   when replacing a confirmed method.
 */
data class ConfirmTotpRequest(
    @SerializedName("id") val id: String,
    @SerializedName("code") val code: String,
    @SerializedName("password") val password: String? = null,
    @SerializedName("reauth_code") val reauthCode: String? = null,
) {
    /** Returns a diagnostic representation with every secret redacted. */
    override fun toString(): String = "ConfirmTotpRequest(id=$id, code=***, " +
        "password=${if (password == null) "null" else "***"}, reauthCode=${if (reauthCode == null) "null" else "***"})"
}

/**
 * Re-authentication proof for `POST /users/self/mfa/recovery-codes` and
 * `DELETE /users/self/mfa/{methodId}`. Supply [password] or [code].
 *
 * @property password Current password.
 * @property code Live 6-digit authenticator code, or an existing recovery code (consumed).
 */
data class MfaReauthRequest(
    @SerializedName("password") val password: String? = null,
    @SerializedName("code") val code: String? = null,
) {
    /** Returns a diagnostic representation with both values redacted. */
    override fun toString(): String =
        "MfaReauthRequest(password=${if (password == null) "null" else "***"}, code=${if (code == null) "null" else "***"})"
}
