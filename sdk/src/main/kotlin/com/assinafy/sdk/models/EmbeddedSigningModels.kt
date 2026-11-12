package com.assinafy.sdk.models

import com.google.gson.annotations.SerializedName

/**
 * An exact allowed origin for embedded signing.
 *
 * @property id Origin identifier.
 * @property origin Scheme, host and optional port.
 * @property createdAt Creation time.
 */
data class EmbedOrigin(
    @SerializedName("id") val id: String,
    @SerializedName("origin") val origin: String,
    @SerializedName("created_at") val createdAt: String? = null,
)

/**
 * Single-use URL to load in an iframe within five minutes.
 *
 * @property url Credential-bearing URL; never log or prefetch it.
 * @property expiresAt Expiration time.
 */
data class EmbedSession(
    @SerializedName("url") val url: String,
    @SerializedName("expires_at") val expiresAt: String? = null,
) {
    /** Omits credential-bearing values from diagnostics. */
    override fun toString(): String = "EmbedSession(url=***, expiresAt=$expiresAt)"
}
