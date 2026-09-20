package com.assinafy.sdk.request

import com.assinafy.sdk.NotificationMethod
import com.assinafy.sdk.VerificationMethod
import com.google.gson.annotations.SerializedName

/**
 * Reference to a signer within an assignment request.
 *
 * Verification and notification are **coupled**: supply one, both, or neither and the API infers
 * the missing side, defaulting both to Email. Only these pairings are accepted, and anything else
 * is rejected with `400`:
 *
 * | [verificationMethod] | Allowed [notificationMethods] |
 * |---|---|
 * | [VerificationMethod.EMAIL] | [NotificationMethod.EMAIL] |
 * | [VerificationMethod.WHATSAPP] | [NotificationMethod.WHATSAPP] |
 * | [VerificationMethod.DIGITAL_CERTIFICATE] | [NotificationMethod.EMAIL] or [NotificationMethod.WHATSAPP] |
 *
 * @property id Signer ID. Required when creating an assignment; optional for cost estimation.
 * @property verificationMethod One of [VerificationMethod]. Omit to let the API infer it.
 *   [VerificationMethod.DIGITAL_CERTIFICATE] (ICP-Brasil A1/A3) additionally requires an entitled
 *   account, the signer's CPF or CNPJ in `government_id` (set through `signers.update` after
 *   creation), and costs two credits plus the selected notification.
 * @property notificationMethods Exactly one channel from [NotificationMethod]. For non-certificate
 *   verification it must be the matching channel. Omit to use the API's Email default.
 * @property step Positive integer controlling signing order. Signers sharing a step sign in
 *   parallel; a step is activated only after the previous step completes, and each signer is
 *   notified when their step activates. If supplied for any signer it must be supplied for all,
 *   forming a contiguous sequence starting at 1. A digital-certificate signer must be alone in
 *   their step.
 */
data class SignerReference(
    @SerializedName("id") val id: String? = null,
    @SerializedName("verification_method") val verificationMethod: String? = null,
    @SerializedName("notification_methods") val notificationMethods: List<String>? = null,
    @SerializedName("step") val step: Int? = null,
) {
    /** Convenience construction for existing signers. */
    companion object {
        /**
         * Creates the minimal signer reference required to create an assignment.
         *
         * @param signerId Existing account signer identifier.
         * @return A reference with API-inferred verification and notification settings.
         */
        fun ofId(signerId: String) = SignerReference(id = signerId)

        /**
         * Creates a reference that verifies and notifies over a single matching channel.
         *
         * @param signerId Existing account signer identifier.
         * @param channel [NotificationMethod.EMAIL] or [NotificationMethod.WHATSAPP]; used for both
         *   the verification code and the signing invitation.
         * @param step Optional one-based signing step.
         * @return A reference with matching verification and notification channels.
         */
        fun over(signerId: String, channel: String, step: Int? = null) = SignerReference(
            id = signerId,
            verificationMethod = channel,
            notificationMethods = listOf(channel),
            step = step,
        )

        /**
         * Creates a reference for a signer who signs with their own ICP-Brasil A1/A3 certificate.
         *
         * @param signerId Existing account signer identifier; the signer needs a CPF or CNPJ in
         *   `government_id`.
         * @param notifyBy Channel carrying the invitation: [NotificationMethod.EMAIL] (default) or
         *   [NotificationMethod.WHATSAPP].
         * @param step Optional one-based signing step; the signer must be alone in it.
         * @return A reference requesting digital-certificate verification.
         */
        fun withDigitalCertificate(
            signerId: String,
            notifyBy: String = NotificationMethod.EMAIL,
            step: Int? = null,
        ) = SignerReference(
            id = signerId,
            verificationMethod = VerificationMethod.DIGITAL_CERTIFICATE,
            notificationMethods = listOf(notifyBy),
            step = step,
        )
    }
}
