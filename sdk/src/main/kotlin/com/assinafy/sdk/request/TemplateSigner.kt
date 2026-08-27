package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * Maps an existing signer to a role when instantiating a template.
 *
 * @property roleId Template role identifier from [com.assinafy.sdk.models.Template.roles].
 * @property id Existing account signer identifier; required for document creation and omitted from estimates.
 * @property verificationMethod `Email`, `Whatsapp`, or `DigitalCertificate`. Digital certificate
 * requires an entitled account and the existing signer's CPF/CNPJ in `government_id` (set through
 * `signers.update`), and costs two credits plus the selected notification.
 * @property notificationMethods Exactly one Email or WhatsApp channel when supplied; it must match
 * non-certificate verification. Omit to use the Email default.
 * @property step Optional one-based sequential-signing step; if any signer has a step, all must.
 */
data class TemplateSigner(
    @SerializedName("role_id") val roleId: String,
    @SerializedName("id") val id: String? = null,
    @SerializedName("verification_method") val verificationMethod: String? = null,
    @SerializedName("notification_methods") val notificationMethods: List<String>? = null,
    @SerializedName("step") val step: Int? = null,
)
