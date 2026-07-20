package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

data class Workspace(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("primary_color") val primaryColor: String? = null,
    @SerializedName("secondary_color") val secondaryColor: String? = null,
    @SerializedName("is_delete_allowed") val isDeleteAllowed: Boolean = false,
    @SerializedName("roles") val roles: List<String> = emptyList(),
    @SerializedName("created_at") val createdAt: String? = null,
)

data class WorkspaceListItem(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("primary_color") val primaryColor: String? = null,
    @SerializedName("secondary_color") val secondaryColor: String? = null,
    @SerializedName("is_delete_allowed") val isDeleteAllowed: Boolean = false,
    @SerializedName("roles") val roles: List<String> = emptyList(),
    @SerializedName("created_at") val createdAt: String? = null,
)

/**
 * An account's branding theme (`GET /accounts/{accountId}/theme`).
 *
 * @property primaryColor Hex color without a leading `#` (e.g. `2072b9`).
 * @property logo URL to the account logo, or `null` when no logo is set. Fetch the bytes with
 *   [com.assinafy.sdk.resources.WorkspaceResource.getLogo].
 */
data class AccountTheme(
    @SerializedName("account_name") val accountName: String? = null,
    @SerializedName("primary_color") val primaryColor: String? = null,
    @SerializedName("secondary_color") val secondaryColor: String? = null,
    @SerializedName("logo") val logo: String? = null,
)
