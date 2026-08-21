package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * One line item in an assignment or resend cost estimate.
 *
 * @property code Machine-readable pricing component.
 * @property name Human-readable pricing component.
 * @property cost Total credits charged for this component.
 * @property quantity Number of billable units, when itemized by the API.
 * @property unitCost Credits per unit, when itemized by the API.
 */
data class CostEstimateBreakdownItem(
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("cost") val cost: Double,
    @SerializedName("quantity") val quantity: Int? = null,
    @SerializedName("unit_cost") val unitCost: Double? = null,
)

/**
 * Credit/document estimate returned by assignment pricing endpoints. Legacy resend-only fields are
 * optional because older Assinafy deployments return the smaller `total` response shape.
 *
 * @property documents Number of document units used by the operation.
 * @property credits Credit units used by the operation.
 * @property needsExtraDocument Whether the account must purchase an extra document unit.
 * @property extraDocumentCost Cost of the required extra document unit.
 * @property totalCredits Total credit cost after all components.
 * @property breakdown Itemized pricing components.
 * @property documentBalance Account document-unit balance before the operation.
 * @property creditBalance Account credit balance before the operation.
 * @property hasSufficientResources Whether the account can perform the operation.
 * @property blockingReason Machine-readable reason the operation cannot proceed.
 * @property message Human-readable pricing or resource message.
 * @property legacyTotal Total returned by older resend-cost responses.
 * @property legacyHasSufficientCredits Sufficiency flag returned by older resend-cost responses.
 */
data class CostEstimate(
    @SerializedName("documents") val documents: Int? = null,
    @SerializedName("credits") val credits: Double? = null,
    @SerializedName("needs_extra_document") val needsExtraDocument: Boolean? = null,
    @SerializedName("extra_document_cost") val extraDocumentCost: Double? = null,
    @SerializedName("total_credits") val totalCredits: Double? = null,
    @SerializedName("breakdown") val breakdown: List<CostEstimateBreakdownItem> = emptyList(),
    @SerializedName("document_balance") val documentBalance: Double? = null,
    @SerializedName("credit_balance") val creditBalance: Double? = null,
    @SerializedName("has_sufficient_resources") val hasSufficientResources: Boolean? = null,
    @SerializedName("blocking_reason") val blockingReason: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("total") val legacyTotal: Double? = null,
    @SerializedName("has_sufficient_credits") val legacyHasSufficientCredits: Boolean? = null,
)

/**
 * Aggregate completion state embedded in an assignment.
 *
 * @property signerCount Total signers on the assignment.
 * @property completedCount Signers who have completed the assignment.
 * @property signers Signer records included by deployments that populate the summary list.
 */
data class AssignmentSummary(
    @SerializedName("signer_count") val signerCount: Int = 0,
    @SerializedName("completed_count") val completedCount: Int = 0,
    @SerializedName("signers") val signers: List<Signer> = emptyList(),
)

/**
 * Signing link generated for one assignment signer.
 *
 * @property signerId Signer that owns the link.
 * @property url Public signing URL containing the server-generated access context.
 */
data class SigningUrl(
    @SerializedName("signer_id") val signerId: String,
    @SerializedName("url") val url: String,
)

/**
 * A signer field embedded in an assignment response.
 *
 * [displaySettings] and [value] remain opaque because the API deliberately allows multiple JSON
 * shapes: collect assignments return a placement object, while virtual and legacy assignments may
 * return an empty array or another value. The stable nested resources are strongly typed.
 *
 * @property id Stable assignment-item identifier when included by the response projection.
 * @property page Document page containing the item, or `null` for virtual assignments.
 * @property signer Signer responsible for completing the item when expanded by the API.
 * @property field Reusable field definition, or `null` when the item has no field.
 * @property displaySettings Server-defined rendering metadata.
 * @property value Captured value; its JSON type depends on [field].
 * @property completed Whether the signer has completed the item.
 */
data class AssignmentItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("page") val page: DocumentPage? = null,
    @SerializedName("signer") val signer: Signer? = null,
    @SerializedName("field") val field: FieldDefinition? = null,
    @SerializedName("display_settings") val displaySettings: Any? = null,
    @SerializedName("value") val value: Any? = null,
    @SerializedName("completed") val completed: Boolean = false,
)

/**
 * Signature assignment returned by create, expiration, and document-detail operations.
 *
 * @property resource API resource discriminator.
 * @property id Stable assignment identifier.
 * @property senderEmail Email shown as the request sender.
 * @property method Assignment method, normally `virtual` or `collect`.
 * @property expiresAt ISO-8601 signature deadline.
 * @property expiration Legacy expiration representation returned by older deployments.
 * @property message Optional message shown to signers.
 * @property signers Signers and their assignment-specific state.
 * @property copyReceivers Recipients copied when the assignment completes.
 * @property items Signer fields placed in the assignment.
 * @property summary Aggregate signer completion state.
 * @property signingUrls Generated signing links, normally present on assignment creation.
 */
data class Assignment(
    @SerializedName("resource") val resource: String? = null,
    @SerializedName("id") val id: String,
    @SerializedName("sender_email") val senderEmail: String? = null,
    @SerializedName("method") val method: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("expiration") val expiration: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("signers") val signers: List<Signer> = emptyList(),
    @SerializedName("copy_receivers") val copyReceivers: List<Signer>? = null,
    @SerializedName("items") val items: List<AssignmentItem>? = null,
    @SerializedName("summary") val summary: AssignmentSummary? = null,
    @SerializedName("signing_urls") val signingUrls: List<SigningUrl>? = null,
)

/**
 * Result of resending one signer notification.
 *
 * @property isSent Whether the notification was accepted for delivery.
 * @property documentId Document associated with the notification.
 * @property signerId Signer who receives the notification.
 */
data class ResendEmailResponse(
    @SerializedName("is_sent") val isSent: Boolean? = null,
    @SerializedName("document_id") val documentId: String? = null,
    @SerializedName("signer_id") val signerId: String? = null,
)

/**
 * Action button embedded in a rendered WhatsApp notification.
 *
 * @property text Button label shown to the signer.
 * @property url Optional URL opened by the button.
 */
data class WhatsappNotificationButton(
    @SerializedName("text") val text: String,
    @SerializedName("url") val url: String? = null,
)

/**
 * A rendered WhatsApp message sent for an assignment.
 *
 * @property sentAt Delivery timestamp returned by the API.
 * @property header Rendered message heading.
 * @property body Rendered message content.
 * @property buttons Actions included in the message.
 * @property phoneNumber Destination WhatsApp number.
 * @property signerId Signer associated with the delivery.
 */
data class WhatsappNotification(
    @SerializedName("sent_at") val sentAt: Long? = null,
    @SerializedName("header") val header: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("buttons") val buttons: List<WhatsappNotificationButton> = emptyList(),
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("signer_id") val signerId: String? = null,
)
