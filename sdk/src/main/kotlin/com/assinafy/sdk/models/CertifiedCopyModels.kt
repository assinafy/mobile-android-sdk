package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * Availability or delivery result of a signer certified-copy request.
 *
 * @property available Whether another request is allowed.
 * @property notificationMethod Email or Whatsapp, or null when unavailable.
 * @property resendsLeft Remaining WhatsApp resends; null for email.
 * @property sentTo Masked destination, present after delivery.
 */
data class CertifiedCopy(
    @SerializedName("available") val available: Boolean,
    @SerializedName("notification_method") val notificationMethod: String? = null,
    @SerializedName("resends_left") val resendsLeft: Int? = null,
    @SerializedName("sent_to") val sentTo: String? = null,
)
