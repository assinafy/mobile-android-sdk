package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * Partial body for `PUT /accounts/{accountId}`; omitted properties remain unchanged.
 *
 * @property name Replacement account display name.
 * @property primaryColor Legacy six-digit primary branding color.
 * @property secondaryColor Legacy six-digit secondary branding color.
 * @property notificationSenderType Replacement sender identity, `User` or `Account`.
 */
data class UpdateWorkspaceRequest(
    @SerializedName("name") val name: String? = null,
    @Deprecated("Not in the current OpenAPI update schema; retained for live compatibility")
    @SerializedName("primary_color") val primaryColor: String? = null,
    @Deprecated("Not in the current OpenAPI update schema; retained for live compatibility")
    @SerializedName("secondary_color") val secondaryColor: String? = null,
    @SerializedName("notification_sender_type") val notificationSenderType: String? = null,
)
