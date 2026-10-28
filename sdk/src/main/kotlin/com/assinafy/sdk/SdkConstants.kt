package com.assinafy.sdk

/** Stable SDK, endpoint, size, timeout, and polling defaults. */
object SdkConstants {
    /** SDK release version included in [USER_AGENT]. */
    const val VERSION = BuildConfig.SDK_VERSION

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
    /** Metadata is ready and the document can receive a new assignment. */
    const val METADATA_READY = "metadata_ready"

    /** Terminal state for a document whose signatures are complete and certificate is issued. */
    const val CERTIFICATED = "certificated"

    /** Every signer signed; certification waits for the account's identity-review decision. */
    const val PENDING_IDENTITY_REVIEW = "pending_identity_review"

    /**
     * Statuses at which a document has finished metadata processing and can proceed to an
     * assignment (or is already past that point). Used by `DocumentResource.waitUntilReady`.
     * `certificating` is included because the API reports it once every signer has finished and the
     * final artifact is being produced — further metadata polling would never advance from there.
     */
    val READY = setOf(METADATA_READY, "pending_signature", PENDING_IDENTITY_REVIEW, "certificating", CERTIFICATED)

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

/** Stable machine-readable codes exposed through `ValidationException.errors["code"]`. */
object ValidationCode {
    /** A digital-certificate signer was supplied for a non-virtual assignment. */
    const val DIGITAL_CERTIFICATE_REQUIRES_VIRTUAL_ASSIGNMENT =
        "digital_certificate_requires_virtual_assignment"
}

/**
 * How a signer proves their identity before signing, set per signer through
 * [com.assinafy.sdk.request.SignerReference.verificationMethod].
 *
 * Verification and notification are **coupled**: send one, both, or neither and the API infers the
 * missing side; sending neither defaults both to [EMAIL]. See [NotificationMethod] for the pairings
 * the API accepts.
 */
object VerificationMethod {
    /** One-time code sent by email, entered before signing. Free, and the default. */
    const val EMAIL = "Email"

    /**
     * One-time code sent over WhatsApp. Requires the signer's `whatsapp_phone_number` and a paid
     * subscription; it forces the WhatsApp notification channel, so the signer costs 0.45 credits.
     */
    const val WHATSAPP = "Whatsapp"

    /**
     * The signer signs with their own ICP-Brasil certificate (**A1** or **A3**) through the Web PKI
     * browser extension, producing a qualified PAdES signature.
     *
     * Requires the account's Digital Certificate feature (Standard and Pro plans) and a CPF or CNPJ
     * in the signer's `government_id`; the assignment method must be [AssignmentMethod.VIRTUAL],
     * the signer must be alone in their signing step, and the signature costs 0.5 credits on top of
     * the notification. The certificate handshake itself runs in the browser extension, so send the
     * signer to the web signing page rather than signing through this SDK's native flow.
     */
    const val DIGITAL_CERTIFICATE = "DigitalCertificate"

    /** Hosted identity-document and face verification; requires the account feature and a valid signer CPF. */
    const val ID_DOCUMENT_FACE_MATCH_BASIC = "IdDocumentFaceMatchBasic"

    /** Every value the API accepts. */
    val ALL = setOf(EMAIL, WHATSAPP, DIGITAL_CERTIFICATE, ID_DOCUMENT_FACE_MATCH_BASIC)
}

/**
 * How a signer is told that a signature is being requested, set per signer through
 * [com.assinafy.sdk.request.SignerReference.notificationMethods]. Exactly one channel per signer.
 */
object NotificationMethod {
    /** Email invitation carrying the signing link. Free. */
    const val EMAIL = "Email"

    /**
     * WhatsApp message carrying the signing link. Requires the signer's `whatsapp_phone_number` and
     * a paid subscription; costs 0.45 credits per signer, charged again on every resend.
     */
    const val WHATSAPP = "Whatsapp"

    /** Every value the API accepts. */
    val ALL = setOf(EMAIL, WHATSAPP)
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
    /** Terms changed. */
    const val DOCUMENT_CONTRACT_UPDATED = "document_contract_updated"

    /** The notice period started. */
    const val DOCUMENT_CONTRACT_RENEWAL_NOTICE = "document_contract_renewal_notice"

    /** The end date is approaching. */
    const val DOCUMENT_CONTRACT_EXPIRING = "document_contract_expiring"

    /** The contract expired. */
    const val DOCUMENT_CONTRACT_EXPIRED = "document_contract_expired"

    /** A new term started. */
    const val DOCUMENT_CONTRACT_RENEWED = "document_contract_renewed"

    /** The contract was closed. */
    const val DOCUMENT_CONTRACT_TERMINATED = "document_contract_terminated"

