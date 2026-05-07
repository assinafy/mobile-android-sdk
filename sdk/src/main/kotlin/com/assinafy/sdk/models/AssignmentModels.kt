package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

data class AssignmentSummary(
    @SerializedName("signer_count") val signerCount: Int = 0,
    @SerializedName("completed_count") val completedCount: Int = 0,
    @SerializedName("signers") val signers: List<Any> = emptyList(),
)

data class Assignment(
    @SerializedName("id") val id: String,
    @SerializedName("method") val method: String,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("signers") val signers: List<Signer> = emptyList(),
    @SerializedName("copy_receivers") val copyReceivers: List<String>? = null,
    @SerializedName("items") val items: List<Any>? = null,
    @SerializedName("summary") val summary: AssignmentSummary? = null,
    @SerializedName("signing_urls") val signingUrls: Map<String, String>? = null,
)
