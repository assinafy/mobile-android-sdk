package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * A workspace-scoped label that can be attached to documents and templates.
 *
 * When returned inline on a document or template (under `tags`), only [id], [name], and [color]
 * are populated; the timestamps are present on the dedicated tag endpoints.
 */
data class Tag(
    @SerializedName("resource") val resource: String? = null,
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("color") val color: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)
