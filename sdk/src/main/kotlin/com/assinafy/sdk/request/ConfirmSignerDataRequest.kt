package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * Body for [com.assinafy.sdk.resources.DocumentResource.confirmSignerData] — a signer confirming
 * their identity details via their access code. Terms acceptance is a separate API operation.
 *
 * @property fullName Signer's full name as it should appear on the signed document.
 * @property email Confirmed email address.
 * @property governmentId Government-issued CPF or CNPJ recorded with the signature.
 * @property whatsappPhoneNumber Confirmed WhatsApp number (E.164, e.g. `+5548999990000`).
 * @property hasAcceptedTerms Whether the signer accepts the terms.
 */
data class ConfirmSignerDataRequest(
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("government_id") val governmentId: String? = null,
    @Deprecated("Not part of the current API schema; update the account signer before signing")
    @SerializedName("whatsapp_phone_number") val whatsappPhoneNumber: String? = null,
    @Deprecated("Use SignerResource.acceptTerms or SignerDocumentResource.acceptTerms")
    @SerializedName("has_accepted_terms") val hasAcceptedTerms: Boolean? = null,
)
