package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * Named signer role defined by a template.
 *
 * @property id Stable role identifier supplied in [com.assinafy.sdk.request.TemplateSigner].
 * @property name Human-readable role name.
 * @property assignmentType Verification or assignment type configured for the role.
 * @property createdAt ISO-8601 creation timestamp.
 * @property updatedAt ISO-8601 last-update timestamp.
 */
data class TemplateRole(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("assignment_type") val assignmentType: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

/**
 * Field configured on a template page.
 *
 * @property id Stable template-field placement identifier.
 * @property fieldId Reusable account field-definition identifier.
 * @property roleId Template role responsible for the value.
 * @property label Optional label shown for the field.
 * @property displaySettings API-defined placement and rendering settings.
 * @property createdAt ISO-8601 creation timestamp.
 * @property updatedAt ISO-8601 last-update timestamp.
 */
data class TemplateFieldPlacement(
    @SerializedName("id") val id: String,
    @SerializedName("field_id") val fieldId: String? = null,
    @SerializedName("role_id") val roleId: String? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("display_settings") val displaySettings: Any? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

/**
 * One page in a template.
 *
 * @property id Stable page identifier.
 * @property number One-based page number.
 * @property height Page image height in pixels.
 * @property width Page image width in pixels.
 * @property downloadUrl Temporary page-image download URL.
 * @property fields Field placements configured on this page when expanded by the endpoint.
 */
data class TemplatePage(
    @SerializedName("id") val id: String,
    @SerializedName("number") val number: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("download_url") val downloadUrl: String? = null,
    @SerializedName("fields") val fields: List<TemplateFieldPlacement>? = null,
)

/**
 * Complete template shape used by list and single-template compatibility endpoints.
 *
 * @property resource API resource discriminator.
 * @property id Stable template identifier.
 * @property name Template display name.
 * @property documentName Default name for documents instantiated from the template.
 * @property message Default signer message.
 * @property status Template processing/readiness status.
 * @property accountId Owning account identifier.
 * @property pages Template pages and field placements when expanded by the endpoint.
 * @property roles Signer roles available when instantiating the template.
 * @property tags Tags assigned to the template itself when included by the endpoint.
 * @property defaultDocumentTags Tags assigned to new documents instantiated from the template.
 * @property createdAt ISO-8601 creation timestamp.
 * @property updatedAt ISO-8601 last-update timestamp.
 */
data class Template(
    @SerializedName("resource") val resource: String? = null,
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("document_name") val documentName: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("status") val status: String,
    @SerializedName("account_id") val accountId: String? = null,
    @SerializedName("pages") val pages: List<TemplatePage>? = null,
    @SerializedName("roles") val roles: List<TemplateRole>? = null,
    @SerializedName("tags") val tags: List<Tag>? = null,
    @SerializedName("default_document_tags") val defaultDocumentTags: List<Tag>? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

/** Template shape returned by account template-list operations. */
typealias TemplateListItem = Template
