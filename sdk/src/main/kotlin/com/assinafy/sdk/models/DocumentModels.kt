package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * Download locations exposed for a document.
 *
 * @property original Original uploaded PDF.
 * @property thumbnail Rendered thumbnail image.
 * @property certificated PDF with the Assinafy completion certificate.
 * @property certificatePage Standalone certificate page.
 * @property bundle Archive containing the document artifacts.
 * @property pades PAdES-signed PDF.
 */
data class DocumentArtifacts(
    @SerializedName("original") val original: String? = null,
    @SerializedName("thumbnail") val thumbnail: String? = null,
    @SerializedName("certificated") val certificated: String? = null,
    @SerializedName("certificate-page") val certificatePage: String? = null,
    @SerializedName("bundle") val bundle: String? = null,
    @SerializedName("pades") val pades: String? = null,
)

/**
 * Result of the public document signature-hash verification endpoint.
 *
 * @property hash Hash evaluated by the API.
 * @property id Matching document identifier, when one exists.
 * @property status Current matching document status.
 * @property pageCount Number of pages as returned by the public endpoint.
 * @property signerCount Number of expected signers as returned by the public endpoint.
 * @property completedCount Number of completed signers.
 * @property completedAt Completion timestamp, when signed.
 * @property verifiedAt Timestamp at which verification ran.
 * @property isValid Whether the hash identifies a valid Assinafy document.
 * @property message Human-readable verification result.
 */
data class DocumentVerification(
    @SerializedName("hash") val hash: String,
    @SerializedName("id") val id: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("page_count") val pageCount: String? = null,
    @SerializedName("signer_count") val signerCount: String? = null,
    @SerializedName("completed_count") val completedCount: Int? = null,
    @SerializedName("completed_at") val completedAt: String? = null,
    @SerializedName("verified_at") val verifiedAt: String,
    @SerializedName("is_valid") val isValid: Boolean,
    @SerializedName("message") val message: String,
)

/**
 * Non-sensitive document information returned before a signer authenticates.
 *
 * @property id Stable document identifier.
 * @property name Display name.
 * @property resource API resource discriminator.
 * @property accountId Owning account identifier when included by the endpoint projection.
 * @property templateId Source template identifier, when applicable.
 * @property status Current processing or signing status.
 * @property artifacts Available download locations.
 * @property isClosed Whether further signatures are blocked when included by the endpoint projection.
 * @property signingUrl Public signing URL, when exposed.
 * @property declineReason Reason supplied when the document was declined.
 * @property declinedBy Signer who declined the document.
 * @property tags Attached tags.
 * @property assignment Current signing assignment.
 * @property pages Rendered pages.
 * @property createdAt Creation timestamp.
 * @property updatedAt Last-update timestamp.
 * @property pageCount Page count in the public projection.
 * @property createdBy Identifier of the user who created the document.
 */
data class PublicDocumentInfo(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("resource") val resource: String? = null,
    @SerializedName("account_id") val accountId: String? = null,
    @SerializedName("template_id") val templateId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("artifacts") val artifacts: DocumentArtifacts? = null,
    @SerializedName("is_closed") val isClosed: Boolean? = null,
    @SerializedName("signing_url") val signingUrl: String? = null,
    @SerializedName("decline_reason") val declineReason: String? = null,
    @SerializedName("declined_by") val declinedBy: Signer? = null,
    @SerializedName("tags") val tags: List<Tag>? = null,
    @SerializedName("assignment") val assignment: Assignment? = null,
    @SerializedName("pages") val pages: List<DocumentPage>? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("page_count") val pageCount: Number? = null,
    @SerializedName("created_by") val createdBy: String? = null,
)

/**
 * One rendered page of a document.
 *
 * @property id Stable page identifier.
 * @property number One-based page number.
 * @property height Rendered height in pixels.
 * @property width Rendered width in pixels.
 * @property downloadUrl Direct page download URL, when included by the API.
 */
data class DocumentPage(
    @SerializedName("id") val id: String,
    @SerializedName("number") val number: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("download_url") val downloadUrl: String? = null,
)

/**
 * Audit event recorded for a document.
 *
 * @property id Stable numeric activity identifier.
 * @property event Machine-readable event name.
 * @property message Human-readable event description.
 * @property payload Event-specific JSON payload; its shape depends on [event].
 * @property origin Event-specific origin metadata.
 * @property createdAt Event timestamp.
 */
data class DocumentActivity(
    @SerializedName("id") val id: Long,
    @SerializedName("event") val event: String,
    @SerializedName("message") val message: String? = null,
    @SerializedName("payload") val payload: Any? = null,
    @SerializedName("origin") val origin: Map<String, Any>? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

/** Full document returned by list and search operations. */
typealias DocumentListItem = DocumentDetails

/** Full document returned after a successful multipart upload. */
typealias DocumentUploadResponse = DocumentDetails

/**
 * Complete document representation returned by authenticated document endpoints.
 *
 * @property resource API resource discriminator.
 * @property id Stable document identifier.
 * @property accountId Owning account identifier.
 * @property templateId Source template identifier, when applicable.
 * @property name Display name.
 * @property status Current processing or signing status.
 * @property assignment Current signing assignment.
 * @property downloadUrl Original-document download URL, when included by the API.
 * @property downloadFinalUrl Completed-document download URL, when included by the API.
 * @property signingUrl Signing URL, when included by the API.
 * @property artifacts Available document artifacts.
 * @property tags Attached tags when included by the endpoint projection.
 * @property pages Rendered document pages when included by the endpoint projection.
 * @property createdAt Creation timestamp.
 * @property updatedAt Last-update timestamp.
 * @property isClosed Whether further signatures are blocked.
 * @property declineReason Reason supplied when the document was declined.
 * @property declinedBy Signer who declined the document.
 * @property activities Audit events when expanded by the API.
 */
data class DocumentDetails(
    @SerializedName("resource") val resource: String? = null,
    @SerializedName("id") val id: String,
    @SerializedName("account_id") val accountId: String? = null,
    @SerializedName("template_id") val templateId: String? = null,
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String,
    @SerializedName("assignment") val assignment: Assignment? = null,
    @SerializedName("download_url") val downloadUrl: String? = null,
    @SerializedName("download_final_url") val downloadFinalUrl: String? = null,
    @SerializedName("signing_url") val signingUrl: String? = null,
    @SerializedName("artifacts") val artifacts: DocumentArtifacts? = null,
    @SerializedName("tags") val tags: List<Tag>? = null,
    @SerializedName("pages") val pages: List<DocumentPage>? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("is_closed") val isClosed: Boolean? = null,
    @SerializedName("decline_reason") val declineReason: String? = null,
    @SerializedName("declined_by") val declinedBy: Signer? = null,
    @SerializedName("activities") val activities: List<DocumentActivity>? = null,
)

/**
 * Derived completion progress for a document.
 *
 * @property signed Number of completed signers.
 * @property total Total required signers.
 * @property pending Number of signers still pending.
 * @property percentage Completion percentage from `0.0` through `100.0`.
 */
data class SigningProgress(
    val signed: Int,
    val total: Int,
    val pending: Int,
    val percentage: Double,
)

/**
 * Document status advertised by the API.
 *
 * @property code Machine-readable status code.
 * @property deletable Whether documents in this status may be deleted.
 */
data class DocumentStatusInfo(
    @SerializedName("code") val code: String,
    @SerializedName("deletable") val deletable: Boolean? = null,
)
