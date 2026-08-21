package com.assinafy.sdk

/** Stable SDK, endpoint, size, timeout, and polling defaults. */
object SdkConstants {
    /** SDK release version included in [USER_AGENT]. */
    const val VERSION = "2.0.0"

    /** HTTP user-agent sent by the default transport. */
    const val USER_AGENT = "assinafy-android-sdk/$VERSION"

    /** Production Assinafy API root. */
    const val DEFAULT_BASE_URL = "https://api.assinafy.com.br/v1"

    /** Per-request network timeout (connect/read/write). */
    const val DEFAULT_TIMEOUT_MS = 30_000L

    /** Maximum PDF upload size accepted by the SDK, in bytes. */
    const val MAX_UPLOAD_BYTES = 25 * 1024 * 1024L

    /** Maximum length of a document name accepted by `PATCH /documents/{id}` (rename). */
    const val MAX_DOCUMENT_NAME_LENGTH = 255

    /** Delay between `DocumentResource.waitUntilReady` readiness requests. */
    const val DEFAULT_POLL_INTERVAL_MS = 2_000L

    /**
     * Total budget for `DocumentResource.waitUntilReady` to poll a freshly
     * uploaded document through `uploaded -> metadata_processing -> metadata_ready`. Independent of
     * [DEFAULT_TIMEOUT_MS] (a single round-trip) since readiness spans several round-trips plus
     * async server-side PDF processing.
     */
    const val DEFAULT_MAX_WAIT_MS = 120_000L
}

/** Document status values and status groups used by readiness polling. */
object DocumentStatus {
    /** Terminal state for a document whose signatures are complete and certificate is issued. */
    const val CERTIFICATED = "certificated"

    /**
     * Statuses at which a document has finished metadata processing and can proceed to an
     * assignment (or is already past that point). Used by `DocumentResource.waitUntilReady`.
     */
    val READY = setOf("metadata_ready", "pending_signature", "certificated")

    /** Terminal non-success statuses that stop a `DocumentResource.waitUntilReady` poll loop. */
    val FAILED = setOf("failed", "rejected_by_signer", "rejected_by_user", "expired")
}

/** Assignment-method wire values accepted by [com.assinafy.sdk.request.CreateAssignmentRequest]. */
object AssignmentMethod {
    /** Remote signing through an emailed or messaged signing link. */
    const val VIRTUAL = "virtual"

    /** In-person field collection on configured document pages. */
    const val COLLECT = "collect"
}

/** Downloadable document artifact names for [com.assinafy.sdk.resources.DocumentResource.download]. */
object DocumentArtifact {
    /** PDF originally uploaded to Assinafy. */
    const val ORIGINAL = "original"

    /** Final PDF containing all signatures and the certificate page. */
    const val CERTIFICATED = "certificated"

    /** Certificate page only. */
    const val CERTIFICATE_PAGE = "certificate-page"

    /** Archive containing the available document artifacts. */
    const val BUNDLE = "bundle"

    /** PAdES-compatible signed PDF. */
    const val PADES = "pades"
}

/** Signature image kinds for [com.assinafy.sdk.resources.SignerResource.uploadSignature]/`downloadSignature`. */
object SignatureType {
    /** Full handwritten signature image. */
    const val SIGNATURE = "signature"

    /** Initials image. */
    const val INITIAL = "initial"
}

/**
 * Webhook event identifiers (wire-format `id` values) as returned by
 * [com.assinafy.sdk.resources.WebhookResource.listEventTypes]. Use these when building a
 * [com.assinafy.sdk.request.RegisterWebhookRequest].
 */
object WebhookEvent {
    /** A document upload was accepted. */
    const val DOCUMENT_UPLOADED = "document_uploaded"

    /** Document metadata extraction completed. */
    const val DOCUMENT_METADATA_READY = "document_metadata_ready"

    /** A document was prepared for assignment. */
    const val DOCUMENT_PREPARED = "document_prepared"

    /** A signing assignment was created. */
    const val ASSIGNMENT_CREATED = "assignment_created"

    /** A signer was notified of a signature request. */
    const val SIGNATURE_REQUESTED = "signature_requested"

    /** A document became ready for signing. */
    const val DOCUMENT_READY = "document_ready"

    /** A signer record was created. */
    const val SIGNER_CREATED = "signer_created"

    /** A signer verified an email address. */
    const val SIGNER_EMAIL_VERIFIED = "signer_email_verified"

    /** A signer verified a WhatsApp number. */
    const val SIGNER_WHATSAPP_VERIFIED = "signer_whatsapp_verified"

    /** A signer confirmed identity data. */
    const val SIGNER_DATA_CONFIRMED = "signer_data_confirmed"

    /** A signer completed a document signature. */
    const val SIGNER_SIGNED_DOCUMENT = "signer_signed_document"

    /** A signer viewed a document. */
    const val SIGNER_VIEWED_DOCUMENT = "signer_viewed_document"

    /** A signer declined a document. */
    const val SIGNER_REJECTED_DOCUMENT = "signer_rejected_document"

    /** Document processing ended in failure. */
    const val DOCUMENT_PROCESSING_FAILED = "document_processing_failed"
}
