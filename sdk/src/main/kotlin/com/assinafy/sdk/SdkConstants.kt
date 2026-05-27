package com.assinafy.sdk

object SdkConstants {
    const val VERSION = "1.0.2"
    const val USER_AGENT = "assinafy-android-sdk/$VERSION"
    const val DEFAULT_BASE_URL = "https://api.assinafy.com.br/v1"
    const val DEFAULT_TIMEOUT_MS = 30_000L
    const val MAX_UPLOAD_BYTES = 25 * 1024 * 1024L
    const val DEFAULT_POLL_INTERVAL_MS = 2_000L
    const val DEFAULT_MAX_WAIT_MS = 30_000L
}

object DocumentStatus {
    /** Terminal state for a document whose signatures are complete and certificate is issued. */
    const val CERTIFICATED = "certificated"

    /**
     * Statuses at which a document has finished metadata processing and can proceed to an
     * assignment (or is already past that point). Used by [waitUntilReady].
     */
    val READY = setOf("metadata_ready", "pending_signature", "certificated")

    /** Terminal non-success statuses that stop a [waitUntilReady] poll loop. */
    val FAILED = setOf("failed", "rejected_by_signer", "rejected_by_user", "expired")
}

object AssignmentMethod {
    const val VIRTUAL = "virtual"
    const val COLLECT = "collect"
}

object DocumentArtifact {
    const val CERTIFICATED = "certificated"
    const val ORIGINAL = "original"
}
