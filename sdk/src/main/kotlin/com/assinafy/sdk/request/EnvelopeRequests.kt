package com.assinafy.sdk.request

import com.assinafy.sdk.AssignmentMethod
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/**
 * Documents sent together, in order, with one invitation per signer.
 * @property name Envelope title, 1..255 Unicode characters, without less-than signs.
 * @property signers One to twenty distinct persisted signers; steps are not supported.
 * @property documents One to twenty distinct processed, unassigned documents of the workspace.
 * @property message Optional invitation text.
 * @property maxTotalCredits Upper spending limit; a higher price fails without creating or charging.
 * @property idempotencyKey Caller-generated key, reused only for an explicit retry of this creation.
 */
data class CreateEnvelopeRequest(
    val name: String,
    val signers: List<SignerReference>,
    val documents: List<EnvelopeDocumentRequest>,
    val message: String? = null,
    val maxTotalCredits: BigDecimal? = null,
    val idempotencyKey: String? = null,
)

/**
 * One document and the envelope signers required on it.
 * @property id Processed, unassigned document identifier.
 * @property signerIds Nonempty, distinct subset of envelope signers.
 * @property method Virtual by default, or collect with placed fields.
 * @property entries Collect placements; every document signer must own a field.
 * @property expiresAt ISO 8601 expiration at least one hour ahead; null omits expiration.
 * @property copyReceiverIds Signers who receive only a completed copy of this document.
 */
data class EnvelopeDocumentRequest(
    @SerializedName("id") val id: String,
    @SerializedName("signer_ids") val signerIds: List<String>,
    @SerializedName("method") val method: String = AssignmentMethod.VIRTUAL,
    @SerializedName("entries") val entries: List<AssignmentEntry>? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("copy_receiver_ids") val copyReceiverIds: List<String>? = null,
)

/**
 * Partial envelope update, without sending notifications.
 * @property name New title; null keeps it.
 * @property message New message; null keeps it unless clearMessage is true.
 * @property clearMessage Send explicit null to remove the message.
 * @property documentIds All current document IDs exactly once, in the desired order.
 */
data class UpdateEnvelopeRequest(
    val name: String? = null,
    val message: String? = null,
    val clearMessage: Boolean = false,
    val documentIds: List<String>? = null,
)
