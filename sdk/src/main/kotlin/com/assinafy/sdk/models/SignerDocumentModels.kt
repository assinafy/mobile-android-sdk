package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * Signer profile returned by `GET /signers/self?signer-access-code=...`.
 *
 * This is the API's `SignerSelf` response: the ordinary signer identity plus the three
 * signature-state flags computed only for the signer-facing endpoint.
 *
 * @property resource Resource discriminator, normally `signer` when present.
 * @property id Unique signer ID.
 * @property fullName Signer's full name from `full_name`.
 * @property email Signer's email address, or null when the account uses another contact channel.
 * @property whatsappPhoneNumber Normalized E.164 WhatsApp number from `whatsapp_phone_number`.
 * @property hasAcceptedTerms Whether this signer has accepted Assinafy's terms.
 * @property hasSignature Whether a stored signature PNG exists.
 * @property hasInitial Whether a stored initials PNG exists.
 * @property isSignatureReusable Whether the signer allowed the stored images to be reused in
 * future signing processes.
 */
data class SignerSelf(
    @SerializedName("resource") val resource: String? = null,
    @SerializedName("id") val id: String,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("whatsapp_phone_number") val whatsappPhoneNumber: String? = null,
    @SerializedName("has_accepted_terms") val hasAcceptedTerms: Boolean? = null,
    @SerializedName("has_signature") val hasSignature: Boolean? = null,
    @SerializedName("has_initial") val hasInitial: Boolean? = null,
    @SerializedName("is_signature_reusable") val isSignatureReusable: Boolean? = null,
)
