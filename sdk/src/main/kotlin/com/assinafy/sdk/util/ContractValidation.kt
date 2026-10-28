package com.assinafy.sdk.util

import com.assinafy.sdk.ContractRelationType
import com.assinafy.sdk.ContractStatus
import com.assinafy.sdk.exceptions.ValidationException
import com.assinafy.sdk.request.ContractListParams
import com.assinafy.sdk.request.RenewContractRequest
import com.assinafy.sdk.request.TerminateContractRequest
import com.assinafy.sdk.request.UpdateContractRequest
import java.math.BigDecimal

internal object ContractValidation {
    fun update(request: UpdateContractRequest) {
        with(request) {
            check(status == null || status in ContractStatus.ALL, "Unsupported contract status")
            check(relationType == null || relationType in ContractRelationType.ALL, "Unsupported contract relation")
            text(counterpartyName, 255, "Counterparty name")
            text(counterpartyTaxpayerId, 20, "Counterparty taxpayer ID")
            amount(value)
            check(currency == null || Regex("[A-Za-z]{3}").matches(currency), "Currency must contain three letters")
            date(startDate)
            date(endDate)
            check(startDate == null || endDate == null || endDate >= startDate, "End date must not precede start date")
            check(renewalTermMonths == null || renewalTermMonths in 1..600, "Renewal term must be 1..600 months")
            check(noticePeriodDays == null || noticePeriodDays in 0..3650, "Notice period must be 0..3650 days")
            ownerId?.let { ApiValidator.requireNonBlank(it, "Contract owner ID") }
            parentDocumentId?.let { ApiValidator.requireNonBlank(it, "Parent document ID") }
            updatedAt?.let { ApiValidator.requireNonBlank(it, "Contract version timestamp") }
            metadata?.let { values ->
                check(values.size <= 50, "Contract metadata supports at most 50 keys")
                check(values.isEmpty() || values.keys.toList() != (0 until values.size).map(Int::toString), "Metadata keys must describe an object")
                values.forEach { (key, value) ->
                    check(Regex("[A-Za-z0-9_.-]{1,40}").matches(key), "Invalid contract metadata key")
                    text(value, 500, "Metadata value")
                    check(!Regex("<[^>]+>").containsMatchIn(value), "Metadata must be plain text")
                    check(
                        !(key.endsWith("_at") || key == "stuck_since") || value.toBigDecimalOrNull() == null,
                        "Date metadata keys must not contain numeric text",
                    )
                }
            }
            externalReferences?.let { references ->
                check(references.size <= 20, "At most 20 external references are allowed")
                references.forEach { ref ->
                    check(Regex("[A-Za-z0-9_.-]{1,64}").matches(ref.system), "Invalid external reference system")
                    ApiValidator.requireNonBlank(ref.id, "External reference ID")
                    text(ref.id, 255, "External reference ID")
                    check(ref.id.none { it.isSurrogate() }, "External reference IDs must not contain four-byte characters")
                }
            }
        }
    }

    fun renew(request: RenewContractRequest) {
        with(request) {
            check(term != null || !endDate.isNullOrBlank(), "Renewal requires the current term or a new end date")
            check(term == null || term >= 1, "Renewal term must be at least 1")
            if (!endDate.isNullOrEmpty()) date(endDate)
            amount(value)
            check(!clearValue || value == null, "Set value or clearValue, not both")
            updatedAt?.let { ApiValidator.requireNonBlank(it, "Contract version timestamp") }
        }
    }

    fun terminate(request: TerminateContractRequest) {
        text(request.reason, 500, "Termination reason")
        request.updatedAt?.let { ApiValidator.requireNonBlank(it, "Contract version timestamp") }
    }

    fun list(params: ContractListParams) {
        with(params) {
            check(statuses == null || statuses.all { it in ContractStatus.ALL }, "Unsupported contract status filter")
            check(sort == null || sort in SORTS, "Contract sort must be end_date or updated_at, optionally descending")
            date(endDateFrom)
            date(endDateTo)
            check(endingWithinDays == null || endingWithinDays in 0..3650, "Ending window must be 0..3650 days")
        }
    }

    private fun amount(value: BigDecimal?) {
        if (value == null) return
        check(value >= BigDecimal.ZERO && value <= MAX_VALUE, "Contract value must be 0..9999999999999.99")
        check(value.stripTrailingZeros().scale() <= 2, "Contract value supports at most two decimal places")
    }

    private fun text(value: String?, limit: Int, name: String) {
        check(value == null || value.codePointCount(0, value.length) <= limit, "$name must not exceed $limit characters")
    }

    private fun date(value: String?) {
        if (value == null) return
        check(Regex("[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}").matches(value), "Contract dates must be YYYY-MM-DD, from year 1000")
        val year = value.substring(0, 4).toInt()
        val month = value.substring(5, 7).toInt()
        val day = value.substring(8, 10).toInt()
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val days = when (month) {
            2 -> if (leap) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        check(month in 1..12 && day in 1..days, "Contract date does not exist")
    }

    private fun check(condition: Boolean, message: String) {
        if (!condition) throw ValidationException(message)
    }

    private val MAX_VALUE = BigDecimal("9999999999999.99")
    private val SORTS = setOf("end_date", "-end_date", "updated_at", "-updated_at")
}
