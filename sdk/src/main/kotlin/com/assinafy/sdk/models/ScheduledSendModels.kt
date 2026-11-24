package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * An assignment or envelope request retained until its send time.
 * @property id Scheduled-send identifier.
 * @property type Request kind from [com.assinafy.sdk.ScheduledSendType].
 * @property documentIds Documents to send; deleted documents are omitted.
 * @property status State from [com.assinafy.sdk.ScheduledSendStatus].
 * @property sendAt ISO-8601 scheduled send time.
 * @property assignmentId Created assignment after a successful assignment send.
 * @property envelopeId Created envelope, or null before sending or after its deletion.
 * @property failureReason Portuguese refusal reason when failed.
 * @property createdAt ISO-8601 creation time.
 * @property processedAt ISO-8601 time of sending, failure or cancellation.
 * @property resource API resource discriminator.
 */
data class ScheduledSend(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("document_ids") val documentIds: List<String>,
    @SerializedName("status") val status: String,
    @SerializedName("send_at") val sendAt: String,
    @SerializedName("assignment_id") val assignmentId: String? = null,
    @SerializedName("envelope_id") val envelopeId: String? = null,
    @SerializedName("failure_reason") val failureReason: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("processed_at") val processedAt: String? = null,
    @SerializedName("resource") val resource: String? = null,
)

/**
 * A sender-declared signer capacity, separate from the signing projection.
 * @property signerId Assignment signer identifier.
 * @property signAs Capacity from [com.assinafy.sdk.SignerCapacity]; defaults to Sign on the server.
 */
data class AssignmentSignerCapacity(
    @SerializedName("signer_id") val signerId: String,
    @SerializedName("sign_as") val signAs: String,
)

/**
 * Delivery attempts for one assignment signer, including delivery and open timestamps.
 * @property signerId Assignment signer identifier.
 * @property notificationHistory Delivery attempts in the order returned by the server.
 */
data class SignerNotificationHistory(
    @SerializedName("signer_id") val signerId: String,
    @SerializedName("notification_history") val notificationHistory: List<NotificationHistoryEntry>,
)
