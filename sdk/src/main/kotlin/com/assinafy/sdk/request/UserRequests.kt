package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * Partial JSON map for `PUT /users/self/notification-preferences`.
 * Omitted values remain unchanged; at least one property must be supplied.
 *
 * @property documentCompleted Receive email when every signer signed and a document was certified.
 * @property signerDeclined Receive email when a signer declined.
 * @property documentCancelled Receive email when a document was cancelled.
 * @property documentAboutToExpire Receive email when a signature deadline is approaching.
 * @property documentExpired Receive email when a signature deadline passed.
 * @property documentExpirationReset Receive email when a signature deadline was extended.
 * @property documentProcessingFailed Receive email when an uploaded document could not be processed.
 * @property templateProcessingFailed Receive email when a template could not be processed.
 * @property signerWhatsappFailed Receive email when a signer WhatsApp notification failed.
 */
data class UpdateNotificationPreferencesRequest(
    @SerializedName("DocumentCompleted") val documentCompleted: Boolean? = null,
    @SerializedName("SignerDeclined") val signerDeclined: Boolean? = null,
    @SerializedName("DocumentCancelled") val documentCancelled: Boolean? = null,
    @SerializedName("DocumentAboutToExpire") val documentAboutToExpire: Boolean? = null,
    @SerializedName("DocumentExpired") val documentExpired: Boolean? = null,
    @SerializedName("DocumentExpirationReset") val documentExpirationReset: Boolean? = null,
    @SerializedName("DocumentProcessingFailed") val documentProcessingFailed: Boolean? = null,
    @SerializedName("TemplateProcessingFailed") val templateProcessingFailed: Boolean? = null,
    @SerializedName("SignerWhatsappFailed") val signerWhatsappFailed: Boolean? = null,
) {
    internal fun hasChanges(): Boolean = listOf(
        documentCompleted,
        signerDeclined,
        documentCancelled,
        documentAboutToExpire,
        documentExpired,
        documentExpirationReset,
        documentProcessingFailed,
        templateProcessingFailed,
        signerWhatsappFailed,
    ).any { it != null }
}
