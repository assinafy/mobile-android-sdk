package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

data class WebhookSubscription(
    @SerializedName("id") val id: String,
    @SerializedName("url") val url: String,
    @SerializedName("email") val email: String,
    @SerializedName("events") val events: List<String>,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

data class WebhookEventTypeInfo(
    @SerializedName("id") val id: String,
    @SerializedName("description") val description: String,
)

data class WebhookDispatch(
    @SerializedName("id") val id: String,
    @SerializedName("event") val event: String,
    @SerializedName("activity_id") val activityId: Long,
    @SerializedName("endpoint") val endpoint: String? = null,
    @SerializedName("payload") val payload: Map<String, Any>? = null,
    @SerializedName("delivered") val delivered: Boolean,
    @SerializedName("http_status") val httpStatus: Int? = null,
    @SerializedName("response_body") val responseBody: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long? = null,
)

data class WebhookPayload(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("event") val event: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("payload") val payload: Map<String, Any>? = null,
    @SerializedName("account_id") val accountId: String? = null,
)
