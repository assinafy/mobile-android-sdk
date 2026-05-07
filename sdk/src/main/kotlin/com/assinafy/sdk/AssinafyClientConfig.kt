package com.assinafy.sdk

data class AssinafyClientConfig(
    val apiKey: String? = null,
    val token: String? = null,
    val accountId: String? = null,
    val baseUrl: String = DEFAULT_BASE_URL,
    val webhookSecret: String? = null,
    val timeoutMs: Long = 30_000L,
    val logger: Logger? = null,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.assinafy.com.br/v1"
    }
}
