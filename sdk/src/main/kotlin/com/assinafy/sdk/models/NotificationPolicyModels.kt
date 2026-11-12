package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * Workspace notification policy. Personal preferences still apply.
 *
 * @property editable Whether this caller can change the policy.
 * @property ownersReceiveAll Whether owners also receive member document pushes.
 * @property events Per-event email and push switches; identity-review email is always sent.
 */
data class NotificationPolicy(
    @SerializedName("editable") val editable: Boolean? = null,
    @SerializedName("owners_receive_all") val ownersReceiveAll: Boolean? = null,
    @SerializedName("events") val events: Map<String, Boolean>? = null,
)
