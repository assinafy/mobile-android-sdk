package com.assinafy.smoke

import android.app.Application
import com.assinafy.sdk.AssinafyClient
import com.assinafy.sdk.AssinafyClientConfig
import com.assinafy.sdk.request.CreateAssignmentRequest
import com.assinafy.sdk.request.ListParams
import com.assinafy.sdk.request.SignerReference

class SmokeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val client = AssinafyClient.create(AssinafyClientConfig(baseUrl = "http://127.0.0.1"))
        client.webhookVerifier.extractEvent("{\"event\":\"smoke\"}")
        CreateAssignmentRequest(signers = listOf(SignerReference.ofId("signer")))
    }

    @Suppress("unused")
    private suspend fun compilePublicApi(client: AssinafyClient) {
        client.documents.list(ListParams(perPage = 1))
        client.users.getCurrent()
        client.users.getPushPreferences()
        client.workspaces.get("account", expandSigningFeatures = true)
        client.workspaces.getUsage("account")
        client.envelopes.list()
        client.assignments.listScheduledSends()
        client.assignments.getSignerCapacities("document")
        client.assignments.getNotificationHistory("document")
        client.assignments.schedule(
            "document",
            CreateAssignmentRequest(signers = listOf(SignerReference.ofId("signer"))),
            System.currentTimeMillis() / 1_000 + 7_200,
        )
        client.assignments.cancelScheduledSend("scheduled")
        client.assignments.sendScheduledNow("scheduled")
        client.documents.listWatchers("document")
        client.documents.setWatchers("document", emptyList())
        client.envelopes.estimateResendCost("envelope", "signer")
        client.signerDocuments.uploadSignature("signer-code", byteArrayOf(1), contentType = "image/jpeg")
        client.fields.validateAsSigner("field", 0, "signer-code", "account")
        client.fields.validateMultipleAsSigner(
            listOf(
                com.assinafy.sdk.request
                    .FieldValidationEntry("field", null),
            ),
            "signer-code",
            "account",
        )
        client.assignments.scheduleEnvelope(
            com.assinafy.sdk.request.CreateEnvelopeRequest(
                "Envelope",
                listOf(SignerReference.ofId("signer")),
                listOf(
                    com.assinafy.sdk.request
                        .EnvelopeDocumentRequest("document", listOf("signer")),
                ),
            ),
            System.currentTimeMillis() / 1_000 + 7_200,
        )
        client.embeddedSigning.listOrigins()
        client.signerDocuments.download("signer", "document", "original", "signer-code")
        client.contracts.list(com.assinafy.sdk.request.ContractListParams(endingWithinDays = 30))
        client.contracts.get("document")
        client.contracts.update("document", com.assinafy.sdk.request.UpdateContractRequest(status = com.assinafy.sdk.ContractStatus.ACTIVE))
        client.contracts.related("document")
        client.contracts.renew("document", com.assinafy.sdk.request.RenewContractRequest(term = 1))
        client.contracts.terminate("document")
        client.webhooks.listEventTypes()
        client.webhooks.listEndpoints(url = "https://example.com/hook")
        client.signerDocuments.getIdentityVerification("signer-code")
        client.assignments.getIdentityVerification("document", "assignment", "signer")
    }
}
