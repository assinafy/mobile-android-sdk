package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * JSON body for `POST /accounts/{accountId}/webhooks/endpoints`.
 *
 * @property url HTTPS (or HTTP) URL that receives the events; unique within the workspace.
 * @property email Contact email for delivery-failure notices.
 * @property events Event identifiers to deliver (see [com.assinafy.sdk.WebhookEvent]).
 * @property name Optional label that tells endpoints apart.
 * @property isActive Whether events are delivered; the API defaults to `true`.
 * @property signingEnabled Sign deliveries with a Standard Webhooks signature; the API defaults to `false`.
 */
data class CreateWebhookEndpointRequest(
    @SerializedName("url") val url: String,
    @SerializedName("email") val email: String,
    @SerializedName("events") val events: List<String>,
    @SerializedName("name") val name: String? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
    @SerializedName("signing_enabled") val signingEnabled: Boolean? = null,
)

/**
 * JSON body for `PUT /accounts/{accountId}/webhooks/endpoints/{endpointId}`. Only non-null
 * fields are sent and updated; at least one is required.
 *
 * @property url New destination URL; unique within the workspace.
 * @property email New contact email for delivery-failure notices.
 * @property events New event selection.
 * @property name New label.
 * @property isActive Enables or pauses delivery.
 * @property signingEnabled `true` generates a secret when the endpoint has none and keeps the
 *   current one otherwise; `false` discards the secret.
 */
data class UpdateWebhookEndpointRequest(
    @SerializedName("url") val url: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("events") val events: List<String>? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
    @SerializedName("signing_enabled") val signingEnabled: Boolean? = null,
)
