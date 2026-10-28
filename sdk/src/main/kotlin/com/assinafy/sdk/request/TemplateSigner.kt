package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * Maps an existing signer or inline email identity to a Signer or CopyReceiver template role.
 *
 * @property roleId Template role identifier from [com.assinafy.sdk.models.Template.roles].
 * @property id Existing signer identifier; creation requires id or email. Estimates omit identity fields.
 * @property verificationMethod A value from [com.assinafy.sdk.VerificationMethod], including identity-document/face verification. Digital certificate
 * requires an entitled account, the existing signer's CPF/CNPJ in `government_id` (set through
 * `signers.update`), and a template without placed Signer-role fields so the generated assignment is virtual;
 * it costs 0.5 credits plus the selected notification.
 * @property notificationMethods Exactly one Email or WhatsApp channel when supplied; it must match
 * Email/WhatsApp OTP verification; biometric verification accepts either and needs an entitled account and signer CPF. Omit to use the Email default.
 * @property step Optional one-based signing step; the API validates signing roles, while copy receivers ignore it.
 * @property email Inline signer email, used when no existing id is supplied.
 * @property name Inline signer full name.
 * @property whatsappPhoneNumber Inline signer WhatsApp number.
 */
data class TemplateSigner(
    @SerializedName("role_id") val roleId: String,
    @SerializedName("id") val id: String? = null,
    @SerializedName("verification_method") val verificationMethod: String? = null,
    @SerializedName("notification_methods") val notificationMethods: List<String>? = null,
    @SerializedName("step") val step: Int? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("whatsapp_phone_number") val whatsappPhoneNumber: String? = null,
)
