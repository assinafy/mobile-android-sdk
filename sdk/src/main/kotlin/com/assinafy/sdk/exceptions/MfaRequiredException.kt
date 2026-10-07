package com.assinafy.sdk.exceptions

/**
 * Thrown by `login` and `socialLogin` when the user has two-factor authentication enabled. No
 * session exists yet: pass [mfaToken] and the user's authenticator or recovery code to
 * [com.assinafy.sdk.resources.AuthenticationResource.verifyMfa] within five minutes.
 *
 * @property mfaToken Single-use challenge token; never log it.
 */
class MfaRequiredException(
    val mfaToken: String,
) : AssinafyException("Two-factor authentication required") {
    /** Returns a diagnostic representation with the challenge token redacted. */
    override fun toString(): String = "MfaRequiredException(mfaToken=***)"
}
