package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

data class Workspace(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("is_delete_allowed") val isDeleteAllowed: Boolean = false,
    @SerializedName("roles") val roles: List<String> = emptyList(),
    @SerializedName("created_at") val createdAt: String,
)

data class WorkspaceListItem(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("is_delete_allowed") val isDeleteAllowed: Boolean,
    @SerializedName("roles") val roles: List<String> = emptyList(),
    @SerializedName("created_at") val createdAt: String,
)
