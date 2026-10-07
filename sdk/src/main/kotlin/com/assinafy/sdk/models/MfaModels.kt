package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * `data` payload of `GET /users/self/mfa`.
 *
 * @property methods Enrolled two-factor methods.
 * @property recoveryCodesRemaining Unused recovery codes left.
 */
data class MfaStatus(
    @SerializedName("methods") val methods: List<MfaMethod> = emptyList(),
    @SerializedName("recovery_codes_remaining") val recoveryCodesRemaining: Int = 0,
)

/**
 * One enrolled two-factor method.
 *
 * @property id Method identifier, used by `removeMfaMethod`.
 * @property type Method type; see [com.assinafy.sdk.MfaMethodType].
 * @property label Caller-chosen label, or `null`.
 * @property confirmedAt ISO-8601 time the enrollment was confirmed, or `null` while pending.
 * @property lastUsedAt ISO-8601 time of the last successful use, or `null`.
 */
data class MfaMethod(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("confirmed_at") val confirmedAt: String? = null,
    @SerializedName("last_used_at") val lastUsedAt: String? = null,
)

/**
 * `data` payload of `POST /users/self/mfa/totp`. The secret is returned only once.
 *
 * @property id Pending method identifier, passed to `confirmTotpEnrollment`.
 * @property secret Base32 shared secret for manual entry in an authenticator app.
 * @property provisioningUri `otpauth://` URI to render as a QR code.
 */
data class TotpEnrollment(
    @SerializedName("id") val id: String,
    @SerializedName("secret") val secret: String,
    @SerializedName("provisioning_uri") val provisioningUri: String,
) {
    /** Returns a diagnostic representation with the secret and URI redacted. */
    override fun toString(): String = "TotpEnrollment(id=$id, secret=***, provisioningUri=***)"
}

/**
 * `data` payload carrying recovery codes, shown only once.
 *
 * @property recoveryCodes Single-use codes that replace an authenticator code.
 */
data class MfaRecoveryCodes(
    @SerializedName("recovery_codes") val recoveryCodes: List<String> = emptyList(),
) {
    /** Returns a diagnostic representation with the codes redacted. */
    override fun toString(): String = "MfaRecoveryCodes(recoveryCodes=${recoveryCodes.size} redacted)"
}

/**
 * `data` payload of `DELETE /users/self/mfa/{methodId}`.
 *
 * @property isMfaEnabled Whether the user still has a confirmed method.
 */
data class MfaRemoval(
    @SerializedName("is_mfa_enabled") val isMfaEnabled: Boolean,
)
