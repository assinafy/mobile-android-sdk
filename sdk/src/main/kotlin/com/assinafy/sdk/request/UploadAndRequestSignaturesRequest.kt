package com.assinafy.sdk.request

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
    data class SignerEntry(
        val name: String,
        val email: String,
        val whatsappPhoneNumber: String? = null,
    )

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

    override fun hashCode(): Int {
        var result = fileData.contentHashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + signers.hashCode()
        return result
    }
}
