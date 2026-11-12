package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/**
 * Allowance and consumption in the current billing cycle.
 *
 * @property balance Remaining quantity; null means unlimited.
 * @property limit Cycle limit; null means unlimited or not cycle-limited.
 * @property spent Consumed quantity.
 */
data class UsageBalance(
    @SerializedName("balance") val balance: BigDecimal? = null,
    @SerializedName("limit") val limit: BigDecimal? = null,
    @SerializedName("spent") val spent: BigDecimal? = null,
)

/**
 * Document and credit pools.
 *
 * @property documents Document allowance.
 * @property credits Credit allowance.
 */
data class UsageBalances(
    @SerializedName("documents") val documents: UsageBalance? = null,
    @SerializedName("credits") val credits: UsageBalance? = null,
)

/**
 * Workspace plan and remaining allowances.
 *
 * @property plan Plan name, null if unavailable.
 * @property documentsRenewalDate Next document allowance renewal date.
 * @property balances Current balances.
 */
data class AccountUsage(
    @SerializedName("plan") val plan: String? = null,
    @SerializedName("documents_renewal_date") val documentsRenewalDate: String? = null,
    @SerializedName("balances") val balances: UsageBalances? = null,
)
