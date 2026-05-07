package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

data class CreateWorkspaceRequest(
    @SerializedName("name") val name: String,
    @SerializedName("primary_color") val primaryColor: String? = null,
    @SerializedName("secondary_color") val secondaryColor: String? = null,
)
