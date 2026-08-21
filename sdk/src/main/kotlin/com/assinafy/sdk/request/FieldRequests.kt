package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * JSON body for `POST /accounts/{accountId}/fields`.
 *
 * @property name Human-readable field name.
 * @property type Platform validation type returned by `GET /field-types`.
 * @property regex Optional custom validation expression.
 * @property isRequired Whether a signer must supply a value.
 */
data class CreateFieldRequest(
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("regex") val regex: String? = null,
    @SerializedName("is_required") val isRequired: Boolean? = null,
)

/**
 * Changes sent to `PUT /accounts/{accountId}/fields/{fieldId}`.
 *
 * Null properties are omitted. Use [clearRegex] to send an explicit JSON `null` for `regex`.
 *
 * @property name Replacement field name, or `null` to keep the current name.
 * @property regex Replacement validation expression, or `null` when unchanged.
 * @property clearRegex Whether to remove the current regex by sending `"regex": null`.
 * @property isActive Replacement active state, or `null` to keep the current state.
 */
data class UpdateFieldRequest(
    val name: String? = null,
    val regex: String? = null,
    val clearRegex: Boolean = false,
    val isActive: Boolean? = null,
)

/**
 * Entry in the JSON array sent to `POST /accounts/{accountId}/fields/validate-multiple`.
 *
 * @property fieldId Field definition used to validate [value].
 * @property value JSON-compatible value to validate; it is sent even when `null`.
 */
data class FieldValidationEntry(
    @SerializedName("field_id") val fieldId: String,
    @SerializedName("value") val value: Any?,
)