    /** A signed instrument changed the root contract. */
    const val DOCUMENT_CONTRACT_AMENDED = "document_contract_amended"

    /** An instrument link or pending delta changed. */
    const val DOCUMENT_CONTRACT_AMENDMENT_UPDATED = "document_contract_amendment_updated"

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

    /** The final signer completed the document and its status became ready. */
    const val DOCUMENT_READY = "document_ready"

    /** A signer record was created. */
    const val SIGNER_CREATED = "signer_created"

    /** A signer verified an email address. */
    const val SIGNER_EMAIL_VERIFIED = "signer_email_verified"

    /** A signer verified a WhatsApp number. */
    const val SIGNER_WHATSAPP_VERIFIED = "signer_whatsapp_verified"

    /** The signer passed identity-document verification, directly or after review. */
    const val SIGNER_IDENTITY_VERIFIED = "signer_identity_verified"

    /** Identity-document verification ended without approval. */
    const val SIGNER_IDENTITY_VERIFICATION_FAILED = "signer_identity_verification_failed"

    /** The account must review the signer's identity before certification. */
    const val SIGNER_IDENTITY_REVIEW_REQUIRED = "signer_identity_review_required"

    /** The account approved the signer's identity after review. */
    const val SIGNER_IDENTITY_REVIEW_APPROVED = "signer_identity_review_approved"

    /** The account rejected the signer's identity and cancelled the document. */
    const val SIGNER_IDENTITY_REVIEW_REJECTED = "signer_identity_review_rejected"

    /** A signer confirmed identity data. */
    const val SIGNER_DATA_CONFIRMED = "signer_data_confirmed"

    /** A signer completed a document signature. */
    const val SIGNER_SIGNED_DOCUMENT = "signer_signed_document"

    /** A signer viewed a document. */
    const val SIGNER_VIEWED_DOCUMENT = "signer_viewed_document"

    /** A signer declined a document. */
    const val SIGNER_REJECTED_DOCUMENT = "signer_rejected_document"

    /** An account user cancelled a document. */
    const val USER_REJECTED_DOCUMENT = "user_rejected_document"

    /** Document processing ended in failure. */
    const val DOCUMENT_PROCESSING_FAILED = "document_processing_failed"

    /** A reusable template was created. */
    const val TEMPLATE_CREATED = "template_created"

    /** Template processing completed and the template became ready. */
    const val TEMPLATE_PROCESSED = "template_processed"

    /** Template processing ended in failure. */
    const val TEMPLATE_PROCESSING_FAILED = "template_processing_failed"
}

/** Who appears as the sender of signer notifications (`notification_sender_type`). */
object NotificationSenderType {
    /** The user who sent the document. */
    const val USER = "User"

    /** The workspace itself. */
    const val ACCOUNT = "Account"
}

/** Values of `blocking_reason` in a cost estimate. */
object CostBlockingReason {
    /** The workspace has an unpaid invoice. */
    const val PENDING_PAYMENT = "PendingPayment"

    /** The plan's document allowance is exhausted. */
    const val INSUFFICIENT_DOCUMENTS = "InsufficientDocuments"

    /** The workspace lacks credits for the paid verification or notification methods. */
    const val INSUFFICIENT_CREDITS = "InsufficientCredits"
}

/** Delivery outcome of a WhatsApp notification history entry (`status`). */
object NotificationDeliveryStatus {
    /** The message was sent. */
    const val SENT = "sent"

    /** The message could not be sent. */
    const val FAILED = "failed"
}

/** Two-factor method types returned by `GET /users/self/mfa`. */
object MfaMethodType {
    /** Authenticator app (RFC 6238 time-based one-time password). */
    const val TOTP = "totp"
}

/** States of a hosted identity-document verification session. */
object IdentityVerificationStatus {
    /** Capture or provider processing is pending. */
    const val PENDING = "Pending"

    /** The account must decide the provider's review. Signing may continue meanwhile. */
    const val IN_REVIEW = "InReview"

    /** The identity was approved. */
    const val APPROVED = "Approved"

    /** The provider declined the verification; a new signing process is required. */
    const val DECLINED = "Declined"

    /** The capture link expired before submission; a replacement session may be opened. */
    const val EXPIRED = "Expired"

    /** Capture was abandoned; a new signing process is required. */
    const val ABANDONED = "Abandoned"
}

/** Account decisions accepted by the identity-review endpoint. */
object IdentityReviewDecision {
    /** Approve the identity and permit certification once every signer has signed. */
    const val APPROVED = "Approved"

