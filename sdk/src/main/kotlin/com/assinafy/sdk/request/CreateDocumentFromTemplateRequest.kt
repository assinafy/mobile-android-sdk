package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

data class CreateDocumentFromTemplateRequest(
    @SerializedName("signers") val signers: List<TemplateSigner>,
    @SerializedName("name") val name: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("editor_fields") val editorFields: List<Any>? = null,
)
