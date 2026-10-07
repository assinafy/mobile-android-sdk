package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * The account's oldest webhook endpoint, as read and written by the legacy subscription
 * operations. Accounts with several endpoints use [WebhookEndpoint].
 *
 * @property url Destination that receives event POST requests.
 * @property email Delivery contact address.
 * @property events Subscribed event identifiers.
 * @property isActive Whether Assinafy currently sends matching events.
 * @property updatedAt ISO-8601 last-update timestamp.
 */
data class WebhookSubscription(
    @SerializedName("url") val url: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("events") val events: List<String> = emptyList(),
    @SerializedName("is_active") val isActive: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

/**
 * Webhook event type advertised by the API.
 *
 * @property id Wire identifier supplied in webhook subscription requests.
 * @property description Human-readable event description.
 */
data class WebhookEventTypeInfo(
    @SerializedName("id") val id: String,
    @SerializedName("description") val description: String? = null,
)

/**
 * One recorded webhook delivery attempt.
 *
 * @property resource API resource discriminator.
 * @property id Stable dispatch identifier used by the retry endpoint.
 * @property event Event identifier delivered in the payload.
 * @property activityId Source activity identifier.
 * @property endpointId Endpoint the delivery was sent to; `null` once that endpoint is deleted.
 * @property endpoint Destination used for the attempt.
 * @property payload JSON object sent to the destination.
 * @property delivered Whether the destination accepted the delivery.
 * @property httpStatus Destination HTTP status, when a response was received.
 * @property responseBody Destination response body.
 * @property error Transport or delivery error message.
 * @property createdAt ISO-8601 creation timestamp.
 * @property updatedAt ISO-8601 last-update timestamp.
 */
data class WebhookDispatch(
    @SerializedName("resource") val resource: String? = null,
    @SerializedName("id") val id: String,
    @SerializedName("event") val event: String,
    @SerializedName("activity_id") val activityId: Long? = null,
    @SerializedName("endpoint_id") val endpointId: String? = null,
    @SerializedName("endpoint") val endpoint: String? = null,
    @SerializedName("payload") val payload: Map<String, Any>? = null,
    @SerializedName("delivered") val delivered: Boolean = false,
    @SerializedName("http_status") val httpStatus: Int? = null,
    @SerializedName("response_body") val responseBody: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

/**
 * Webhook delivery envelope parsed by [com.assinafy.sdk.support.WebhookVerifier].
 *
 * @property id Numeric activity/delivery identifier.
 * @property event Current event identifier.
 * @property type Legacy event identifier used by older payloads.
 * @property message Human-readable event description.
 * @property payload Current event-specific data object.
 * @property subject Subject snapshot supplied by the event.
 * @property obj Object snapshot supplied by the event's legacy shape.
 * @property origin Origin snapshot supplied by the event.
 * @property createdAt Unix event timestamp.
 * @property accountId Account that emitted the event.
 */
data class WebhookPayload(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("event") val event: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("payload") val payload: Map<String, Any>? = null,
    @SerializedName("subject") val subject: Map<String, Any>? = null,
    @SerializedName("object") val obj: Map<String, Any>? = null,
    @SerializedName("origin") val origin: Map<String, Any>? = null,
    @SerializedName("created_at") val createdAt: Long? = null,
    @SerializedName("account_id") val accountId: String? = null,
)

/**
 * A URL that receives the account's webhook events. Every active endpoint subscribed to an event
 * receives it independently. An account has 1 endpoint, or up to 3 on paid plans.
 *
 * @property id Endpoint identifier.
 * @property name Label that tells endpoints apart, or `null`.
 * @property url Destination that receives event POST requests.
 * @property email Contact address for delivery-failure notices.
 * @property events Event identifiers delivered to this endpoint.
 * @property isActive Whether events are delivered to this endpoint.
 * @property signingEnabled Whether deliveries carry a `webhook-signature` header.
 * @property createdAt ISO-8601 creation timestamp.
 * @property updatedAt ISO-8601 last-update timestamp.
 */
data class WebhookEndpoint(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("url") val url: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("events") val events: List<String> = emptyList(),
    @SerializedName("is_active") val isActive: Boolean = false,
    @SerializedName("signing_enabled") val signingEnabled: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

/**
 * An endpoint's signing secret, used by [com.assinafy.sdk.support.WebhookVerifier].
 *
 * @property secret Standard Webhooks secret: `whsec_` followed by the base64-encoded key.
 */
data class WebhookEndpointSecret(
    @SerializedName("secret") val secret: String,
) {
    /** Returns a diagnostic representation with the secret redacted. */
    override fun toString(): String = "WebhookEndpointSecret(secret=***)"
}
