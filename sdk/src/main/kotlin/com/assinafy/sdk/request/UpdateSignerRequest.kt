package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * Mutable signer fields accepted by `PUT /accounts/{accountId}/signers/{signerId}`.
 *
 * @property fullName Replacement full name.
 * @property email Replacement email address.
 * @property whatsappPhoneNumber Replacement WhatsApp number.
 * @property cpf Legacy CPF field retained for older deployments.
 * @property governmentId Current government-issued CPF or CNPJ identifier.
 */
data class UpdateSignerRequest(
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("whatsapp_phone_number") val whatsappPhoneNumber: String? = null,
    @Deprecated("Use governmentId; cpf is retained for older deployments")
    @SerializedName("cpf") val cpf: String? = null,
    @SerializedName("government_id") val governmentId: String? = null,
)
