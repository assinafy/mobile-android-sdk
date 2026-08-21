package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * Body for creating an account signer.
 *
 * @property fullName Required signer name.
 * @property email Optional email used for matching and email notifications.
 * @property whatsappPhoneNumber Optional WhatsApp number used for notifications or verification.
 * @property cpf Legacy CPF field omitted by current API deployments.
 * @property metadata Legacy caller-defined values omitted by current API deployments.
 */
data class CreateSignerRequest(
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("whatsapp_phone_number") val whatsappPhoneNumber: String? = null,
    @Deprecated("Not part of the current create-signer schema; use governmentId on update")
    @SerializedName("cpf") val cpf: String? = null,
    @Deprecated("Not part of the current create-signer schema; retained for older deployments")
    @SerializedName("metadata") val metadata: Map<String, Any>? = null,
)
