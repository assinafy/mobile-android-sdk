package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

data class RegisterWebhookRequest(
    @SerializedName("url") val url: String,
    @SerializedName("email") val email: String,
    @SerializedName("events") val events: List<String>? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
)
