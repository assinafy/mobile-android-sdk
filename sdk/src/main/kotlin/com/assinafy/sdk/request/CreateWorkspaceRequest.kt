package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * Body for `POST /accounts`.
 *
 * @property name Required account display name.
 * @property primaryColor Legacy six-digit primary branding color.
 * @property secondaryColor Legacy six-digit secondary branding color.
 * @property notificationSenderType Notification sender identity, `User` or `Account`.
 */
data class CreateWorkspaceRequest(
    @SerializedName("name") val name: String,
    @Deprecated("Not in the current OpenAPI create schema; retained for live compatibility")
    @SerializedName("primary_color") val primaryColor: String? = null,
    @Deprecated("Not in the current OpenAPI create schema; retained for live compatibility")
    @SerializedName("secondary_color") val secondaryColor: String? = null,
    @SerializedName("notification_sender_type") val notificationSenderType: String? = null,
)
