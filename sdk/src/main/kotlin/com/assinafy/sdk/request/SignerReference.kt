package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * Reference to a signer within an assignment request.
 *
 * @property id Signer ID. Required when creating an assignment; optional for cost estimation.
 * @property verificationMethod e.g. `"Email"` or `"Whatsapp"`. If omitted the API infers it.
 * @property notificationMethods e.g. `["Email"]`. If omitted the API infers it from [verificationMethod].
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
    companion object {
        fun ofId(signerId: String) = SignerReference(id = signerId)
    }
}
