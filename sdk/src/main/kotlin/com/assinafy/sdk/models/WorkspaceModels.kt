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
