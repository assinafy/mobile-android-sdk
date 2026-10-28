package com.assinafy.sdk.request

import com.assinafy.sdk.ContractField
import com.assinafy.sdk.models.ContractExternalReference
import com.assinafy.sdk.util.ContractValidation
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/**
 * Partial contract update. Null properties are omitted; [clearFields] sends explicit JSON nulls.
 * A pending amendment's null removes a term from its delta. On a root contract null clears the
 * field, resets auto-renew to false, or resets currency to BRL when a value remains.
 * External references replace the whole list. Linking requires a parent and relation on the first
 * write; clearing PARENT_DOCUMENT_ID unlinks a pending instrument. Applied links are read-only.
 * Server-side membership, taxpayer validity, entitlement, lifecycle and link rules still apply.
 *
 * @property status ContractStatus value; expired/terminated can only be supplied on initial creation.
 * @property counterpartyName Counterparty name, up to 255 characters.
 * @property counterpartyTaxpayerId Formatted or unformatted CPF/CNPJ, sent unchanged.
 * @property value Exact nonnegative amount, at most two decimals and 9999999999999.99.
 * @property currency Three-letter currency code, normalized by the server.
 * @property startDate Start date, YYYY-MM-DD, year 1000 or later.
 * @property endDate End date on or after startDate; changing it advances the term.
 * @property autoRenew Whether to renew automatically when the end date passes.
 * @property renewalTermMonths Months per renewal, 1..600; 12 when unset.
 * @property noticePeriodDays Notice lead time, 0..3650 days.
 * @property ownerId Active workspace member responsible for the contract.
 * @property metadata Up to 50 plain-text custom fields; keys 1..40 characters, values at most 500.
 * @property externalReferences Replacement list, at most 20 references; empty clears it.
 * @property parentDocumentId Root or family member to link this instrument to.
 * @property relationType ContractRelationType value; required when first linking.
 * @property updatedAt Last-read contract/link timestamp; stale writes return HTTP 409.
 * @property clearFields Fields sent as explicit null; a field cannot also have a supplied value.
 */
data class UpdateContractRequest(
    @SerializedName("status") val status: String? = null,
    @SerializedName("counterparty_name") val counterpartyName: String? = null,
    @SerializedName("counterparty_taxpayer_id") val counterpartyTaxpayerId: String? = null,
    @SerializedName("value") val value: BigDecimal? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("auto_renew") val autoRenew: Boolean? = null,
    @SerializedName("renewal_term_months") val renewalTermMonths: Int? = null,
    @SerializedName("notice_period_days") val noticePeriodDays: Int? = null,
    @SerializedName("owner_id") val ownerId: String? = null,
    @SerializedName("metadata") val metadata: Map<String, String>? = null,
    @SerializedName("external_references") val externalReferences: List<ContractExternalReference>? = null,
    @SerializedName("parent_document_id") val parentDocumentId: String? = null,
    @SerializedName("relation_type") val relationType: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @Transient val clearFields: Set<ContractField> = emptySet(),
)

/**
 * Renewal preconditions prevent a repeated request from starting two terms.
 * @property term Current term; required unless a nonempty endDate is supplied.
 * @property endDate Explicit new YYYY-MM-DD end date, later than the current end date.
 * @property value Replacement exact amount; omitted by default.
 * @property clearValue Send value:null, clearing the amount; mutually exclusive with value.
 * @property updatedAt Last-read timestamp; a changed contract returns HTTP 409.
 */
data class RenewContractRequest(
    @SerializedName("term") val term: Int? = null,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("value") val value: BigDecimal? = null,
    @Transient val clearValue: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

/**
 * Explicitly close a contract; a terminated contract cannot be renewed.
 * @property reason Optional explanation, at most 500 Unicode characters; the API strips HTML.
 * @property updatedAt Last-read timestamp; a changed contract returns HTTP 409.
 */
data class TerminateContractRequest(
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

/**
 * Contract list filters, combined to narrow results; pagination metadata comes from headers.
 * @property page One-based page.
 * @property perPage Page size, 1..100.
 * @property statuses ContractStatus values, joined with commas (any matching status).
 * @property counterparty Partial counterparty name or exact CPF/CNPJ.
 * @property search Partial document name.
 * @property endDateFrom Inclusive lower end date, YYYY-MM-DD.
 * @property endDateTo Inclusive upper end date, YYYY-MM-DD.
 * @property endingWithinDays End dates from today in Brasilia through 0..3650 days later.
 * @property externalSystem External-reference system.
 * @property externalId External-reference identifier.
 * @property sort end_date or updated_at, optionally prefixed with minus; default -updated_at.
 * @property expandDocument Include each contract's document.
 */
data class ContractListParams(
    val page: Int? = null,
    val perPage: Int? = null,
    val statuses: List<String>? = null,
    val counterparty: String? = null,
    val search: String? = null,
    val endDateFrom: String? = null,
    val endDateTo: String? = null,
    val endingWithinDays: Int? = null,
    val externalSystem: String? = null,
    val externalId: String? = null,
    val sort: String? = null,
    val expandDocument: Boolean = false,
) {
    /** Validate and encode all populated filters with the API's exact query names. */
    fun toQueryMap(): Map<String, Any?> {
        ContractValidation.list(this)
        return ListParams(page = page, perPage = perPage, search = search, sort = sort).toQueryMap() +
            buildMap {
                statuses?.takeIf { it.isNotEmpty() }?.let { put("status", it.joinToString(",")) }
                counterparty?.let { put("counterparty", it) }
                endDateFrom?.let { put("end_date_from", it) }
                endDateTo?.let { put("end_date_to", it) }
                endingWithinDays?.let { put("ending_within_days", it) }
                externalSystem?.let { put("external_system", it) }
                externalId?.let { put("external_id", it) }
                if (expandDocument) put("expand", "document")
            }
    }
}
