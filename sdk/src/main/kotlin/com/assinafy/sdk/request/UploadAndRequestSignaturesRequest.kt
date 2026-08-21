package com.assinafy.sdk.request

/**
 * Input for [com.assinafy.sdk.AssinafyClient.uploadAndRequestSignatures].
 *
 * @property fileData Complete PDF bytes to upload.
 * @property fileName PDF file name sent with the multipart upload.
 * @property signers Non-empty signer list, resolved or created in request order.
 * @property message Optional message shown to every signer.
 * @property metadata Optional legacy document metadata.
 * @property waitForReady Whether to refresh the document after assignment creation. Metadata
 * readiness is always awaited to avoid processing-state races across API deployments.
 * @property expiresAt Optional ISO-8601 assignment expiration.
 * @property copyReceivers Optional signer IDs for recipients who only receive a copy.
 * @property accountId Account override; otherwise the client's default account is used.
 */
data class UploadAndRequestSignaturesRequest(
    val fileData: ByteArray,
    val fileName: String,
    val signers: List<SignerEntry>,
    val message: String? = null,
    val metadata: Map<String, Any>? = null,
    val waitForReady: Boolean = true,
    val expiresAt: String? = null,
    val copyReceivers: List<String>? = null,
    val accountId: String? = null,
) {
    /**
     * Signer input used by the high-level upload workflow.
     *
     * @property name Required full name.
     * @property email Required email used to reuse an existing signer or create a new one.
     * @property whatsappPhoneNumber Optional WhatsApp destination.
     * @property cpf Legacy CPF value retained for older API deployments.
     * @property metadata Legacy signer metadata retained for older API deployments.
     */
    data class SignerEntry(
        val name: String,
        val email: String,
        val whatsappPhoneNumber: String? = null,
        val cpf: String? = null,
        val metadata: Map<String, Any>? = null,
    )

    /** Compares file bytes by content and all remaining workflow inputs by value. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UploadAndRequestSignaturesRequest) return false
        return fileData.contentEquals(other.fileData) &&
            fileName == other.fileName &&
            signers == other.signers &&
            message == other.message &&
            metadata == other.metadata &&
            waitForReady == other.waitForReady &&
            expiresAt == other.expiresAt &&
            copyReceivers == other.copyReceivers &&
            accountId == other.accountId
    }

    /** Computes a content-based hash that is consistent with [equals]. */
    override fun hashCode(): Int {
        var result = fileData.contentHashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + signers.hashCode()
        result = 31 * result + (message?.hashCode() ?: 0)
        result = 31 * result + (metadata?.hashCode() ?: 0)
        result = 31 * result + waitForReady.hashCode()
        result = 31 * result + (expiresAt?.hashCode() ?: 0)
        result = 31 * result + (copyReceivers?.hashCode() ?: 0)
        result = 31 * result + (accountId?.hashCode() ?: 0)
        return result
    }
}
