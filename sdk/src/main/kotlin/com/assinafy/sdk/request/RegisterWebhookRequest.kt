package com.assinafy.sdk.request

import com.assinafy.sdk.WebhookEvent
import com.google.gson.annotations.SerializedName

/**
 * Request to register/replace the account's webhook subscription.
 *
 * @property url HTTPS (or HTTP) endpoint that will receive event POSTs.
 * @property email Contact email for delivery notifications.
 * @property events Event ids to subscribe to (see [WebhookEvent]). When `null` or empty the SDK
 *   substitutes [DEFAULT_EVENTS].
 * @property isActive Whether the subscription is active on registration.
 */
data class RegisterWebhookRequest(
    @SerializedName("url") val url: String,
    @SerializedName("email") val email: String,
    @SerializedName("events") val events: List<String>? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
) {
    companion object {
        /** Default subscription used when [events] is null/empty. */
        val DEFAULT_EVENTS: List<String> = listOf(
            WebhookEvent.DOCUMENT_READY,
            WebhookEvent.DOCUMENT_PREPARED,
            WebhookEvent.SIGNER_SIGNED_DOCUMENT,
            WebhookEvent.SIGNER_REJECTED_DOCUMENT,
            WebhookEvent.DOCUMENT_PROCESSING_FAILED,
        )
    }
}
