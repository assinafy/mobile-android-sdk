package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

data class Signer(
    @SerializedName("id") val id: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("whatsapp_phone_number") val whatsappPhoneNumber: String? = null,
    @SerializedName("has_accepted_terms") val hasAcceptedTerms: Boolean? = null,
)
