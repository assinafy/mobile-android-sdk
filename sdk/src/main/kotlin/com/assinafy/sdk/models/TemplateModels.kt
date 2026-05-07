package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

data class TemplateRole(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
)

data class TemplateListItem(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String,
    @SerializedName("account_id") val accountId: String? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

data class Template(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String,
    @SerializedName("account_id") val accountId: String? = null,
    @SerializedName("roles") val roles: List<TemplateRole>? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String? = null,
)
