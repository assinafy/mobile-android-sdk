package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/**
 * A value assigned to one editor field while creating a document from a template.
 *
 * @property fieldId Template editor-field identifier.
 * @property value Replacement value rendered into the document.
 */
data class TemplateEditorField(
    @SerializedName("field_id") val fieldId: String,
    @SerializedName("value") val value: String,
)

/**
 * Document and assignment values used when instantiating a template.
 *
 * @property signers Existing or inline email signers for every Signer role and optional CopyReceiver roles; omit Editor.
 * @property name Optional generated-document name.
 * @property message Optional message shown to signers.
 * @property expiresAt Optional ISO-8601 assignment deadline.
 * @property editorFields Optional values rendered into template editor fields.
 * @property tags Tag names merged with the template's default document tags.
 * @property maxTotalCredits Maximum credits the server may spend; a higher price fails without charging.
 * @property idempotencyKey Stable caller key for retrying this creation explicitly after a lost response.
 */
data class CreateDocumentFromTemplateRequest(
    @SerializedName("signers") val signers: List<TemplateSigner>,
    @SerializedName("name") val name: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("editor_fields") val editorFields: List<TemplateEditorField>? = null,
    @SerializedName("tags") val tags: List<String>? = null,
    @SerializedName("max_total_credits") val maxTotalCredits: BigDecimal? = null,
    @Transient val idempotencyKey: String? = null,
)
