package com.assinafy.sdk.models

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/** A contract's terms or a document linked as an amendment, renewal or termination. */
sealed interface ContractRecord {
    /** Document whose contract or amendment was read. */
    val documentId: String

    /** Contract or amendment status; see the concrete record type. */
    val status: String

    /** Version timestamp to send as the next write's optimistic precondition. */
    val updatedAt: String?
}

/**
 * Identifier for a contract in another system; sending a reference list replaces all references.
 * @property system System name (letters, digits, underscore, hyphen or dot), stored in lower case.
 * @property id External identifier, preserved as supplied.
 */
data class ContractExternalReference(
    @SerializedName("system") val system: String,
    @SerializedName("id") val id: String,
)

/**
 * Terms tracked on a document, independently of its signature workflow.
 * @property documentId Contract document identifier.
 * @property status A [com.assinafy.sdk.ContractStatus] value.
 * @property resource API resource discriminator; may be omitted in lists.
 * @property counterpartyName Counterparty display name.
 * @property counterpartyTaxpayerId Normalized CPF or CNPJ.
 * @property value Exact monetary amount, with at most two decimal places.
 * @property currency Three-letter currency; defaults to BRL when a value is supplied.
 * @property startDate Start date, YYYY-MM-DD.
 * @property endDate End date, YYYY-MM-DD.
 * @property autoRenew Whether the lifecycle job renews instead of expiring the contract.
 * @property renewalTermMonths Renewal duration; the server uses 12 when unset.
 * @property noticePeriodDays Days before the end date to notify the owner.
 * @property term Current term, starting at one and incremented on renewal.
 * @property ownerId Responsible workspace member, or null when unassigned.
 * @property metadata Custom plain-text fields.
 * @property externalReferences External system identifiers.
 * @property document Document included when list expansion is requested.
 * @property amendments Linked instruments included by the single-contract and family reads.
 * @property createdAt Creation timestamp.
 * @property updatedAt Version timestamp for optimistic concurrency.
 */
data class DocumentContract(
    @SerializedName("document_id") override val documentId: String,
    @SerializedName("status") override val status: String,
    @SerializedName("resource") val resource: String? = null,
    @SerializedName("counterparty_name") val counterpartyName: String? = null,
    @SerializedName("counterparty_taxpayer_id") val counterpartyTaxpayerId: String? = null,
    @SerializedName("value") val value: BigDecimal? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("auto_renew") val autoRenew: Boolean? = null,
    @SerializedName("renewal_term_months") val renewalTermMonths: Int? = null,
    @SerializedName("notice_period_days") val noticePeriodDays: Int? = null,
    @SerializedName("term") val term: Int? = null,
    @SerializedName("owner_id") val ownerId: String? = null,
    @SerializedName("metadata") val metadata: Map<String, String>? = null,
    @SerializedName("external_references") val externalReferences: List<ContractExternalReference>? = null,
    @SerializedName("document") val document: DocumentDetails? = null,
    @SerializedName("amendments") val amendments: List<DocumentContractAmendment>? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") override val updatedAt: String? = null,
) : ContractRecord

/**
 * A signed instrument's link and delta, applied to its root contract in certification order.
 * @property documentId Instrument document identifier.
 * @property status A [com.assinafy.sdk.ContractAmendmentStatus] value.
 * @property parentDocumentId Root contract document identifier.
 * @property relationType A [com.assinafy.sdk.ContractRelationType] value.
 * @property resource API resource discriminator; may be omitted when nested.
 * @property documentName Instrument document name.
 * @property number Application sequence number; null until applied.
 * @property documentStatus Instrument's separate signing/processing status.
 * @property applyError Reason a signed instrument has not applied, when blocked.
 * @property currency Currency of the delta and changes, frozen when applied.
 * @property terms Only the terms this instrument changes; JSON retains exact numeric values.
 * @property changes Changed fields as objects containing from/to; preview until applied.
 * @property appliedAt Application timestamp, or null while pending.
 * @property createdAt Link creation timestamp.
 * @property updatedAt Link version timestamp for optimistic concurrency.
 */
data class DocumentContractAmendment(
    @SerializedName("document_id") override val documentId: String,
    @SerializedName("status") override val status: String,
    @SerializedName("parent_document_id") val parentDocumentId: String,
    @SerializedName("relation_type") val relationType: String,
    @SerializedName("resource") val resource: String? = null,
    @SerializedName("document_name") val documentName: String? = null,
    @SerializedName("number") val number: Int? = null,
    @SerializedName("document_status") val documentStatus: String? = null,
    @SerializedName("apply_error") val applyError: String? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("terms") val terms: JsonObject? = null,
    @SerializedName("changes") val changes: JsonObject? = null,
    @SerializedName("applied_at") val appliedAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") override val updatedAt: String? = null,
) : ContractRecord
