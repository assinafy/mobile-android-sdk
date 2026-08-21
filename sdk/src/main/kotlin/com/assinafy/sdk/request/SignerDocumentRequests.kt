package com.assinafy.sdk.request

import com.google.gson.annotations.SerializedName

/**
 * One field value submitted by
 * `POST /documents/{documentId}/assignments/{assignmentId}?signer-access-code=...`.
 *
 * @property itemId Assignment-item ID from `GET /sign`'s `assignment.items[].id`.
 * @property fieldId Field ID from `GET /sign`'s `assignment.items[].field.id`.
 * @property pageId Page ID from `GET /sign`'s `assignment.items[].page.id`.
 * @property value String value captured for the field, such as a name, date, or checkbox value.
 */
data class SignAssignmentItemRequest(
    @SerializedName("itemId") val itemId: String,
    @SerializedName("fieldId") val fieldId: String,
    @SerializedName("pageId") val pageId: String,
    @SerializedName("value") val value: String,
)

/**
 * JSON body for `POST /verify?signer-access-code=...`.
 *
 * @property verificationCode One-time password delivered to the signer. It is serialized using
 * the API's exact hyphenated `verification-code` key; the signer access code is a query parameter,
 * never part of this body.
 */
data class VerifySignerEmailRequest(
    @SerializedName("verification-code") val verificationCode: String,
) {
    /** Returns a diagnostic representation with the one-time code redacted. */
    override fun toString(): String = "VerifySignerEmailRequest(verificationCode=***)"
}
