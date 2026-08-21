package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * One email or WhatsApp delivery attempt associated with an assignment signer.
 *
 * @property event Notification event identifier.
 * @property status Delivery state.
 * @property errorCode Provider error code for a failed delivery.
 * @property errorMessage Provider error description for a failed delivery.
 * @property sentAt ISO-8601 successful-send time.
 * @property failedAt ISO-8601 failure time.
 */
data class NotificationHistoryEntry(
    @SerializedName("event") val event: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("error_code") val errorCode: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null,
    @SerializedName("sent_at") val sentAt: String? = null,
    @SerializedName("failed_at") val failedAt: String? = null,
)

/**
 * A signer. The base fields ([id], [fullName], [email], [whatsappPhoneNumber], [cpf],
 * [hasAcceptedTerms], [metadata]) are returned by the signer endpoints. When a signer appears
 * inside `assignment.signers`, the API also populates the assignment-context fields
 * ([completed], [verificationMethod], [notificationMethods], [step], [notified]); those are `null`
 * outside an assignment context.
 *
 * @property id Stable signer identifier.
 * @property fullName Signer's full name.
 * @property email Signer's email address.
 * @property whatsappPhoneNumber Signer's WhatsApp number.
 * @property cpf Legacy CPF value returned by older deployments.
 * @property governmentId Current government-issued identifier.
 * @property hasAcceptedTerms Whether the signer accepted the terms.
 * @property metadata Legacy caller-defined metadata.
 * @property completed Whether this signer completed the enclosing assignment.
 * @property verificationMethod Identity-verification channel for the assignment.
 * @property notificationMethods Channels used to notify the signer.
 * @property step One-based sequential-signing step.
 * @property notified Whether an assignment notification was sent.
 * @property notificationHistory Delivery attempts for the assignment.
 * @property hasSignature Whether a stored signature image exists.
 * @property hasInitial Whether a stored initials image exists.
 * @property isSignatureReusable Whether the signer permits stored-signature reuse.
 * @property resource API resource discriminator when returned by a single-resource endpoint.
 */
data class Signer(
    @SerializedName("id") val id: String,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("whatsapp_phone_number") val whatsappPhoneNumber: String? = null,
    @SerializedName("cpf") val cpf: String? = null,
    @SerializedName("government_id") val governmentId: String? = null,
    @SerializedName("has_accepted_terms") val hasAcceptedTerms: Boolean? = null,
    @SerializedName("metadata") val metadata: Map<String, Any>? = null,
    // Assignment-context fields (present only inside assignment.signers):
    @SerializedName("completed") val completed: Boolean? = null,
    @SerializedName("verification_method") val verificationMethod: String? = null,
    @SerializedName("notification_methods") val notificationMethods: List<String>? = null,
    @SerializedName("step") val step: Int? = null,
    @SerializedName("notified") val notified: Boolean? = null,
    @SerializedName("notification_history") val notificationHistory: List<NotificationHistoryEntry>? = null,
    @SerializedName("has_signature") val hasSignature: Boolean? = null,
    @SerializedName("has_initial") val hasInitial: Boolean? = null,
    @SerializedName("is_signature_reusable") val isSignatureReusable: Boolean? = null,
    @SerializedName("resource") val resource: String? = null,
)
