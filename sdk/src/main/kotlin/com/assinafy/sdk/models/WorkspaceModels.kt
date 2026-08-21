package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * An Assinafy account, also called a workspace by this SDK.
 *
 * @property resource API resource discriminator.
 * @property id Stable account identifier.
 * @property name Account display name.
 * @property primaryColor Legacy six-digit primary branding color.
 * @property secondaryColor Legacy six-digit secondary branding color.
 * @property notificationSenderType Sender identity used for account notifications.
 * @property isDeleteAllowed Whether the authenticated user may delete the account when included.
 * @property roles Authenticated user's roles when included by the endpoint projection.
 * @property createdAt ISO-8601 creation timestamp.
 */
data class Workspace(
    @SerializedName("resource") val resource: String? = null,
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("primary_color") val primaryColor: String? = null,
    @SerializedName("secondary_color") val secondaryColor: String? = null,
    @SerializedName("notification_sender_type") val notificationSenderType: String? = null,
    @SerializedName("is_delete_allowed") val isDeleteAllowed: Boolean? = null,
    @SerializedName("roles") val roles: List<String>? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

/** List and single-account endpoints return the same account shape. */
typealias WorkspaceListItem = Workspace

/**
 * An account's branding theme (`GET /accounts/{accountId}/theme`).
 *
 * @property accountName Account name presented with the theme.
 * @property primaryColor Hex color without a leading `#` (e.g. `2072b9`).
 * @property secondaryColor Optional secondary hex color without a leading `#`.
 * @property logo URL to the account logo, or `null` when no logo is set. Fetch the bytes with
 *   [com.assinafy.sdk.resources.WorkspaceResource.getLogo].
 */
data class AccountTheme(
    @SerializedName("account_name") val accountName: String? = null,
    @SerializedName("primary_color") val primaryColor: String? = null,
    @SerializedName("secondary_color") val secondaryColor: String? = null,
    @SerializedName("logo") val logo: String? = null,
)
