package com.assinafy.sdk

object SdkConstants {
    const val VERSION = "1.0.0"
    const val USER_AGENT = "assinafy-android-sdk/$VERSION"
    const val DEFAULT_BASE_URL = "https://api.assinafy.com.br/v1"
    const val DEFAULT_TIMEOUT_MS = 30_000L
    const val MAX_UPLOAD_BYTES = 25 * 1024 * 1024L
    const val DEFAULT_POLL_INTERVAL_MS = 2_000L
    const val DEFAULT_MAX_WAIT_MS = 30_000L
}

object DocumentStatus {
    val READY = setOf("metadata_ready", "pending_signature", "certificated")
    val FAILED = setOf("failed", "rejected_by_signer", "rejected_by_user", "expired")
}

object AssignmentMethod {
    const val VIRTUAL = "virtual"
}

object DocumentArtifact {
    const val CERTIFICATED = "certificated"
    const val ORIGINAL = "original"
}