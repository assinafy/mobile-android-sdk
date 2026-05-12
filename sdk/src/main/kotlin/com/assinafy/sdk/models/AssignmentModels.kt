package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

data class AssignmentSummary(
    @SerializedName("signer_count") val signerCount: Int = 0,
    @SerializedName("completed_count") val completedCount: Int = 0,
    @SerializedName("signers") val signers: List<Signer> = emptyList(),
)

data class SigningUrl(
    @SerializedName("signer_id") val signerId: String,
    @SerializedName("url") val url: String,
)

data class Assignment(
    @SerializedName("resource") val resource: String? = null,
    @SerializedName("id") val id: String,
    @SerializedName("sender_email") val senderEmail: String? = null,
    @SerializedName("method") val method: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("expiration") val expiration: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("signers") val signers: List<Signer> = emptyList(),
    @SerializedName("copy_receivers") val copyReceivers: List<Signer>? = null,
    @SerializedName("items") val items: List<Map<String, Any>>? = null,
    @SerializedName("summary") val summary: AssignmentSummary? = null,
    @SerializedName("signing_urls") val signingUrls: List<SigningUrl>? = null,
)

data class ResendEmailResponse(
    @SerializedName("is_sent") val isSent: Boolean? = null,
    @SerializedName("document_id") val documentId: String? = null,
    @SerializedName("signer_id") val signerId: String? = null,
)
