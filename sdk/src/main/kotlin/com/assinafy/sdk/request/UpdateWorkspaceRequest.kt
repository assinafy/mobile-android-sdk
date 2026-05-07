package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

data class UpdateWorkspaceRequest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("primary_color") val primaryColor: String? = null,
    @SerializedName("secondary_color") val secondaryColor: String? = null,
)
