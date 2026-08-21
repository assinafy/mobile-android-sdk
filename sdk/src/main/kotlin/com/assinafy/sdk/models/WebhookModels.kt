package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * The single webhook subscription configured for an account.
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
