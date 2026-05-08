package com.assinafy.sdk.request

import com.assinafy.sdk.AssignmentMethod
import com.google.gson.annotations.SerializedName

data class CreateAssignmentRequest(
    @SerializedName("method") val method: String = AssignmentMethod.VIRTUAL,
    @SerializedName("signers") val signers: List<SignerReference>,
    @SerializedName("message") val message: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("copy_receivers") val copyReceivers: List<String>? = null,
    @SerializedName("entries") val entries: List<Any>? = null,
)
