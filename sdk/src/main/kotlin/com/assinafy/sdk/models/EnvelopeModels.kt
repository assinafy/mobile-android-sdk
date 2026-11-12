package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * Envelope identity included only when explicitly expanded.
 *
 * @property id Envelope identifier.
 * @property name Envelope title.
 */
data class EnvelopeSummary(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
)

/**
 * A document in an envelope; every document keeps its own lifecycle.
 *
 * @property id Document identifier.
 * @property position One-based document order.
 * @property name Document title.
 * @property status Document status code.
 * @property artifacts Available downloads; list responses contain only a thumbnail.
 * @property signers Signers of this document and their completion state.
 */
data class EnvelopeDocument(
    @SerializedName("id") val id: String,
    @SerializedName("position") val position: Int,
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String,
    @SerializedName("artifacts") val artifacts: DocumentArtifacts? = null,
    @SerializedName("signers") val signers: List<Signer> = emptyList(),
)

/**
 * Documents sent together; an envelope has no status of its own.
 *
 * @property id Envelope identifier.
 * @property name Invitation title.
 * @property message Invitation message.
 * @property documents Documents in signing order.
 * @property counts Counts by document status; null when empty.
 * @property signingUrls One entry per signer; URLs may be null.
 * @property createdAt Creation timestamp.
 * @property resource Resource discriminator.
 */
data class DocumentEnvelope(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("message") val message: String? = null,
    @SerializedName("documents") val documents: List<EnvelopeDocument> = emptyList(),
    @SerializedName("counts") val counts: Map<String, Int>? = null,
    @SerializedName("signing_urls") val signingUrls: List<SigningUrl>? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("resource") val resource: String? = null,
)

/**
 * Outcome for one document when cancelling an envelope.
 *
 * @property id Document identifier.
 * @property status Resulting status.
 * @property cancelled Whether this request cancelled the document.
 */
data class EnvelopeCancellation(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String,
    @SerializedName("cancelled") val cancelled: Boolean,
)

/**
 * Delivery result for one signer reminder.
 *
 * @property isSent Whether every notification channel sent the reminder.
 * @property envelopeId Envelope identifier.
 * @property signerId Signer identifier.
 */
data class EnvelopeReminder(
    @SerializedName("is_sent") val isSent: Boolean,
    @SerializedName("envelope_id") val envelopeId: String,
    @SerializedName("signer_id") val signerId: String,
)
