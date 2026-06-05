package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * Body for [com.assinafy.sdk.resources.DocumentResource.confirmSignerData] — a signer confirming
 * their contact details and accepting terms via their access code.
 *
 * @property email Confirmed email address.
 * @property whatsappPhoneNumber Confirmed WhatsApp number (E.164, e.g. `+5548999990000`).
 * @property hasAcceptedTerms Whether the signer accepts the terms.
 */
data class ConfirmSignerDataRequest(
    @SerializedName("email") val email: String? = null,
    @SerializedName("whatsapp_phone_number") val whatsappPhoneNumber: String? = null,
    @SerializedName("has_accepted_terms") val hasAcceptedTerms: Boolean? = null,
)
