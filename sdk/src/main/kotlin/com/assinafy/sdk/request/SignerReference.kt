package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * Reference to a signer within an assignment request.
 *
 * @property id Signer ID. Required when creating an assignment; optional for cost estimation.
 * @property verificationMethod `"Email"`, `"Whatsapp"`, or `"DigitalCertificate"`. If omitted the
 * API infers Email. Digital certificate requires an entitled account and the signer's CPF/CNPJ in
 * `government_id` (set through `signers.update` after creation), and costs two credits plus the
 * selected notification.
 * @property notificationMethods Email, WhatsApp, or both. For non-certificate verification the
 * matching channel must be included. Omit to use the API's Email default.
 * @property step Positive integer controlling signing order. Signers sharing a step sign in
 *   parallel; a step is activated only after the previous step completes. If supplied for any
 *   signer it must be supplied for all, forming a contiguous sequence starting at 1.
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
    }
}
