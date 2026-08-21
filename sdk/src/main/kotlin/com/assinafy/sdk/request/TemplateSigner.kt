package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * Maps an existing signer to a role when instantiating a template.
 *
 * @property roleId Template role identifier from [com.assinafy.sdk.models.Template.roles].
 * @property id Existing account signer identifier; required for document creation and omitted from estimates.
 * @property verificationMethod Identity-verification channel, such as `Email` or `Whatsapp`.
 * @property notificationMethods Channels used to notify the signer.
 * @property step Optional one-based sequential-signing step; if any signer has a step, all must.
 */
data class TemplateSigner(
    @SerializedName("role_id") val roleId: String,
    @SerializedName("id") val id: String? = null,
    @SerializedName("verification_method") val verificationMethod: String? = null,
    @SerializedName("notification_methods") val notificationMethods: List<String>? = null,
    @SerializedName("step") val step: Int? = null,
)
