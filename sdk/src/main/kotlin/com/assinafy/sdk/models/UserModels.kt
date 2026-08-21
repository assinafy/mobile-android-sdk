package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/** Granularity accepted by `GET /users/self/stats`. */
enum class DocumentStatsGranularity(internal val wireValue: String) {
    /** One row for each of the latest 12 months. */
    MONTHLY("monthly"),

    /** One row for each day in the requested month. */
    DAILY("daily"),
}

/**
 * Query parameters for `GET /users/self/stats`.
 *
 * @property granularity Monthly by default, or daily when a day-by-day series is needed.
 * @property month Target month in `YYYY-MM` form; required for [DocumentStatsGranularity.DAILY].
 */
data class DocumentStatsQuery(
    val granularity: DocumentStatsGranularity? = null,
    val month: String? = null,
)

/**
 * One zero-filled period returned by `GET /users/self/stats`.
 *
 * @property period `YYYY-MM` for monthly results or `YYYY-MM-DD` for daily results.
 * @property documentsUploaded Documents uploaded during the period.
 * @property documentsSent Documents sent for signature during the period.
 * @property signatureRequests Total individual signature requests created during the period.
 * @property signatureRequestsNotificationEmail Requests whose signer was notified by email.
 * @property signatureRequestsNotificationWhatsapp Requests whose signer was notified by WhatsApp.
 * @property signatureRequestsNotificationBypass Requests created without a signer notification.
 * @property signatureRequestsVerificationEmail Requests verified with an email token.
 * @property signatureRequestsVerificationWhatsapp Requests verified with a WhatsApp token.
 * @property signatureRequestsVerificationBypass Requests completed without token verification.
 * @property signatureRequestsVerificationDigitalCertificate Requests verified with an ICP-Brasil certificate.
 * @property signatureRequestsViewed Requests whose document was first viewed during the period.
 * @property signatureRequestsCompleted Individual signer requests completed during the period.
 * @property documentsCertified Documents certified after all required signatures were completed.
 */
data class DocumentStatsRow(
    @SerializedName("period") val period: String,
    @SerializedName("documents_uploaded") val documentsUploaded: Long,
    @SerializedName("documents_sent") val documentsSent: Long,
    @SerializedName("signature_requests") val signatureRequests: Long,
    @SerializedName("signature_requests_notification_email") val signatureRequestsNotificationEmail: Long,
    @SerializedName("signature_requests_notification_whatsapp") val signatureRequestsNotificationWhatsapp: Long,
    @SerializedName("signature_requests_notification_bypass") val signatureRequestsNotificationBypass: Long,
    @SerializedName("signature_requests_verification_email") val signatureRequestsVerificationEmail: Long,
    @SerializedName("signature_requests_verification_whatsapp") val signatureRequestsVerificationWhatsapp: Long,
    @SerializedName("signature_requests_verification_bypass") val signatureRequestsVerificationBypass: Long,
    @SerializedName("signature_requests_verification_digital_certificate")
    val signatureRequestsVerificationDigitalCertificate: Long,
    @SerializedName("signature_requests_viewed") val signatureRequestsViewed: Long,
    @SerializedName("signature_requests_completed") val signatureRequestsCompleted: Long,
    @SerializedName("documents_certified") val documentsCertified: Long,
)

/**
 * Complete notification map returned by `GET` and `PUT /users/self/notification-preferences`.
 * `true` means the authenticated user receives that owner-facing email in every account.
 *
 * @property documentCompleted Every signer signed and the document was certified.
 * @property signerDeclined A signer declined to sign.
 * @property documentCancelled The document was cancelled.
 * @property documentAboutToExpire The signature deadline is approaching.
 * @property documentExpired The signature deadline passed.
 * @property documentExpirationReset The signature deadline was extended.
 * @property documentProcessingFailed An uploaded document could not be processed.
 * @property templateProcessingFailed A template could not be processed.
 * @property signerWhatsappFailed A WhatsApp notification could not be delivered to a signer.
 */
data class NotificationPreferences(
    @SerializedName("DocumentCompleted") val documentCompleted: Boolean,
    @SerializedName("SignerDeclined") val signerDeclined: Boolean,
    @SerializedName("DocumentCancelled") val documentCancelled: Boolean,
    @SerializedName("DocumentAboutToExpire") val documentAboutToExpire: Boolean,
    @SerializedName("DocumentExpired") val documentExpired: Boolean,
    @SerializedName("DocumentExpirationReset") val documentExpirationReset: Boolean,
    @SerializedName("DocumentProcessingFailed") val documentProcessingFailed: Boolean,
    @SerializedName("TemplateProcessingFailed") val templateProcessingFailed: Boolean,
    @SerializedName("SignerWhatsappFailed") val signerWhatsappFailed: Boolean,
)
