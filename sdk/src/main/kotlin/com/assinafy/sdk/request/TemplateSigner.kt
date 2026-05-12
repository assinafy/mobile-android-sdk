package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

data class TemplateSigner(
    @SerializedName("role_id") val roleId: String,
    @SerializedName("id") val id: String? = null,
    @SerializedName("verification_method") val verificationMethod: String? = null,
    @SerializedName("notification_methods") val notificationMethods: List<String>? = null,
)
