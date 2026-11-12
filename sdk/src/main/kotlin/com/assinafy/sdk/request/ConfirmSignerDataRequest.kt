package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * Signer contact confirmation and optional terms acceptance using a signer access code.
 *
 * @property fullName Legacy extension; update the workspace signer for persisted identity changes.
 * @property email Confirmed email address.
 * @property governmentId Legacy extension; update the workspace signer for persisted identity changes.
 * @property whatsappPhoneNumber Confirmed WhatsApp contact number.
 * @property hasAcceptedTerms Whether the signer explicitly accepted the terms.
 */
data class ConfirmSignerDataRequest(
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("government_id") val governmentId: String? = null,
    @SerializedName("whatsapp_phone_number") val whatsappPhoneNumber: String? = null,
    @SerializedName("has_accepted_terms") val hasAcceptedTerms: Boolean? = null,
)
