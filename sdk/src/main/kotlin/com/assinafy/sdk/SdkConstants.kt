package com.assinafy.sdk

object SdkConstants {
    const val VERSION = "1.1.0"
    const val USER_AGENT = "assinafy-android-sdk/$VERSION"
    const val DEFAULT_BASE_URL = "https://api.assinafy.com.br/v1"

    /** Per-request network timeout (connect/read/write). */
    const val DEFAULT_TIMEOUT_MS = 30_000L
    const val MAX_UPLOAD_BYTES = 25 * 1024 * 1024L
    const val DEFAULT_POLL_INTERVAL_MS = 2_000L

    /**
     * Total budget for [com.assinafy.sdk.resources.DocumentResource.waitUntilReady] to poll a freshly
     * uploaded document through `uploaded -> metadata_processing -> metadata_ready`. Independent of
     * [DEFAULT_TIMEOUT_MS] (a single round-trip) since readiness spans several round-trips plus
     * async server-side PDF processing.
     */
    const val DEFAULT_MAX_WAIT_MS = 120_000L
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

/** Downloadable document artifact names for [com.assinafy.sdk.resources.DocumentResource.download]. */
object DocumentArtifact {
    const val ORIGINAL = "original"
    const val CERTIFICATED = "certificated"
    const val CERTIFICATE_PAGE = "certificate-page"
    const val BUNDLE = "bundle"
}

/** Signature image kinds for [com.assinafy.sdk.resources.SignerResource.uploadSignature]/`downloadSignature`. */
object SignatureType {
    const val SIGNATURE = "signature"
    const val INITIAL = "initial"
}

/**
 * Webhook event identifiers (wire-format `id` values) as returned by
 * [com.assinafy.sdk.resources.WebhookResource.listEventTypes]. Use these when building a
 * [com.assinafy.sdk.request.RegisterWebhookRequest].
 */
object WebhookEvent {
    const val DOCUMENT_UPLOADED = "document_uploaded"
    const val DOCUMENT_METADATA_READY = "document_metadata_ready"
    const val DOCUMENT_PREPARED = "document_prepared"
    const val ASSIGNMENT_CREATED = "assignment_created"
    const val SIGNATURE_REQUESTED = "signature_requested"
    const val DOCUMENT_READY = "document_ready"
    const val SIGNER_CREATED = "signer_created"
    const val SIGNER_EMAIL_VERIFIED = "signer_email_verified"
    const val SIGNER_WHATSAPP_VERIFIED = "signer_whatsapp_verified"
    const val SIGNER_DATA_CONFIRMED = "signer_data_confirmed"
    const val SIGNER_SIGNED_DOCUMENT = "signer_signed_document"
    const val SIGNER_VIEWED_DOCUMENT = "signer_viewed_document"
    const val SIGNER_REJECTED_DOCUMENT = "signer_rejected_document"
    const val DOCUMENT_PROCESSING_FAILED = "document_processing_failed"
}
