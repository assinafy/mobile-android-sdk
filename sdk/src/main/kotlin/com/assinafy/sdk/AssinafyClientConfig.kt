package com.assinafy.sdk

data class AssinafyClientConfig(
    val apiKey: String? = null,
    val token: String? = null,
    val accountId: String? = null,
    val baseUrl: String = SdkConstants.DEFAULT_BASE_URL,
    val webhookSecret: String? = null,
    val timeoutMs: Long = SdkConstants.DEFAULT_TIMEOUT_MS,
    val logger: Logger? = null,
)