    /** Reject the identity and cancel the document, notifying signers and copy receivers. */
    const val REJECTED = "Rejected"
}

/** Evidence names available only while an identity review is open. */
object IdentityReviewImage {
    /** Front of the identity document. */
    const val DOCUMENT_FRONT = "document_front"

    /** Back of the identity document. */
    const val DOCUMENT_BACK = "document_back"

    /** Digital identity document PDF. */
    const val DOCUMENT_PDF = "document_pdf"

    /** Captured face photograph. */
    const val FACE = "face"

    /** Every evidence name accepted by the API. */
    val ALL = setOf(DOCUMENT_FRONT, DOCUMENT_BACK, DOCUMENT_PDF, FACE)
}

/** Contract lifecycle status values. */
object ContractStatus {
    /** Terms being prepared. */
    const val DRAFT = "draft"

    /** Contract currently in force. */
    const val ACTIVE = "active"

    /** Contract ended without renewal. */
    const val EXPIRED = "expired"

    /** Contract explicitly closed. */
    const val TERMINATED = "terminated"

    /** Every supported wire value. */
    val ALL = setOf(DRAFT, ACTIVE, EXPIRED, TERMINATED)
}

/** Kinds of signed instruments linked to a contract. */
object ContractRelationType {
    /** Changes the allowed contract terms. */
    const val AMENDMENT = "amendment"

    /** Changes only the end date and value. */
    const val RENEWAL = "renewal"

    /** Closes the contract on its effective end date. */
    const val TERMINATION = "termination"

    /** Every supported wire value. */
    val ALL = setOf(AMENDMENT, RENEWAL, TERMINATION)
}

/** Application state of a linked instrument. */
object ContractAmendmentStatus {
    /** Waiting for certification, entitlement or preceding instruments. */
    const val PENDING = "pending"

    /** The delta has been applied; the link is read-only. */
    const val APPLIED = "applied"

    /** Every supported wire value. */
    val ALL = setOf(PENDING, APPLIED)
}

/** Template role assignment types. */
object TemplateRoleType {
    /** Fields filled before generating the document; omit from signers. */
    const val EDITOR = "Editor"

    /** Person required to sign the generated document. */
    const val SIGNER = "Signer"

    /** Recipient of the completed document; signing settings are ignored. */
    const val COPY_RECEIVER = "CopyReceiver"

    /** Every supported wire value. */
    val ALL = setOf(EDITOR, SIGNER, COPY_RECEIVER)
}

/** Kinds of webhook delivery attempts. */
object WebhookAttemptKind {
    /** Automatic delivery or retry. */
    const val SCHEDULED = "scheduled"

    /** Recovery probe for a held endpoint. */
    const val PROBE = "probe"

    /** Explicit delivery retry. */
    const val MANUAL = "manual"

    /** Every supported wire value. */
    val ALL = setOf(SCHEDULED, PROBE, MANUAL)
}

/** Nullable contract fields that can be explicitly cleared in a partial update. */
enum class ContractField(internal val wireValue: String) {
    /** Send JSON null for `counterparty_name`; see the update request for amendment semantics. */
    COUNTERPARTY_NAME("counterparty_name"),

    /** Send JSON null for `counterparty_taxpayer_id`; see the update request for amendment semantics. */
    COUNTERPARTY_TAXPAYER_ID("counterparty_taxpayer_id"),

    /** Send JSON null for `value`; see the update request for amendment semantics. */
    VALUE("value"),

    /** Send JSON null for `currency`; see the update request for amendment semantics. */
    CURRENCY("currency"),

    /** Send JSON null for `start_date`; see the update request for amendment semantics. */
    START_DATE("start_date"),

    /** Send JSON null for `end_date`; see the update request for amendment semantics. */
    END_DATE("end_date"),

    /** Send JSON null for `auto_renew`; see the update request for amendment semantics. */
    AUTO_RENEW("auto_renew"),

    /** Send JSON null for `renewal_term_months`; see the update request for amendment semantics. */
    RENEWAL_TERM_MONTHS("renewal_term_months"),

    /** Send JSON null for `notice_period_days`; see the update request for amendment semantics. */
    NOTICE_PERIOD_DAYS("notice_period_days"),

    /** Send JSON null for `owner_id`; see the update request for amendment semantics. */
    OWNER_ID("owner_id"),

    /** Send JSON null for `metadata`; see the update request for amendment semantics. */
    METADATA("metadata"),

    /** Send JSON null for `external_references`; see the update request for amendment semantics. */
    EXTERNAL_REFERENCES("external_references"),

    /** Send JSON null for `parent_document_id`; see the update request for amendment semantics. */
    PARENT_DOCUMENT_ID("parent_document_id"),

    /** Send JSON null for `updated_at`; see the update request for amendment semantics. */
    UPDATED_AT("updated_at"),
}
