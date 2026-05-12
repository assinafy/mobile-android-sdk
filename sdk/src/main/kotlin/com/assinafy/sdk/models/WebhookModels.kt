package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

data class WebhookSubscription(
    @SerializedName("url") val url: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("events") val events: List<String> = emptyList(),
    @SerializedName("is_active") val isActive: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

data class WebhookEventTypeInfo(
    @SerializedName("id") val id: String,
    @SerializedName("description") val description: String? = null,
)

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
