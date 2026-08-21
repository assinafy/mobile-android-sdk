package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * Reusable field definition returned by the account field endpoints.
 *
 * @property resource API resource discriminator when supplied by the server.
 * @property id Stable field-definition identifier.
 * @property name Human-readable field name.
 * @property type Validation type such as `text`, `cpf`, `email`, or `signature`.
 * @property regex Custom validation expression, or `null` when none is configured.
 * @property isPreDefined Whether Assinafy supplied the definition.
 * @property isActive Whether the definition can be used in new assignments.
 * @property isRequired Whether a signer must provide a value.
 * @property isStandard Whether this is a built-in signature, initial, or signature-date field.
 * @property isReadOnly Whether callers may edit the definition.
 * @property isVisible Whether the field is visible to signers.
 */
data class FieldDefinition(
    @SerializedName("resource") val resource: String? = null,
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("regex") val regex: String? = null,
    @SerializedName("is_pre_defined") val isPreDefined: Boolean? = null,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("is_required") val isRequired: Boolean? = null,
    @SerializedName("is_standard") val isStandard: Boolean? = null,
    @SerializedName("is_read_only") val isReadOnly: Boolean? = null,
    @SerializedName("is_visible") val isVisible: Boolean? = null,
)

/**
 * Supported validation type returned by `GET /field-types`.
 *
 * @property type Machine-readable type sent in field-definition requests.
 * @property name Human-readable localized type name.
 */
data class FieldType(
    @SerializedName("type") val type: String,
    @SerializedName("name") val name: String,
)

/**
 * Result returned by field validation operations.
 *
 * @property fieldId Field-definition identifier; present for each multi-value result and absent for a single value.
 * @property type Validation type applied by the server.
 * @property success Whether the supplied value passed validation.
 * @property errorMessage Empty when [success] is true; otherwise the validation error.
 */
data class FieldValidationResult(
    @SerializedName("field_id") val fieldId: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("success") val success: Boolean,
    @SerializedName("error_message") val errorMessage: String,
)
