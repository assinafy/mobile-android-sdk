package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * Body for creating an account signer.
 *
 * @property fullName Required signer name.
 * @property email Optional email used for matching and email notifications.
 * @property whatsappPhoneNumber Optional WhatsApp number used for notifications or verification.
 * @property cpf Legacy CPF; sent as `government_id` when [governmentId] is null.
 * @property metadata Legacy caller-defined values omitted by current API deployments.
 * @property governmentId CPF (11 digits) or CNPJ (14 characters, may be alphanumeric); formatting is
 *   accepted and normalized by the API. Required later for a digital-certificate signer.
 */
data class CreateSignerRequest(
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("whatsapp_phone_number") val whatsappPhoneNumber: String? = null,
    @Deprecated("Use governmentId")
    @SerializedName("cpf") val cpf: String? = null,
    @Deprecated("Not part of the current create-signer schema; retained for older deployments")
    @SerializedName("metadata") val metadata: Map<String, Any>? = null,
    @SerializedName("government_id") val governmentId: String? = null,
)
