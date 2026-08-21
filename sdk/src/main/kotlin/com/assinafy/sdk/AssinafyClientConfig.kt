package com.assinafy.sdk

/**
 * Client configuration. [apiKey] and [token] are mutually exclusive; both may be omitted for login
 * and public signing operations. Use HTTPS whenever credentials are supplied.
 *
 * @property apiKey API key sent as `X-Api-Key` only to the configured API origin.
 * @property token Bearer token sent as `Authorization` only to the configured API origin.
 * @property accountId Default account for account-scoped calls; individual methods may override it.
 * @property baseUrl Absolute HTTP(S) API root including `/v1`; defaults to production.
 * @property webhookSecret Optional local HMAC secret used by `webhookVerifier`; never sent to the API.
 * @property timeoutMs Positive connect, read, and write timeout for each HTTP request, in milliseconds.
 * @property logger Optional SDK logging sink; `null` selects [Logger.NONE].
 */
data class AssinafyClientConfig(
    val apiKey: String? = null,
    val token: String? = null,
    val accountId: String? = null,
    val baseUrl: String = SdkConstants.DEFAULT_BASE_URL,
    val webhookSecret: String? = null,
    val timeoutMs: Long = SdkConstants.DEFAULT_TIMEOUT_MS,
    val logger: Logger? = null,
) {
    /** Returns configuration diagnostics without exposing API, bearer, or webhook secrets. */
    override fun toString(): String =
        "AssinafyClientConfig(apiKey=${apiKey.redacted()}, token=${token.redacted()}, " +
            "accountId=$accountId, baseUrl=$baseUrl, webhookSecret=${webhookSecret.redacted()}, " +
            "timeoutMs=$timeoutMs, logger=$logger)"

    private fun String?.redacted(): String = if (this == null) "null" else "***"
}
