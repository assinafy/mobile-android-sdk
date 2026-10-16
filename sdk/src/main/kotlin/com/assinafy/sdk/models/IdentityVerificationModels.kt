package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * A provider reason for sending an identity verification to account review.
 *
 * @property code Provider reason code.
 * @property description Human-readable provider reason.
 */
data class IdentityReviewReason(
    @SerializedName("code") val code: String,
    @SerializedName("description") val description: String,
)

/**
 * Identity registered by the account before capture.
 *
 * @property name Name returned by the provider.
 * @property cpf CPF value returned by the provider.
 */
data class IdentityReviewExpected(
    @SerializedName("name") val name: String? = null,
    @SerializedName("cpf") val cpf: String? = null,
)

/**
 * Identity read from the submitted document.
 *
 * @property type Identity-document type, when available.
 * @property name Name returned by the provider.
 * @property cpf CPF value returned by the provider.
 */
data class IdentityReviewDocument(
    @SerializedName("type") val type: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("cpf") val cpf: String? = null,
)

/**
 * Match outcomes between the registered identity and captured document.
 *
 * @property cpf Whether the CPF matches the registered identity.
 * @property name Name similarity score from 0 to 1.
 * @property birthDate Whether the birth date matches; null when unavailable.
 */
data class IdentityReviewMatches(
    @SerializedName("cpf") val cpf: Boolean? = null,
    @SerializedName("name") val name: Double? = null,
    @SerializedName("birth_date") val birthDate: Boolean? = null,
)

/**
 * One provider verification check, with its optional score and reasons.
 *
 * @property name Name returned by the provider.
 * @property result Provider check result, such as pass, review, skipped, or failure.
 * @property score Optional provider score.
 * @property reasons Provider reason codes for this check.
 */
data class IdentityReviewCheck(
    @SerializedName("name") val name: String,
    @SerializedName("result") val result: String,
    @SerializedName("score") val score: Double? = null,
    @SerializedName("reasons") val reasons: List<String> = emptyList(),
)

/**
 * The account user who decided an identity review.
 *
 * @property id Stable verification or reviewer identifier.
 * @property name Name returned by the provider.
 */
data class IdentityReviewer(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
)

/**
 * Account-facing identity review and evidence summary. Evidence images are available only while the status is InReview.
 *
 * @property id Stable verification or reviewer identifier.
 * @property status Current state; see [com.assinafy.sdk.IdentityVerificationStatus].
 * @property provider Verification provider identifier.
 * @property verificationMethod Identity-document verification method used by this request.
 * @property reasonCodes Ordered provider review reasons; unknown codes retain their own description.
 * @property evidenceAvailable Whether the provider evidence summary could be fetched.
 * @property expected Identity registered before capture, when evidence is available.
 * @property document Identity read from the submitted document, when available.
 * @property matches Identity-match results, when available.
 * @property checks Individual provider checks; empty when evidence is unavailable.
 * @property images Available image names; see [com.assinafy.sdk.IdentityReviewImage]. Empty after a decision.
 * @property rulesetVersion Provider ruleset used to evaluate this session.
 * @property evidenceSha256 SHA-256 digest of the retained evidence summary.
 * @property reviewedBy Account reviewer who approved or rejected the identity.
 * @property reviewedAt ISO-8601 account-decision timestamp.
 * @property reviewNote Optional internal note; never shown to the signer.
 * @property completedAt ISO-8601 completion time, when available.
 */
data class SignerIdentityReview(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String,
    @SerializedName("provider") val provider: String,
    @SerializedName("verification_method") val verificationMethod: String,
    @SerializedName("reason_codes") val reasonCodes: List<IdentityReviewReason> = emptyList(),
    @SerializedName("evidence_available") val evidenceAvailable: Boolean,
    @SerializedName("expected") val expected: IdentityReviewExpected? = null,
    @SerializedName("document") val document: IdentityReviewDocument? = null,
    @SerializedName("matches") val matches: IdentityReviewMatches? = null,
    @SerializedName("checks") val checks: List<IdentityReviewCheck> = emptyList(),
    @SerializedName("images") val images: List<String> = emptyList(),
    @SerializedName("ruleset_version") val rulesetVersion: String? = null,
    @SerializedName("evidence_sha256") val evidenceSha256: String? = null,
    @SerializedName("reviewed_by") val reviewedBy: IdentityReviewer? = null,
    @SerializedName("reviewed_at") val reviewedAt: String? = null,
    @SerializedName("review_note") val reviewNote: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null,
)

/**
 * Current signer-facing verification state. Only an absent or Expired session can be started.
 *
 * @property canStart Whether a provider session can be opened or replaced after expiration.
 * @property status Current state; see [com.assinafy.sdk.IdentityVerificationStatus].
 * @property url Hosted verification URL; keep its access token out of logs.
 * @property expiresAt ISO-8601 capture-link expiration; provider processing or review can finish later.
 * @property completedAt ISO-8601 completion time, when available.
 */
data class SignerIdentityVerification(
    @SerializedName("can_start") val canStart: Boolean,
    @SerializedName("status") val status: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null,
) {
    /** Returns diagnostics without exposing the hosted capture token. */
    override fun toString(): String = "SignerIdentityVerification(url=***)"
}

/**
 * Hosted capture session opened after explicit biometric-processing consent. Treat the URL as a credential.
 *
 * @property id Stable verification or reviewer identifier.
 * @property url Hosted verification URL; keep its access token out of logs.
 * @property expiresAt ISO-8601 capture-link expiration; provider processing or review can finish later.
 */
data class IdentityVerificationSession(
    @SerializedName("id") val id: String,
    @SerializedName("url") val url: String,
    @SerializedName("expires_at") val expiresAt: String? = null,
) {
    /** Returns diagnostics without exposing the hosted capture token. */
    override fun toString(): String = "IdentityVerificationSession(url=***)"
}
