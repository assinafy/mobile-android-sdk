package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

data class UpdateSignerRequest(
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("whatsapp_phone_number") val whatsappPhoneNumber: String? = null,
    @SerializedName("cpf") val cpf: String? = null,
)
