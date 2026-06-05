package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * A signer. The base fields ([id], [fullName], [email], [whatsappPhoneNumber], [cpf],
 * [hasAcceptedTerms], [metadata]) are returned by the signer endpoints. When a signer appears
 * inside `assignment.signers`, the API also populates the assignment-context fields
 * ([completed], [verificationMethod], [notificationMethods], [step], [notified]); those are `null`
 * outside an assignment context.
 */
data class Signer(
    @SerializedName("id") val id: String,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("whatsapp_phone_number") val whatsappPhoneNumber: String? = null,
    @SerializedName("cpf") val cpf: String? = null,
    @SerializedName("has_accepted_terms") val hasAcceptedTerms: Boolean? = null,
    @SerializedName("metadata") val metadata: Map<String, Any>? = null,
    // Assignment-context fields (present only inside assignment.signers):
    @SerializedName("completed") val completed: Boolean? = null,
    @SerializedName("verification_method") val verificationMethod: String? = null,
    @SerializedName("notification_methods") val notificationMethods: List<String>? = null,
    @SerializedName("step") val step: Int? = null,
    @SerializedName("notified") val notified: Boolean? = null,
)
