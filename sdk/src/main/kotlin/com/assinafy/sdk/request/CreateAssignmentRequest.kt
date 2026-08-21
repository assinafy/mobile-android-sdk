package com.assinafy.sdk.request

import com.assinafy.sdk.AssignmentMethod
import com.google.gson.annotations.SerializedName

/**
 * Rectangle and optional text styling used to place a collect field on a 150-DPI page image.
 *
 * @property left Horizontal position in pixels from the page's left edge.
 * @property top Vertical position in pixels from the page's top edge.
 * @property width Positive field width in pixels.
 * @property height Positive field height in pixels.
 * @property fontSize Positive rendered text size.
 * @property fontFamily Optional font-family name.
 * @property backgroundColor Optional field background color accepted by the API.
 */
data class DisplaySettings(
    @SerializedName("left") val left: Float,
    @SerializedName("top") val top: Float,
    @SerializedName("width") val width: Float,
    @SerializedName("height") val height: Float,
    @SerializedName("fontSize") val fontSize: Float,
    @SerializedName("fontFamily") val fontFamily: String? = null,
    @SerializedName("backgroundColor") val backgroundColor: String? = null,
)

/**
 * One signer field placed within a collect assignment page.
 *
 * @property signerId Existing signer responsible for the field.
 * @property fieldId Account field-definition identifier.
 * @property displaySettings Optional placement; required for visual fields that the signer edits.
 */
data class AssignmentFieldPlacement(
    @SerializedName("signer_id") val signerId: String,
    @SerializedName("field_id") val fieldId: String,
    @SerializedName("display_settings") val displaySettings: DisplaySettings? = null,
)

/**
 * One page and its field placements for a collect-method assignment.
 *
 * @property pageId Document page identifier from document details.
 * @property fields Non-empty fields placed on this page.
 */
data class AssignmentEntry(
    @SerializedName("page_id") val pageId: String,
    @SerializedName("fields") val fields: List<AssignmentFieldPlacement>,
)

/**
 * Request for creating or estimating a virtual or collect assignment.
 *
 * @property method [AssignmentMethod.VIRTUAL] or [AssignmentMethod.COLLECT].
 * @property signers Non-empty signer references; IDs may be omitted only for cost estimation.
 * @property message Optional message shown to signers.
 * @property expiresAt Optional ISO-8601 signature deadline.
 * @property copyReceivers Optional signer IDs for recipients who only receive a copy.
 * @property entries Page/field placements required for collect assignments and omitted for virtual assignments.
 */
data class CreateAssignmentRequest(
    @SerializedName("method") val method: String = AssignmentMethod.VIRTUAL,
    @SerializedName("signers") val signers: List<SignerReference>,
    @SerializedName("message") val message: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("copy_receivers") val copyReceivers: List<String>? = null,
    @SerializedName("entries") val entries: List<AssignmentEntry>? = null,
)
