package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

data class SignerReference(
    @SerializedName("id") val id: String? = null,
    @SerializedName("verification_method") val verificationMethod: String? = null,
    @SerializedName("notification_methods") val notificationMethods: List<String>? = null,
) {
    companion object {
        fun ofId(signerId: String) = SignerReference(id = signerId)
    }
}
