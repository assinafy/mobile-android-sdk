package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

data class DocumentArtifacts(
    @SerializedName("original") val original: String,
    @SerializedName("certificated") val certificated: String? = null,
    @SerializedName("certificate-page") val certificatePage: String? = null,
    @SerializedName("bundle") val bundle: String? = null,
)

data class DocumentPage(
    @SerializedName("id") val id: String,
    @SerializedName("number") val number: Int,
    @SerializedName("height") val height: Int,
    @SerializedName("width") val width: Int,
    @SerializedName("download_url") val downloadUrl: String,
)

data class DocumentActivity(
    @SerializedName("id") val id: Long,
    @SerializedName("event") val event: String,
    @SerializedName("message") val message: String,
    @SerializedName("origin") val origin: String,
    @SerializedName("created_at") val createdAt: Long,
)

data class DocumentListItem(
    @SerializedName("resource") val resource: String = "document",
    @SerializedName("id") val id: String,
    @SerializedName("account_id") val accountId: String? = null,
    @SerializedName("template_id") val templateId: String? = null,
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("is_closed") val isClosed: Boolean? = null,
)

data class DocumentUploadResponse(
    @SerializedName("resource") val resource: String = "document",
    @SerializedName("id") val id: String,
    @SerializedName("account_id") val accountId: String,
    @SerializedName("template_id") val templateId: String? = null,
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String,
    @SerializedName("assignment") val assignment: Any? = null,
    @SerializedName("artifacts") val artifacts: DocumentArtifacts? = null,
    @SerializedName("pages") val pages: List<DocumentPage> = emptyList(),
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("is_closed") val isClosed: Boolean = false,
    @SerializedName("decline_reason") val declineReason: String? = null,
    @SerializedName("declined_by") val declinedBy: Signer? = null,
)

data class DocumentDetails(
    @SerializedName("resource") val resource: String? = null,
    @SerializedName("id") val id: String,
    @SerializedName("account_id") val accountId: String,
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String,
    @SerializedName("assignment") val assignment: Assignment? = null,
    @SerializedName("download_url") val downloadUrl: String? = null,
    @SerializedName("download_final_url") val downloadFinalUrl: String? = null,
    @SerializedName("signing_url") val signingUrl: String? = null,
    @SerializedName("artifacts") val artifacts: DocumentArtifacts? = null,
    @SerializedName("pages") val pages: List<DocumentPage> = emptyList(),
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("is_closed") val isClosed: Boolean = false,
    @SerializedName("decline_reason") val declineReason: String? = null,
    @SerializedName("activities") val activities: List<DocumentActivity>? = null,
)

data class SigningProgress(
    val signed: Int,
    val total: Int,
    val pending: Int,
    val percentage: Double,
)

data class DocumentStatusInfo(
    @SerializedName("code") val code: String,
    @SerializedName("deletable") val deletable: Boolean? = null,
)
